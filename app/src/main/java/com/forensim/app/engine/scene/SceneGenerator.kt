package com.forensim.app.engine.scene

import com.forensim.app.core.model.ManifestItem
import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.math.distanceXZ
import com.forensim.app.engine.math.parseHexColor
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class ObjectKind { FLOOR, CEILING, WALL, FURNITURE, SCENE_LOG, TAPE, EVIDENCE, VICTIM }

data class SceneObject(
    val id: String,
    val kind: ObjectKind,
    val bounds: Aabb,
    val color: FloatArray,
    /** Blocks player movement. */
    val solid: Boolean,
    /** Blocks interaction rays (walls/furniture occlude evidence behind them). */
    val occludes: Boolean,
)

data class PlacedEvidence(
    val id: String,
    val type: EvidenceType,
    /** Collision / pick box: the item's footprint turned by [yawDeg], made axis-aligned. */
    val bounds: Aabb,
    /** The spawn point used, or "floor" / "top:<surface id>" for randomly placed items. */
    val spawnPointId: String,
    val locationLabel: String,
    /** Turn about the vertical axis (degrees, as drawn by the renderer). 0 for spawn-point layouts. */
    val yawDeg: Float = 0f,
)

/** One printed police-tape strip between two points (rendered only; never blocks movement). */
data class TapeLine(val a: Vec3, val b: Vec3)

data class SceneLayout(
    val template: ScenarioTemplate,
    val seed: Int,
    val objects: List<SceneObject>,
    val evidence: List<PlacedEvidence>,
    val sceneLog: Aabb,
    val start: PlayerStart,
    val tapes: List<TapeLine> = emptyList(),
    /** The victim's body, when the case has one (it is also in [objects] as a solid VICTIM). */
    val victim: VictimBody? = null,
)

class SceneGenerationException(message: String) : Exception(message)

/**
 * Deterministic, seeded scene assembler (plan section 5.5).
 * Template + server manifest + seed -> validated placement. Identical inputs always produce
 * identical output. Evidence is never placed inside furniture or other evidence, and every item
 * must be reachable from the start position within interaction range.
 */
object SceneGenerator {
    const val PLAYER_RADIUS = 0.28f
    const val INTERACT_RANGE = 2.2f
    private const val WALL_T = 0.2f
    private const val CELL = 0.2f

    fun generate(template: ScenarioTemplate, manifest: List<ManifestItem>, seed: Int, victim: Victim? = null): SceneLayout {
        val rng = Random(seed)
        val room = template.room
        val objects = mutableListOf<SceneObject>()
        val wallColor = parseHexColor(room.wallColor)
        val floorColor = parseHexColor(room.floorColor)

        objects += SceneObject("floor", ObjectKind.FLOOR,
            Aabb(Vec3(-WALL_T, -0.1f, -WALL_T), Vec3(room.width + WALL_T, 0f, room.depth + WALL_T)), floorColor, solid = false, occludes = true)
        objects += SceneObject("ceiling", ObjectKind.CEILING,
            Aabb(Vec3(-WALL_T, room.height, -WALL_T), Vec3(room.width + WALL_T, room.height + 0.1f, room.depth + WALL_T)),
            floatArrayOf(0.16f, 0.18f, 0.22f), solid = false, occludes = true)
        objects += walls(template, wallColor)

        template.furniture.forEach { f ->
            objects += SceneObject(f.id, ObjectKind.FURNITURE, Aabb.onSurface(f.x, 0f, f.z, f.w, f.h, f.d),
                parseHexColor(f.color), solid = true, occludes = true)
        }
        val logBox = Aabb.onSurface(template.sceneLog.x, 0f, template.sceneLog.z, 0.45f, 1.0f, 0.35f)
        objects += SceneObject("scene_log", ObjectKind.SCENE_LOG, logBox, floatArrayOf(0.22f, 0.34f, 0.5f), solid = true, occludes = true)
        objects += tape(template)

        val solids = objects.filter { it.solid }.map { it.bounds }
        val reach = ReachabilityGrid(template, solids)
        if (!reach.isFree(template.playerStart.x, template.playerStart.z)) {
            throw SceneGenerationException("Player start is blocked")
        }

        val placed = mutableListOf<PlacedEvidence>()
        if (template.randomPlacement) {
            placed += RandomPlacer(template, objects, reach, rng).placeAll(manifest)
        } else {
            placeOnSpawnPoints(template, manifest, rng, objects, reach, placed)
        }
        // The body goes in last, on its own random stream: evidence positions never depend on it.
        val body = victim?.let { VictimPlacer.place(template, seed, it, objects, placed) }
        if (body != null) {
            objects += SceneObject("victim", ObjectKind.VICTIM, body.bounds, floatArrayOf(0f, 0f, 0f), solid = true, occludes = false)
        }
        return SceneLayout(template, seed, objects, placed, logBox, template.playerStart, tapeLines(template), body)
    }

    /** Original placement (layouts made before random placement): one item per fixed spawn point. */
    private fun placeOnSpawnPoints(
        template: ScenarioTemplate, manifest: List<ManifestItem>, rng: Random, objects: List<SceneObject>,
        reach: ReachabilityGrid, placed: MutableList<PlacedEvidence>,
    ) {
        val usedPoints = mutableSetOf<String>()
        for (item in manifest) {
            val type = EvidenceType.from(item.type) ?: throw SceneGenerationException("Unknown evidence type ${item.type}")
            val candidates = template.spawnPoints
                .filter { it.surface in type.surfaces && it.id !in usedPoints }
                .shuffled(rng)
                // Latent prints prefer glass surfaces when available.
                .sortedBy { if (type == EvidenceType.LATENT_PRINT && !it.label.contains("glass", true)) 1 else 0 }
            var chosen: PlacedEvidence? = null
            for (point in candidates) {
                for (attempt in 0 until 6) {
                    val jitter = if (attempt == 5) 0f else 0.18f
                    val jx = (rng.nextFloat() * 2 - 1) * jitter
                    val jz = (rng.nextFloat() * 2 - 1) * jitter
                    val (w, h, d) = type.size
                    val box = Aabb.onSurface(point.x + jx, point.y, point.z + jz, w, h, d)
                    if (isValid(box, point, template, objects, placed, reach)) {
                        chosen = PlacedEvidence(item.id, type, box, point.id, point.label)
                        break
                    }
                }
                if (chosen != null) break
            }
            chosen ?: throw SceneGenerationException("No valid spawn point for ${item.id} (${item.type})")
            usedPoints += chosen.spawnPointId
            placed += chosen
        }
    }

    /**
     * Random placement: each item lands on a random free spot of the floor or of a furniture top,
     * turned to a random angle. Everything comes from the seeded [rng], so the same case builds
     * the same scene for every student, while a new case in the same room looks different.
     * Items are kept apart, clear of furniture and of the doorway, and within reach.
     */
    private class RandomPlacer(
        private val t: ScenarioTemplate,
        private val objects: List<SceneObject>,
        private val reach: ReachabilityGrid,
        private val rng: Random,
    ) {
        private val blockers = objects.filter { it.kind == ObjectKind.FURNITURE || it.kind == ObjectKind.SCENE_LOG }
        private val placed = mutableListOf<PlacedEvidence>()
        private val usage = mutableMapOf<String, Int>()

        fun placeAll(manifest: List<ManifestItem>): List<PlacedEvidence> {
            for (item in manifest) {
                val type = EvidenceType.from(item.type) ?: throw SceneGenerationException("Unknown evidence type ${item.type}")
                val chosen = randomSpot(item.id, type) ?: scanSpot(item.id, type)
                    ?: throw SceneGenerationException("No free spot for ${item.id} (${item.type})")
                usage.merge(chosen.spawnPointId, 1, Int::plus)
                placed += chosen
            }
            return placed
        }

        /** Floor weight vs. furniture tops; tops are weighted by size, and used tops count less. */
        private fun pickArea(type: EvidenceType): String? {
            val options = mutableListOf<Pair<String, Float>>()
            val onFloor = "floor" in type.surfaces
            val onTops = "furniture" in type.surfaces && t.surfaces.isNotEmpty()
            if (onFloor) options += FLOOR_ID to (if (onTops) 1f else 2f) / (1f + 0.25f * (usage[FLOOR_ID] ?: 0))
            if (onTops) {
                val total = t.surfaces.sumOf { sqrt(area(it)).toDouble() }.toFloat().coerceAtLeast(1e-3f)
                for (s in t.surfaces) {
                    var w = sqrt(area(s)) / total * (if (onFloor) 1f else 2f)
                    // Latent prints show up best on smooth glass tops, so those are likelier.
                    if (type == EvidenceType.LATENT_PRINT && s.label.contains("glass", true)) w *= 3f
                    options += topId(s) to w / (1f + 1.5f * (usage[topId(s)] ?: 0))
                }
            }
            if (options.isEmpty()) return null
            var r = rng.nextFloat() * options.sumOf { it.second.toDouble() }.toFloat()
            for ((id, w) in options) {
                r -= w
                if (r <= 0f) return id
            }
            return options.last().first
        }

        private fun randomSpot(id: String, type: EvidenceType): PlacedEvidence? {
            repeat(ATTEMPTS) { attempt ->
                val areaId = pickArea(type) ?: return null
                val yaw = rng.nextFloat() * 360f
                // Spread items out at first; accept closer neighbours only when the room is crowded.
                val spacing = if (attempt < ATTEMPTS * 2 / 3) 0.4f else 0.15f
                candidate(id, type, areaId, yaw, rng.nextFloat(), rng.nextFloat(), spacing)?.let { return it }
            }
            return null
        }

        /** Last resort: every area on a fine grid in a seeded order, items axis-aligned. */
        private fun scanSpot(id: String, type: EvidenceType): PlacedEvidence? {
            val areas = buildList {
                if ("floor" in type.surfaces) add(FLOOR_ID)
                if ("furniture" in type.surfaces) t.surfaces.forEach { add(topId(it)) }
            }
            val tries = areas.flatMap { a -> (0..10).flatMap { i -> (0..10).flatMap { j -> listOf(0f, 90f).map { y -> Triple(a, i / 10f to j / 10f, y) } } } }
            for ((a, uv, yaw) in tries.shuffled(rng)) {
                candidate(id, type, a, yaw, uv.first, uv.second, 0.12f)?.let { return it }
            }
            return null
        }

        private fun candidate(id: String, type: EvidenceType, areaId: String, yawDeg: Float, u: Float, v: Float, spacing: Float): PlacedEvidence? {
            val (w, h, d) = type.size
            val r = Math.toRadians(yawDeg.toDouble())
            val c = abs(cos(r)).toFloat()
            val s = abs(sin(r)).toFloat()
            val ex = (c * w + s * d) / 2
            val ez = (s * w + c * d) / 2
            val surface = if (areaId == FLOOR_ID) null else t.surfaces.first { topId(it) == areaId }
            val (x0, z0, x1, z1) = surface?.let { listOf(it.x0 + ex + 0.02f, it.z0 + ez + 0.02f, it.x1 - ex - 0.02f, it.z1 - ez - 0.02f) }
                ?: listOf(0.3f + ex, 0.3f + ez, t.room.width - 0.3f - ex, t.room.depth - 0.3f - ez)
            if (x1 < x0 || z1 < z0) return null
            val x = x0 + (x1 - x0) * u
            val z = z0 + (z1 - z0) * v
            val y = surface?.y ?: 0f
            val box = Aabb(Vec3(x - ex, y, z - ez), Vec3(x + ex, y + h, z + ez))
            if (placed.any { it.bounds.overlapsXZ(box, spacing) }) return null
            if (surface == null) {
                if (blockers.any { it.bounds.overlapsXZ(box, 0.06f) }) return null
                // Not underfoot where the student walks in.
                if (distanceXZ(t.playerStart.x, t.playerStart.z, box) < 0.9f) return null
            } else if (blockers.any { it.bounds.min.y > y + 0.02f && it.bounds.min.y < y + h && it.bounds.overlapsXZ(box) }) {
                return null // something hangs over this top
            }
            if (!reach.canReach(box)) return null
            val label = surface?.label ?: floorLabel(x, z)
            return PlacedEvidence(id, type, box, areaId, label, yawDeg)
        }

        /** "floor by the sofa", "floor near the entrance", "floor in the north-east corner"... */
        private fun floorLabel(x: Float, z: Float): String {
            val near = t.furniture.map { f -> f to distanceXZ(x, z, Aabb.onSurface(f.x, 0f, f.z, f.w, f.h, f.d)) }
                .filter { it.second < 0.8f }.minByOrNull { it.second }
            if (near != null) return "floor by the ${near.first.displayName}"
            val door = doorPoint(t, t.door.offset, 0f, 0f)
            if (kotlin.math.hypot(x - door.x, z - door.z) < 1.8f) return "floor near the entrance"
            val w = t.room.width
            val d = t.room.depth
            val ns = when { z < 1.2f -> "north"; z > d - 1.2f -> "south"; else -> null }
            val ew = when { x < 1.2f -> "west"; x > w - 1.2f -> "east"; else -> null }
            return when {
                ns != null && ew != null -> "floor in the $ns-$ew corner"
                ns != null -> "floor by the $ns wall"
                ew != null -> "floor by the $ew wall"
                abs(x - w / 2) < w * 0.2f && abs(z - d / 2) < d * 0.2f -> "floor in the middle of the room"
                else -> "floor in the ${if (z < d / 2) "north" else "south"}-${if (x < w / 2) "west" else "east"} of the room"
            }
        }

        private fun area(s: TopSurface) = ((s.x1 - s.x0) * (s.z1 - s.z0)).coerceAtLeast(0f)

        companion object {
            const val FLOOR_ID = "floor"
            const val ATTEMPTS = 90
            fun topId(s: TopSurface) = "top:${s.id}"
        }
    }

    private fun isValid(
        box: Aabb, point: SpawnPoint, t: ScenarioTemplate, objects: List<SceneObject>,
        placed: List<PlacedEvidence>, reach: ReachabilityGrid,
    ): Boolean {
        // Inside the room with a margin from walls.
        if (box.min.x < 0.15f || box.min.z < 0.15f || box.max.x > t.room.width - 0.15f || box.max.z > t.room.depth - 0.15f) return false
        // Not overlapping other evidence.
        if (placed.any { it.bounds.overlapsXZ(box, 0.15f) }) return false
        val furniture = objects.filter { it.kind == ObjectKind.FURNITURE || it.kind == ObjectKind.SCENE_LOG }
        if (point.surface == "floor") {
            // Floor items must be clear of furniture footprints.
            if (furniture.any { it.bounds.overlapsXZ(box, 0.05f) }) return false
        } else {
            // Surface items must lie fully on top of the supporting piece.
            val support = furniture.firstOrNull { kotlin.math.abs(it.bounds.max.y - point.y) < 0.02f && it.bounds.overlapsXZ(box) }
                ?: return false
            if (box.min.x < support.bounds.min.x + 0.03f || box.max.x > support.bounds.max.x - 0.03f ||
                box.min.z < support.bounds.min.z + 0.03f || box.max.z > support.bounds.max.z - 0.03f) return false
        }
        return reach.canReach(box)
    }

    private fun walls(t: ScenarioTemplate, color: FloatArray): List<SceneObject> {
        val w = t.room.width
        val d = t.room.depth
        val h = t.room.height
        val list = mutableListOf<SceneObject>()
        fun wall(id: String, minX: Float, minZ: Float, maxX: Float, maxZ: Float) {
            if (maxX - minX > 0.01f && maxZ - minZ > 0.01f) {
                list += SceneObject(id, ObjectKind.WALL, Aabb(Vec3(minX, 0f, minZ), Vec3(maxX, h, maxZ)), color, solid = true, occludes = true)
            }
        }
        val half = t.door.width / 2
        fun split(id: String, along: Boolean, fixedMin: Float, fixedMax: Float, length: Float, isDoorWall: Boolean) {
            if (!isDoorWall) {
                if (along) wall(id, -WALL_T, fixedMin, length + WALL_T, fixedMax) else wall(id, fixedMin, -WALL_T, fixedMax, length + WALL_T)
                return
            }
            val a = (t.door.offset - half).coerceIn(0f, length)
            val b = (t.door.offset + half).coerceIn(0f, length)
            if (along) {
                wall("${id}_a", -WALL_T, fixedMin, a, fixedMax); wall("${id}_b", b, fixedMin, length + WALL_T, fixedMax)
            } else {
                wall("${id}_a", fixedMin, -WALL_T, fixedMax, a); wall("${id}_b", fixedMin, b, fixedMax, length + WALL_T)
            }
        }
        split("wall_north", true, -WALL_T, 0f, w, t.door.wall == "north")
        split("wall_south", true, d, d + WALL_T, w, t.door.wall == "south")
        split("wall_west", false, -WALL_T, 0f, d, t.door.wall == "west")
        split("wall_east", false, w, w + WALL_T, d, t.door.wall == "east")
        // Door threshold block outside the room keeps the player inside the scene.
        val half2 = t.door.width / 2 + 0.1f
        when (t.door.wall) {
            "south" -> wall("door_block", t.door.offset - half2, d + 0.6f, t.door.offset + half2, d + 0.8f)
            "north" -> wall("door_block", t.door.offset - half2, -0.8f, t.door.offset + half2, -0.6f)
            "west" -> wall("door_block", -0.8f, t.door.offset - half2, -0.6f, t.door.offset + half2)
            "east" -> wall("door_block", w + 0.6f, t.door.offset - half2, w + 0.8f, t.door.offset + half2)
        }
        return list
    }

    /** Point beside the door: [along] the door wall, [out] metres outside the room, at height [y]. */
    private fun doorPoint(t: ScenarioTemplate, along: Float, out: Float, y: Float): Vec3 = when (t.door.wall) {
        "south" -> Vec3(along, y, t.room.depth + out)
        "north" -> Vec3(along, y, -out)
        "west" -> Vec3(-out, y, along)
        else -> Vec3(t.room.width + out, y, along)
    }

    /** Barrier posts outside the door that carry the perimeter tape. */
    private fun tape(t: ScenarioTemplate): List<SceneObject> {
        val half = t.door.width / 2
        val post = floatArrayOf(0.12f, 0.13f, 0.15f)
        return listOf(-1, 1).map { side ->
            val p = doorPoint(t, t.door.offset + side * (half + 0.35f), 0.45f, 0f)
            SceneObject("tape_post_$side", ObjectKind.TAPE, Aabb(Vec3(p.x - 0.03f, 0f, p.z - 0.03f), Vec3(p.x + 0.03f, 1.1f, p.z + 0.03f)),
                post, solid = false, occludes = false)
        }
    }

    /**
     * "CRIME SCENE - DO NOT CROSS" tape: an X plus a centre strip across the doorway, and a
     * perimeter line from the wall to two posts and back, slightly sagging like real tape.
     */
    private fun tapeLines(t: ScenarioTemplate): List<TapeLine> {
        val o = t.door.offset
        val half = t.door.width / 2
        val inDoor = 0.1f // inside the wall thickness, so it spans the opening
        fun p(along: Float, out: Float, y: Float) = doorPoint(t, along, out, y)
        val lines = mutableListOf(
            TapeLine(p(o - half, inDoor, 0.45f), p(o + half, inDoor, 1.6f)),
            TapeLine(p(o - half, inDoor, 1.6f), p(o + half, inDoor, 0.45f)),
            TapeLine(p(o - half, inDoor + 0.01f, 1.05f), p(o + half, inDoor + 0.01f, 1.05f)),
        )
        val left = p(o - half - 0.35f, 0.45f, 1.02f)
        val right = p(o + half + 0.35f, 0.45f, 1.02f)
        val sag = p(o, 0.45f, 0.94f)
        lines += TapeLine(p(o - half - 1.1f, 0.21f, 1.0f), left)
        lines += TapeLine(left, sag)
        lines += TapeLine(sag, right)
        lines += TapeLine(right, p(o + half + 1.1f, 0.21f, 1.0f))
        return lines
    }

    /** Coarse occupancy grid used to prove every evidence item is reachable. */
    class ReachabilityGrid(private val t: ScenarioTemplate, solids: List<Aabb>) {
        private val cols = ceil(t.room.width / CELL).toInt()
        private val rows = ceil(t.room.depth / CELL).toInt()
        private val free = BooleanArray(cols * rows)
        private val reached = BooleanArray(cols * rows)

        init {
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = (c + 0.5f) * CELL
                val z = (r + 0.5f) * CELL
                free[r * cols + c] = solids.none { distanceXZ(x, z, it) < PLAYER_RADIUS && it.max.y > 0.15f } &&
                    x > PLAYER_RADIUS && z > PLAYER_RADIUS && x < t.room.width - PLAYER_RADIUS && z < t.room.depth - PLAYER_RADIUS
            }
            val sc = floor(t.playerStart.x / CELL).toInt().coerceIn(0, cols - 1)
            val sr = floor(t.playerStart.z / CELL).toInt().coerceIn(0, rows - 1)
            if (free[sr * cols + sc]) {
                val queue = ArrayDeque<Int>()
                queue.add(sr * cols + sc)
                reached[sr * cols + sc] = true
                while (queue.isNotEmpty()) {
                    val i = queue.poll()!!
                    val r = i / cols
                    val c = i % cols
                    for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr !in 0 until rows || nc !in 0 until cols) continue
                        val ni = nr * cols + nc
                        if (free[ni] && !reached[ni]) { reached[ni] = true; queue.add(ni) }
                    }
                }
            }
        }

        fun isFree(x: Float, z: Float): Boolean {
            val c = floor(x / CELL).toInt()
            val r = floor(z / CELL).toInt()
            return c in 0 until cols && r in 0 until rows && free[r * cols + c]
        }

        /** True when some reachable standing cell is within interaction range of the box. */
        fun canReach(box: Aabb): Boolean {
            val range = INTERACT_RANGE * 0.75f // leave slack for eye height / angle
            for (i in reachedCells) {
                if (distanceXZ((i % cols + 0.5f) * CELL, (i / cols + 0.5f) * CELL, box) <= range) return true
            }
            return false
        }

        /** Indices of the reachable cells, in grid order (same answers as scanning every cell). */
        private val reachedCells: IntArray by lazy { reached.indices.filter { reached[it] }.toIntArray() }
    }
}
