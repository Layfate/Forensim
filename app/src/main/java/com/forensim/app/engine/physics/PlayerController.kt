package com.forensim.app.engine.physics

import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Static collision world (plan section 5.3 CollisionWorld). Uses explicit collider boxes rather
 * than visual meshes, and resolves player movement as a circle against boxes in the XZ plane.
 */
class CollisionWorld(colliders: List<Aabb>, private val roomMinX: Float, private val roomMinZ: Float,
                     private val roomMaxX: Float, private val roomMaxZ: Float) {
    // Only boxes tall enough to block a walking person.
    private val blockers = colliders.filter { it.max.y > 0.15f }

    fun blocked(x: Float, z: Float, radius: Float): Boolean {
        if (x < roomMinX + radius || x > roomMaxX - radius || z < roomMinZ + radius || z > roomMaxZ - radius) return true
        return blockers.any { b ->
            val cx = x.coerceIn(b.min.x, b.max.x)
            val cz = z.coerceIn(b.min.z, b.max.z)
            val dx = x - cx
            val dz = z - cz
            dx * dx + dz * dz < radius * radius
        }
    }

    /** Axis-separated slide: try X then Z so the player glides along walls instead of sticking. */
    fun move(fromX: Float, fromZ: Float, dx: Float, dz: Float, radius: Float): Pair<Float, Float> {
        var x = fromX
        var z = fromZ
        // Sub-step large moves so fast frames cannot tunnel through thin walls.
        val steps = maxOf(1, (sqrt(dx * dx + dz * dz) / (radius * 0.5f)).toInt() + 1)
        val sx = dx / steps
        val sz = dz / steps
        repeat(steps) {
            if (!blocked(x + sx, z, radius)) x += sx
            if (!blocked(x, z + sz, radius)) z += sz
        }
        return x to z
    }
}

/** First-person character controller (plan section 5.3 PlayerController). */
class PlayerController(
    startX: Float,
    startZ: Float,
    startYawDegrees: Float,
    private val world: CollisionWorld,
) {
    var x = startX; private set
    var z = startZ; private set
    /** Radians. 0 looks toward -Z; positive turns right. */
    var yaw = Math.toRadians(startYawDegrees.toDouble()).toFloat(); private set
    /** Radians. Positive looks up. */
    var pitch = 0f; private set

    val eyeHeight = 1.6f
    val radius = 0.28f
    var walkSpeed = 1.7f

    val eye: Vec3 get() = Vec3(x, eyeHeight, z)

    val forward: Vec3
        get() = Vec3(sin(yaw) * cos(pitch), sin(pitch), -cos(yaw) * cos(pitch))

    /**
     * @param moveX strafe right (-1..1); @param moveY forward (-1..1), already dead-zoned.
     * @param lookYaw / lookPitch accumulated look deltas in radians.
     */
    fun update(dt: Float, moveX: Float, moveY: Float, lookYaw: Float, lookPitch: Float) {
        yaw = wrap(yaw + lookYaw)
        pitch = (pitch + lookPitch).coerceIn(-MAX_PITCH, MAX_PITCH)
        val len = sqrt(moveX * moveX + moveY * moveY)
        if (len > 0.001f) {
            val scale = (if (len > 1f) 1f / len else 1f) * walkSpeed * dt
            val fx = sin(yaw)
            val fz = -cos(yaw)
            val rx = cos(yaw)
            val rz = sin(yaw)
            val dx = (fx * moveY + rx * moveX) * scale
            val dz = (fz * moveY + rz * moveX) * scale
            val (nx, nz) = world.move(x, z, dx, dz, radius)
            x = nx; z = nz
        }
    }

    fun recenterPitch() {
        pitch = 0f
    }

    private fun wrap(a: Float): Float {
        var v = a
        val twoPi = (Math.PI * 2).toFloat()
        while (v > Math.PI) v -= twoPi
        while (v < -Math.PI) v += twoPi
        return v
    }

    companion object {
        val MAX_PITCH = Math.toRadians(80.0).toFloat()
    }
}

/** Joystick dead-zone with rescaling so movement starts smoothly at the edge of the zone. */
fun applyDeadZone(x: Float, y: Float, deadZone: Float = 0.15f): Pair<Float, Float> {
    val len = sqrt(x * x + y * y)
    if (len < deadZone) return 0f to 0f
    val clamped = minOf(len, 1f)
    val scaled = (clamped - deadZone) / (1f - deadZone)
    return (x / len * scaled) to (y / len * scaled)
}
