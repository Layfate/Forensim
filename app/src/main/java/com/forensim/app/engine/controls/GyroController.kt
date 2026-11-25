package com.forensim.app.engine.controls

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Optional motion-look (plan section 5.2). Uses the fused GAME_ROTATION_VECTOR (no magnetometer,
 * so no compass jumps) and falls back to ROTATION_VECTOR. Raw angular velocity is never integrated,
 * so there is no long-term drift. The camera view axis (device -Z) is projected into world space,
 * which makes yaw/pitch independent of display rotation. Deltas are smoothed and handed to [onLook].
 */
class GyroController(context: Context, private val onLook: (yaw: Float, pitch: Float) -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val isSupported: Boolean get() = sensor != null

    @Volatile var gain: Float = 1f
    private val rotation = FloatArray(9)
    private var lastYaw = Float.NaN
    private var lastPitch = Float.NaN
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var running = false

    fun start() {
        if (running || sensor == null) return
        running = true
        resetReference()
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    /** Forget the previous orientation so the next sample becomes the new reference. */
    fun resetReference() {
        lastYaw = Float.NaN
        lastPitch = Float.NaN
        smoothYaw = 0f
        smoothPitch = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        // Device -Z axis in world coordinates (X east, Y north, Z up).
        val vx = -rotation[2]
        val vy = -rotation[5]
        val vz = -rotation[8]
        val yaw = atan2(vx, vy)
        val pitch = asin(vz.coerceIn(-1f, 1f))
        if (!lastYaw.isNaN()) {
            var dYaw = yaw - lastYaw
            if (dYaw > Math.PI) dYaw -= (2 * Math.PI).toFloat()
            if (dYaw < -Math.PI) dYaw += (2 * Math.PI).toFloat()
            // Yaw is unstable when looking straight up/down; ignore it there.
            if (abs(pitch) > 1.3f) dYaw = 0f
            val dPitch = pitch - lastPitch
            // Light low-pass filter to remove sensor jitter.
            smoothYaw = smoothYaw * 0.35f + dYaw * 0.65f
            smoothPitch = smoothPitch * 0.35f + dPitch * 0.65f
            onLook(smoothYaw * gain, smoothPitch * gain)
        }
        lastYaw = yaw
        lastPitch = pitch
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
