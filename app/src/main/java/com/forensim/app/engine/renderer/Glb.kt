package com.forensim.app.engine.renderer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Minimal glTF 2.0 binary (.glb) reader for the Forensim asset set (art/README.md): static
 * triangle meshes with a base colour (factor and/or PNG texture), emission and alpha mode. No
 * skins, morph targets or animation. Pure Kotlin, so it is unit tested on the JVM; GPU upload
 * lives in [GlesRenderer].
 *
 * Colours are converted to the sRGB-like space the renderer's shader works in.
 */
class GlbModel(val primitives: List<Primitive>, val images: List<ByteArray>) {

    class Material(
        /** sRGB colour and alpha. */
        val baseColor: FloatArray,
        /** Index into [GlbModel.images], or -1. */
        val baseColorImage: Int,
        /** sRGB emission, already multiplied by the emissive strength and clamped to 0..1. */
        val emissive: FloatArray,
        val blend: Boolean,
        val alphaCutoff: Float,
        val doubleSided: Boolean,
    )

    /**
     * One draw call: interleaved vertices (position xyz, normal xyz, uv) already transformed by
     * the node hierarchy, and 32-bit triangle indices.
     */
    class Primitive(val vertices: FloatArray, val indices: IntArray, val material: Material) {
        val vertexCount get() = vertices.size / STRIDE
    }

    /** Axis-aligned bounds of all vertices: (minX, minY, minZ, maxX, maxY, maxZ). */
    val bounds: FloatArray by lazy {
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (p in primitives) {
            var i = 0
            while (i < p.vertices.size) {
                for (k in 0 until 3) {
                    val v = p.vertices[i + k]
                    if (v < b[k]) b[k] = v
                    if (v > b[k + 3]) b[k + 3] = v
                }
                i += STRIDE
            }
        }
        b
    }

    companion object {
        /** Floats per vertex: position (3), normal (3), uv (2). */
        const val STRIDE = 8
        private const val MAGIC = 0x46546C67 // "glTF"
        private const val CHUNK_JSON = 0x4E4F534A
        private const val CHUNK_BIN = 0x004E4942
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(bytes: ByteArray): GlbModel {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bb.int == MAGIC) { "Not a .glb file" }
            require(bb.int == 2) { "Only glTF 2.0 is supported" }
            bb.int // total length
            var doc: JsonObject? = null
            var bin: ByteBuffer? = null
            while (bb.remaining() >= 8) {
                val len = bb.int
                val type = bb.int
                val start = bb.position()
                when (type) {
                    CHUNK_JSON -> doc = json.parseToJsonElement(String(bytes, start, len, Charsets.UTF_8)).jsonObject
                    CHUNK_BIN -> bin = ByteBuffer.wrap(bytes, start, len).slice().order(ByteOrder.LITTLE_ENDIAN)
                }
                bb.position(start + len)
            }
            return Reader(requireNotNull(doc) { "Missing JSON chunk" }, bin ?: ByteBuffer.allocate(0)).read()
        }

        internal fun linearToSrgb(c: Float): Float =
            if (c <= 0.0031308f) c * 12.92f else (1.055f * c.toDouble().pow(1 / 2.4) - 0.055).toFloat()
    }

    private class Reader(val doc: JsonObject, val bin: ByteBuffer) {
        val accessors = doc.arr("accessors")
        val views = doc.arr("bufferViews")
        val textures = doc.arr("textures") // before materials, which look textures up
        val materials = doc.arr("materials").map { readMaterial(it.jsonObject) }
        val defaultMaterial = Material(floatArrayOf(1f, 1f, 1f, 1f), -1, floatArrayOf(0f, 0f, 0f), blend = false, alphaCutoff = 0f, doubleSided = false)

        fun read(): GlbModel {
            val prims = ArrayList<Primitive>()
            val nodes = doc.arr("nodes")
            val scene = doc["scene"]?.jsonPrimitive?.int ?: 0
            val roots = doc.arr("scenes").getOrNull(scene)?.jsonObject?.arr("nodes")?.map { it.jsonPrimitive.int }
                ?: nodes.indices.toList()
            fun visit(index: Int, parent: FloatArray) {
                val node = nodes[index].jsonObject
                val world = mul(parent, localMatrix(node))
                node["mesh"]?.jsonPrimitive?.int?.let { m ->
                    for (p in doc.arr("meshes")[m].jsonObject.arr("primitives")) readPrimitive(p.jsonObject, world)?.let(prims::add)
                }
                node.arr("children").forEach { visit(it.jsonPrimitive.int, world) }
            }
            roots.forEach { visit(it, identity()) }
            val images = doc.arr("images").map { img ->
                val view = img.jsonObject["bufferView"]?.jsonPrimitive?.int ?: error("External images are not supported")
                viewBytes(view)
            }
            return GlbModel(prims, images)
        }

        fun readMaterial(m: JsonObject): Material {
            val pbr = m["pbrMetallicRoughness"]?.jsonObject
            val f = pbr?.get("baseColorFactor")?.jsonArray?.map { it.jsonPrimitive.float } ?: listOf(1f, 1f, 1f, 1f)
            val texIndex = pbr?.get("baseColorTexture")?.jsonObject?.get("index")?.jsonPrimitive?.int
            val image = texIndex?.let { textures.getOrNull(it)?.jsonObject?.get("source")?.jsonPrimitive?.int } ?: -1
            val strength = m["extensions"]?.jsonObject?.get("KHR_materials_emissive_strength")?.jsonObject
                ?.get("emissiveStrength")?.jsonPrimitive?.float ?: 1f
            val e = m["emissiveFactor"]?.jsonArray?.map { it.jsonPrimitive.float } ?: listOf(0f, 0f, 0f)
            val mode = m["alphaMode"]?.jsonPrimitive?.contentOrNull ?: "OPAQUE"
            return Material(
                baseColor = floatArrayOf(linearToSrgb(f[0]), linearToSrgb(f[1]), linearToSrgb(f[2]), f.getOrElse(3) { 1f }),
                baseColorImage = image,
                emissive = FloatArray(3) { linearToSrgb((e[it] * strength).coerceIn(0f, 1f)) },
                blend = mode == "BLEND",
                alphaCutoff = if (mode == "MASK") m["alphaCutoff"]?.jsonPrimitive?.float ?: 0.5f else 0f,
                doubleSided = m["doubleSided"]?.jsonPrimitive?.contentOrNull == "true",
            )
        }

        fun readPrimitive(p: JsonObject, world: FloatArray): Primitive? {
            if ((p["mode"]?.jsonPrimitive?.int ?: 4) != 4) return null // triangles only
            val attrs = p["attributes"]?.jsonObject ?: return null
            val pos = attrs["POSITION"]?.jsonPrimitive?.int?.let { floats(it, 3) } ?: return null
            val n = pos.size / 3
            val nrm = attrs["NORMAL"]?.jsonPrimitive?.int?.let { floats(it, 3) }
            val uv = attrs["TEXCOORD_0"]?.jsonPrimitive?.int?.let { floats(it, 2) }
            val out = FloatArray(n * STRIDE)
            for (i in 0 until n) {
                val o = i * STRIDE
                val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
                out[o] = world[0] * x + world[4] * y + world[8] * z + world[12]
                out[o + 1] = world[1] * x + world[5] * y + world[9] * z + world[13]
                out[o + 2] = world[2] * x + world[6] * y + world[10] * z + world[14]
                if (nrm != null) {
                    val a = nrm[i * 3]; val b = nrm[i * 3 + 1]; val c = nrm[i * 3 + 2]
                    var nx = world[0] * a + world[4] * b + world[8] * c
                    var ny = world[1] * a + world[5] * b + world[9] * c
                    var nz = world[2] * a + world[6] * b + world[10] * c
                    val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-8f)
                    nx /= len; ny /= len; nz /= len
                    out[o + 3] = nx; out[o + 4] = ny; out[o + 5] = nz
                } else {
                    out[o + 4] = 1f
                }
                if (uv != null) { out[o + 6] = uv[i * 2]; out[o + 7] = uv[i * 2 + 1] }
            }
            val idx = p["indices"]?.jsonPrimitive?.int?.let { indices(it) } ?: IntArray(n) { it }
            val mat = p["material"]?.jsonPrimitive?.int?.let { materials.getOrNull(it) } ?: defaultMaterial
            return Primitive(out, idx, mat)
        }

        fun floats(accessor: Int, comps: Int): FloatArray {
            val a = accessors[accessor].jsonObject
            require(a["componentType"]?.jsonPrimitive?.int == 5126) { "Only float vertex attributes are supported" }
            val count = a["count"]!!.jsonPrimitive.int
            val (buf, stride) = view(a, comps * 4)
            val out = FloatArray(count * comps)
            for (i in 0 until count) for (k in 0 until comps) out[i * comps + k] = buf.getFloat(i * stride + k * 4)
            return out
        }

        fun indices(accessor: Int): IntArray {
            val a = accessors[accessor].jsonObject
            val count = a["count"]!!.jsonPrimitive.int
            return when (val type = a["componentType"]!!.jsonPrimitive.int) {
                5121 -> view(a, 1).let { (b, s) -> IntArray(count) { b.get(it * s).toInt() and 0xFF } }
                5123 -> view(a, 2).let { (b, s) -> IntArray(count) { b.getShort(it * s).toInt() and 0xFFFF } }
                5125 -> view(a, 4).let { (b, s) -> IntArray(count) { b.getInt(it * s) } }
                else -> error("Unsupported index type $type")
            }
        }

        /** Little-endian buffer starting at the accessor's first element, plus the element stride. */
        fun view(a: JsonObject, elementSize: Int): Pair<ByteBuffer, Int> {
            val v = views[a["bufferView"]!!.jsonPrimitive.int].jsonObject
            val offset = (v["byteOffset"]?.jsonPrimitive?.int ?: 0) + (a["byteOffset"]?.jsonPrimitive?.int ?: 0)
            val stride = v["byteStride"]?.jsonPrimitive?.int ?: elementSize
            val b = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            b.position(offset)
            return b.slice().order(ByteOrder.LITTLE_ENDIAN) to stride
        }

        fun viewBytes(index: Int): ByteArray {
            val v = views[index].jsonObject
            val out = ByteArray(v["byteLength"]!!.jsonPrimitive.int)
            val b = bin.duplicate()
            b.position(v["byteOffset"]?.jsonPrimitive?.int ?: 0)
            b.get(out)
            return out
        }

        fun localMatrix(node: JsonObject): FloatArray {
            node["matrix"]?.jsonArray?.let { m -> return FloatArray(16) { m[it].jsonPrimitive.float } }
            val t = node["translation"]?.jsonArray?.map { it.jsonPrimitive.float } ?: listOf(0f, 0f, 0f)
            val r = node["rotation"]?.jsonArray?.map { it.jsonPrimitive.float } ?: listOf(0f, 0f, 0f, 1f)
            val s = node["scale"]?.jsonArray?.map { it.jsonPrimitive.float } ?: listOf(1f, 1f, 1f)
            val (x, y, z, w) = r
            // Column-major T * R * S.
            return floatArrayOf(
                (1 - 2 * (y * y + z * z)) * s[0], (2 * (x * y + z * w)) * s[0], (2 * (x * z - y * w)) * s[0], 0f,
                (2 * (x * y - z * w)) * s[1], (1 - 2 * (x * x + z * z)) * s[1], (2 * (y * z + x * w)) * s[1], 0f,
                (2 * (x * z + y * w)) * s[2], (2 * (y * z - x * w)) * s[2], (1 - 2 * (x * x + y * y)) * s[2], 0f,
                t[0], t[1], t[2], 1f,
            )
        }

        fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        fun mul(a: FloatArray, b: FloatArray) = FloatArray(16) { i ->
            val col = i / 4; val row = i % 4
            (0 until 4).fold(0f) { acc, k -> acc + a[k * 4 + row] * b[col * 4 + k] }
        }

        private fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray) ?: emptyList()
    }
}
