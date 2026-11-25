package com.forensim.app.engine.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 = length().let { if (it < 1e-6f) this else Vec3(x / it, y / it, z / it) }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

/** Axis-aligned bounding box. All colliders and pick volumes use these. */
data class Aabb(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = Vec3((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2)
    val size: Vec3 get() = max - min

    fun intersects(o: Aabb, margin: Float = 0f) =
        min.x - margin < o.max.x && max.x + margin > o.min.x &&
            min.y - margin < o.max.y && max.y + margin > o.min.y &&
            min.z - margin < o.max.z && max.z + margin > o.min.z

    /** Footprint overlap in the XZ plane only. */
    fun overlapsXZ(o: Aabb, margin: Float = 0f) =
        min.x - margin < o.max.x && max.x + margin > o.min.x &&
            min.z - margin < o.max.z && max.z + margin > o.min.z

    fun inflate(d: Float) = Aabb(Vec3(min.x - d, min.y - d, min.z - d), Vec3(max.x + d, max.y + d, max.z + d))

    /** Slab-method ray test. Returns the entry distance, or null when missed / behind. */
    fun raycast(origin: Vec3, dir: Vec3): Float? {
        var tMin = 0f
        var tMax = Float.MAX_VALUE
        val o = floatArrayOf(origin.x, origin.y, origin.z)
        val d = floatArrayOf(dir.x, dir.y, dir.z)
        val lo = floatArrayOf(min.x, min.y, min.z)
        val hi = floatArrayOf(max.x, max.y, max.z)
        for (i in 0..2) {
            if (abs(d[i]) < 1e-8f) {
                if (o[i] < lo[i] || o[i] > hi[i]) return null
            } else {
                val inv = 1f / d[i]
                var t1 = (lo[i] - o[i]) * inv
                var t2 = (hi[i] - o[i]) * inv
                if (t1 > t2) { val t = t1; t1 = t2; t2 = t }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) return null
            }
        }
        return tMin
    }

    companion object {
        /** Box centred on (x, z) resting on [y]. */
        fun onSurface(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float) =
            Aabb(Vec3(x - w / 2, y, z - d / 2), Vec3(x + w / 2, y + h, z + d / 2))
    }
}

/** Distance from a point to the closest point of an AABB in the XZ plane. */
fun distanceXZ(px: Float, pz: Float, box: Aabb): Float {
    val cx = px.coerceIn(box.min.x, box.max.x)
    val cz = pz.coerceIn(box.min.z, box.max.z)
    val dx = px - cx
    val dz = pz - cz
    return sqrt(dx * dx + dz * dz)
}

fun parseHexColor(hex: String, fallback: FloatArray = floatArrayOf(0.5f, 0.5f, 0.5f)): FloatArray = runCatching {
    val v = hex.removePrefix("#").toLong(16)
    floatArrayOf(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f)
}.getOrDefault(fallback)
