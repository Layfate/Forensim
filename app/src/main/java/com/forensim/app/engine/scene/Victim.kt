package com.forensim.app.engine.scene

import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.math.distanceXZ
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class VictimSex(val key: String, val label: String) {
    MALE("male", "Male"),
    FEMALE("female", "Female");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

enum class VictimPose(val key: String, val label: String) {
    FACE_UP("face_up", "Face up"),
    FACE_DOWN("face_down", "Face down");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/** A victim the professor put in the scene: who, and how the body lies. */
data class Victim(val sex: VictimSex, val pose: VictimPose) {
    val label: String get() = "${sex.label}, ${pose.label.lowercase()}"

    /** Body length in metres, head to heel. */
    val length: Float get() = if (sex == VictimSex.MALE) 1.76f else 1.64f

    companion object {
        /** Null unless both parts are known (a case without a victim stores neither). */
        fun from(sex: String?, pose: String?): Victim? {
            val s = VictimSex.from(sex) ?: return null
            return Victim(s, VictimPose.from(pose) ?: VictimPose.FACE_DOWN)
        }
    }
}

/**
 * Where the body lies: centred on [center] (floor level), head pointing along [yawDeg]
 * (0 = towards +x, 90 = towards -z). [bounds] is its axis-aligned footprint, used for collision.
 */
data class VictimBody(val victim: Victim, val center: Vec3, val yawDeg: Float, val bounds: Aabb) {
    /** Point on the floor at the top of the head. */
    val head: Vec3
        get() {
            val r = Math.toRadians(yawDeg.toDouble())
            val half = victim.length / 2
            return Vec3(center.x + (cos(r) * half).toFloat(), 0f, center.z - (sin(r) * half).toFloat())
        }
}

/**
 * Lays the body on a clear stretch of floor after the evidence is placed. It uses its own random
 * stream, so a case with a victim keeps exactly the same evidence positions as one without.
 * The body is solid (you walk round it, not through it), so every candidate is checked against
 * reachability: the start stays free and every item stays within reach.
 */
internal object VictimPlacer {
    private const val BODY_WIDTH = 0.62f
    private const val BODY_HEIGHT = 0.26f

    fun place(template: ScenarioTemplate, seed: Int, victim: Victim, objects: List<SceneObject>, evidence: List<PlacedEvidence>): VictimBody {
        val rng = Random(seed * 31 + 7)
        val room = template.room
        val blockers = objects.filter { it.kind == ObjectKind.FURNITURE || it.kind == ObjectKind.SCENE_LOG }.map { it.bounds }
        val start = template.playerStart
        // A bloodstain on the floor is where the body most likely lay: put it alongside (~0.75 m).
        // Otherwise, near the middle of the room.
        val blood = evidence.firstOrNull { it.type == EvidenceType.BLOODSTAIN }?.bounds?.center
        val anchor = blood ?: Vec3(room.width / 2, 0f, room.depth / 2)
        val idealDistance = if (blood != null) 0.75f else 0f

        val candidates = mutableListOf<VictimBody>()
        var x = 0.6f
        while (x < room.width - 0.6f) {
            var z = 0.6f
            while (z < room.depth - 0.6f) {
                for (base in intArrayOf(0, 90, 180, 270)) {
                    val body = body(victim, x, z, base + (rng.nextFloat() * 2 - 1) * 12f)
                    if (fits(body.bounds, room, blockers, evidence, start)) candidates += body
                }
                z += 0.2f
            }
            x += 0.2f
        }
        // Best spots first, with a little seeded variety among the closest few.
        val ranked = candidates.sortedBy { c -> abs(hypot(c.center.x - anchor.x, c.center.z - anchor.z) - idealDistance) }
        val pool = ranked.take(12).shuffled(rng) + ranked.drop(12)
        val solids = objects.filter { it.solid }.map { it.bounds }
        for (c in pool) {
            val grid = SceneGenerator.ReachabilityGrid(template, solids + c.bounds)
            // The start stays free, every item stays within reach, and the body itself can be examined.
            if (grid.isFree(start.x, start.z) && evidence.all { grid.canReach(it.bounds) } && grid.canReach(c.bounds)) return c
        }
        throw SceneGenerationException("No room for the victim's body")
    }

    private fun body(victim: Victim, x: Float, z: Float, yawDeg: Float): VictimBody {
        val r = Math.toRadians(yawDeg.toDouble())
        val hl = victim.length / 2 + 0.06f
        val hw = BODY_WIDTH / 2 + 0.1f // arms out a little
        val ex = (abs(cos(r)) * hl + abs(sin(r)) * hw).toFloat()
        val ez = (abs(sin(r)) * hl + abs(cos(r)) * hw).toFloat()
        return VictimBody(victim, Vec3(x, 0f, z), yawDeg, Aabb(Vec3(x - ex, 0f, z - ez), Vec3(x + ex, BODY_HEIGHT, z + ez)))
    }

    private fun fits(b: Aabb, room: RoomSpec, blockers: List<Aabb>, evidence: List<PlacedEvidence>, start: PlayerStart): Boolean {
        if (b.min.x < 0.3f || b.min.z < 0.3f || b.max.x > room.width - 0.3f || b.max.z > room.depth - 0.3f) return false
        if (blockers.any { it.overlapsXZ(b, 0.12f) }) return false
        // Evidence must stay visible beside the body, never under it.
        if (evidence.any { it.bounds.overlapsXZ(b, 0.18f) }) return false
        return distanceXZ(start.x, start.z, b) > 1.4f
    }
}
