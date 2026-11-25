package com.forensim.app.feature.ar

import android.content.res.AssetManager
import android.opengl.Matrix
import com.forensim.app.engine.renderer.GlesRenderer
import com.forensim.app.engine.renderer.ModelCache
import com.forensim.app.engine.renderer.ModelPaths
import com.forensim.app.engine.scene.EvidenceType

/**
 * Shared by the AR view and the camera-view fallback: one evidence specimen drawn life-size at a
 * given base matrix (its resting point, y up), turned by [yawDeg] and enlarged by [scale], with a
 * 10 cm scale bar in front of it like a forensic photo scale. [show] swaps the specimen (swiping
 * through a category) without restarting the camera; its model loads on first use.
 */
abstract class SpecimenRenderer(type: EvidenceType, private val assets: AssetManager) : GlesRenderer(assets) {
    private class Current(val type: EvidenceType, val path: String, val bounds: FloatArray?) {
        /** Largest horizontal size of the model in metres (e.g. a 0.62 m bat). */
        val extent: Float = bounds?.let { b -> maxOf(b[3] - b[0], b[5] - b[2]) } ?: 0.2f
    }

    @Volatile private var current = load(type)

    @Volatile var yawDeg = 0f
    /** 1 = life size. */
    @Volatile var scale = 1f
    /**
     * Sideways offset while swiping, in screen widths (-1 = gone off the left, 1 = off the right):
     * the specimen follows the finger, then glides out and the next one glides in.
     */
    @Volatile var slide = 0f

    protected val type: EvidenceType get() = current.type
    protected val extent: Float get() = current.extent

    private val m = FloatArray(16)
    private val pointIn = FloatArray(4)
    private val pointEye = FloatArray(4)
    private val pointClip = FloatArray(4)
    private var drawnThisFrame = false
    private val _labelPoint = kotlinx.coroutines.flow.MutableStateFlow<ScreenPoint?>(null)
    /**
     * Where the top of the specimen is on screen (pixels), for the callout line and label drawn
     * over the camera; null when it isn't shown or is off screen.
     */
    val labelPoint: kotlinx.coroutines.flow.StateFlow<ScreenPoint?> = _labelPoint

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        drawnThisFrame = false
        super.onDrawFrame(gl)
        if (!drawnThisFrame) _labelPoint.value = null
    }

    /** Publishes the screen position of [model]'s top-centre point ([x], [y], [z] in model space). */
    private fun publishLabelPoint(model: FloatArray, x: Float, y: Float, z: Float) {
        drawnThisFrame = true
        pointIn[0] = x; pointIn[1] = y; pointIn[2] = z; pointIn[3] = 1f
        Matrix.multiplyMV(pointEye, 0, model, 0, pointIn, 0)
        Matrix.multiplyMV(pointIn, 0, view, 0, pointEye, 0)
        Matrix.multiplyMV(pointClip, 0, projection, 0, pointIn, 0)
        val w = pointClip[3]
        if (w <= 0.01f) { _labelPoint.value = null; return }
        val px = (pointClip[0] / w * 0.5f + 0.5f) * surfaceWidth
        val py = (1f - (pointClip[1] / w * 0.5f + 0.5f)) * surfaceHeight
        val onScreen = px in -50f..(surfaceWidth + 50f) && py in -50f..(surfaceHeight + 50f)
        val old = _labelPoint.value
        // Only re-publish real moves, so the overlay doesn't recompose for sub-pixel tremble.
        if (!onScreen) _labelPoint.value = null
        else if (old == null || kotlin.math.abs(old.x - px) > 0.75f || kotlin.math.abs(old.y - py) > 0.75f) _labelPoint.value = ScreenPoint(px, py)
    }

    private fun load(t: EvidenceType): Current {
        val path = ModelPaths.evidence(t)
        return Current(t, path, ModelCache.get(assets, path)?.bounds)
    }

    /** Show another specimen in the same place; size and turn go back to the start. */
    fun show(t: EvidenceType) {
        if (t == current.type) return
        current = load(t)
        yawDeg = 0f
        scale = 1f
        onSpecimenChanged()
    }

    /** Called (on the UI thread) after [show] swapped the specimen. */
    protected open fun onSpecimenChanged() {}

    override fun preloadModels() = listOf(current.path)

    protected fun drawSpecimen(base: FloatArray, pedestal: Boolean = false) {
        val c = current
        System.arraycopy(base, 0, m, 0, 16)
        val sl = slide
        if (sl != 0f) {
            // Move along the camera's right-hand direction, by about a screen width at this depth.
            val x = m[12]; val y = m[13]; val z = m[14]
            val depth = -(view[2] * x + view[6] * y + view[10] * z + view[14])
            val k = sl * depth.coerceAtLeast(0.2f) * 1.1f
            m[12] += view[0] * k; m[13] += view[4] * k; m[14] += view[8] * k
        }
        Matrix.rotateM(m, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(m, 0, scale, scale, scale)
        if (!pedestal) {
            // Soft contact shadow on the real surface: three faint discs, darkest in the middle.
            val b = c.bounds
            val rx = (if (b != null) (b[3] - b[0]) / 2 else 0.05f).coerceAtLeast(0.015f) * 1.25f
            val rz = (if (b != null) (b[5] - b[2]) / 2 else 0.05f).coerceAtLeast(0.015f) * 1.25f
            for ((grow, opacity) in listOf(1.35f to 0.12f, 1f to 0.16f, 0.7f to 0.2f)) {
                val disc = m.copyOf()
                Matrix.translateM(disc, 0, 0f, 0.0008f, 0f)
                Matrix.scaleM(disc, 0, rx * 2 * grow, 0.0005f, rz * 2 * grow)
                drawShadowDisc(disc, opacity)
            }
        }
        if (pedestal) {
            // A low dark disc under the item, so a specimen floating in the camera view looks grounded.
            val r = (c.extent * 0.75f).coerceIn(0.12f, 0.5f)
            val disc = m.copyOf()
            Matrix.translateM(disc, 0, 0f, -0.01f, 0f)
            Matrix.scaleM(disc, 0, r * 2, 0.02f, r * 2)
            drawCylinderModel(disc, floatArrayOf(0.16f, 0.22f, 0.33f))
        }
        for (i in 0 until 10) {
            val bar = m.copyOf()
            Matrix.translateM(bar, 0, -0.045f + i * 0.01f, 0.0015f, 0.11f)
            Matrix.scaleM(bar, 0, 0.01f, 0.003f, 0.015f)
            drawCubeModel(bar, if (i % 2 == 0) floatArrayOf(0.95f, 0.95f, 0.95f) else floatArrayOf(0.08f, 0.08f, 0.08f))
        }
        val b = c.bounds ?: return
        if (c.type == EvidenceType.LATENT_PRINT) {
            publishLabelPoint(m, 0f, 0.02f, 0f)
            // The print sits on a pane of glass, as in the 3D viewer.
            val glass = m.copyOf()
            Matrix.translateM(glass, 0, 0f, 0.005f, 0f)
            Matrix.scaleM(glass, 0, 0.3f, 0.01f, 0.3f)
            drawCubeModel(glass, floatArrayOf(0.55f, 0.65f, 0.75f))
            Matrix.translateM(m, 0, 0f, 0.01f, 0f)
        } else {
            // Centre the model on the base point and stand it on it.
            Matrix.translateM(m, 0, -(b[0] + b[3]) / 2, -b[1], -(b[2] + b[5]) / 2)
            publishLabelPoint(m, (b[0] + b[3]) / 2, b[4], (b[2] + b[5]) / 2)
        }
        drawModelMatrix(c.path, m)
    }
}

/** A point on the screen in pixels (from the GL thread to the overlay). */
data class ScreenPoint(val x: Float, val y: Float)
