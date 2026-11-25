package com.forensim.app.engine.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Short interface sounds for the 3D lab. */
enum class Sfx { SUCCESS, WARNING, INFO }

/**
 * Tiny sound-effect player. The tones are synthesised once into WAV files in the cache directory,
 * so the app ships no audio assets. Volume and on/off come from the Sound settings.
 */
class SoundFx(context: Context) {
    private val appContext = context.applicationContext
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = HashMap<Sfx, Int>()

    init {
        val dir = File(appContext.cacheDir, "sfx").apply { mkdirs() }
        Sfx.entries.forEach { sfx ->
            val file = File(dir, "${sfx.name.lowercase()}_v1.wav")
            if (!file.exists()) file.writeBytes(wav(synth(sfx)))
            ids[sfx] = pool.load(file.absolutePath, 1)
        }
    }

    fun play(sfx: Sfx, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        if (v <= 0f) return
        ids[sfx]?.let { pool.play(it, v, v, 1, 0, 1f) }
    }

    /** A short buzz for warnings (or a light tick otherwise). No-op without a vibrator. */
    fun vibrate(sfx: Sfx) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = when (sfx) {
            Sfx.WARNING -> VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 60), -1)
            Sfx.SUCCESS -> VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            Sfx.INFO -> return
        }
        runCatching { vibrator.vibrate(effect) }
    }

    fun release() = pool.release()

    private fun synth(sfx: Sfx): ShortArray = when (sfx) {
        // Two rising notes (E5 then A5).
        Sfx.SUCCESS -> tone(659.3, 0.09) + tone(880.0, 0.14)
        // Low double pulse.
        Sfx.WARNING -> tone(233.1, 0.12, square = true) + silence(0.05) + tone(196.0, 0.16, square = true)
        // Soft single blip.
        Sfx.INFO -> tone(523.3, 0.07)
    }

    private fun tone(freq: Double, seconds: Double, square: Boolean = false): ShortArray {
        val n = (RATE * seconds).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val s = sin(2 * PI * freq * t)
            val wave = if (square) (if (s >= 0) 0.6 else -0.6) * 0.5 + s * 0.5 else s
            // 5 ms attack, exponential decay: no clicks at either end.
            val envelope = minOf(1.0, t / 0.005) * exp(-4.0 * t / seconds)
            (wave * envelope * 0.45 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun silence(seconds: Double) = ShortArray((RATE * seconds).toInt())

    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        return ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
            samples.forEach { putShort(it) }
        }.array()
    }

    private companion object {
        const val RATE = 22050
    }
}
