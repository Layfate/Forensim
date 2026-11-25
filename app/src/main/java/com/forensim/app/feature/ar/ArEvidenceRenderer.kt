package com.forensim.app.feature.ar

import android.content.res.AssetManager
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.scene.EvidenceType
import com.google.ar.core.Anchor
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.SessionPausedException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** What the AR view is doing, for the hint at the bottom of the screen. */
enum class ArPhase { STARTING, SEARCHING, READY_TO_PLACE, PLACED, TRACKING_LOST }

data class ArStatus(val phase: ArPhase = ArPhase.STARTING, val problem: String? = null)

/**
 * Real AR: the phone camera fills the screen (ARCore), flat surfaces are found as the phone
 * moves, and one evidence specimen is placed life-size on a table or floor with a tap.
 * A ring in the middle of the screen shows where it will land. Drag sideways to turn it and
 * pinch to enlarge it (small items like a bullet are easier to inspect at 2-4x).
 *
 * Reuses the app's own GL renderer and .glb models: ARCore only supplies the camera image, the
 * camera's view and projection, and the surfaces.
 */
class ArEvidenceRenderer(
    type: EvidenceType,
    private val session: Session,
    assets: AssetManager,
    /** Display rotation (Surface.ROTATION_*), read when the surface size changes. */
    private val displayRotation: () -> Int,
) : SpecimenRenderer(type, assets) {
    private val taps = ConcurrentLinkedQueue<FloatArray>()
    @Volatile private var clearRequested = false

    private val _status = MutableStateFlow(ArStatus())
    val status: StateFlow<ArStatus> = _status.asStateFlow()

    private var anchor: Anchor? = null
    private var viewWidth = 1
    private var viewHeight = 1
    private var geometryDirty = true

    // Camera image background.
    private var cameraTexture = 0
    private var bgProgram = 0
    private var bgPosition = 0
    private var bgTexCoord = 0
    private val quadCoords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val quad: FloatBuffer = floatBuffer(quadCoords)
    private val quadTex: FloatBuffer = floatBuffer(FloatArray(8))

    private val anchorMatrix = FloatArray(16)
    private val m = FloatArray(16)
    private var reticle: Pose? = null
    private var frame: Frame? = null

    init {
        ambient = 0.45f
        flashlight = 0.5f
    }

    /** Queue a tap (view pixels) to place or move the specimen; handled on the GL thread. */
    fun tap(x: Float, y: Float) { taps.add(floatArrayOf(x, y)) }

    /** Remove the placed specimen so it can be placed again. */
    fun clear() { clearRequested = true }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        super.onSurfaceCreated(gl, config)
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        cameraTexture = tex[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        session.setCameraTextureName(cameraTexture)
        bgProgram = program(BG_VERTEX, BG_FRAGMENT)
        bgPosition = GLES30.glGetAttribLocation(bgProgram, "aPosition")
        bgTexCoord = GLES30.glGetAttribLocation(bgProgram, "aTexCoord")
        geometryDirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        viewWidth = width; viewHeight = height
        geometryDirty = true
    }

    override fun update(dt: Float) {
        if (geometryDirty) {
            session.setDisplayGeometry(displayRotation(), viewWidth, viewHeight)
            geometryDirty = false
        }
        val f = try {
            session.update()
        } catch (e: SessionPausedException) {
            frame = null; return
        } catch (e: Exception) {
            Log.w("ForensimAR", "ARCore update failed: ${e.message}")
            frame = null; return
        }
        frame = f
        if (f.hasDisplayGeometryChanged()) {
            quad.position(0)
            quadTex.position(0)
            f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, quadTex)
        }
        val camera = f.camera
        if (clearRequested) { anchor?.detach(); anchor = null; clearRequested = false }

        if (camera.trackingState != TrackingState.TRACKING) {
            reticle = null
            taps.clear()
            val why = when (camera.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark. Turn on a light."
                TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast. Move the phone slowly."
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point at a surface with some texture (not a plain white wall)."
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "The camera is in use by another app."
                else -> null
            }
            _status.value = ArStatus(if (anchor != null) ArPhase.TRACKING_LOST else ArPhase.STARTING, why)
            return
        }

        camera.getProjectionMatrix(projection, 0, 0.03f, 30f)
        camera.getViewMatrix(view, 0)
        val pose = camera.displayOrientedPose
        val forward = pose.zAxis.let { Vec3(-it[0], -it[1], -it[2]) }
        setEye(Vec3(pose.tx(), pose.ty(), pose.tz()), forward)
        f.lightEstimate?.takeIf { it.state == com.google.ar.core.LightEstimate.State.VALID }?.let {
            ambient = (it.pixelIntensity * 0.75f).coerceIn(0.25f, 0.75f)
        }

        // Where a tap in the middle of the screen would land: shown as a ring.
        reticle = hitSurface(f, viewWidth / 2f, viewHeight / 2f)
        while (true) {
            val t = taps.poll() ?: break
            hitSurface(f, t[0], t[1])?.let { hit ->
                anchor?.detach()
                anchor = session.createAnchor(hit)
            }
        }
        val a = anchor
        _status.value = ArStatus(when {
            a != null && a.trackingState == TrackingState.TRACKING -> ArPhase.PLACED
            a != null -> ArPhase.TRACKING_LOST
            reticle != null -> ArPhase.READY_TO_PLACE
            else -> ArPhase.SEARCHING
        })
    }

    /** First hit on a detected surface (inside its outline), facing up for floors and tables. */
    private fun hitSurface(f: Frame, x: Float, y: Float): Pose? =
        f.hitTest(x, y).firstOrNull { hit ->
            val t = hit.trackable
            t is Plane && t.isPoseInPolygon(hit.hitPose) && t.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                t.trackingState == TrackingState.TRACKING
        }?.hitPose

    override fun drawBackground() {
        val f = frame ?: return
        if (f.timestamp == 0L) return // no camera image yet
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(bgProgram)
        // The 3D engine leaves its last vertex array / buffer bound; with one bound, the plain
        // (client-side) arrays below are refused and nothing is drawn, i.e. a black background.
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture)
        quad.position(0); quadTex.position(0)
        GLES30.glVertexAttribPointer(bgPosition, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glVertexAttribPointer(bgTexCoord, 2, GLES30.GL_FLOAT, false, 0, quadTex)
        GLES30.glEnableVertexAttribArray(bgPosition)
        GLES30.glEnableVertexAttribArray(bgTexCoord)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(bgPosition)
        GLES30.glDisableVertexAttribArray(bgTexCoord)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    override fun draw() {
        if (frame?.camera?.trackingState != TrackingState.TRACKING) return
        val a = anchor
        if (a != null && a.trackingState == TrackingState.TRACKING) {
            a.pose.toMatrix(anchorMatrix, 0)
            roomLight = Vec3(anchorMatrix[12], anchorMatrix[13] + 1.5f, anchorMatrix[14])
            drawSpecimen(anchorMatrix)
        } else {
            reticle?.let { p ->
                p.toMatrix(m, 0)
                // A flat white ring (thin disc with a darker centre) marking where it will go.
                val ring = m.copyOf()
                Matrix.scaleM(ring, 0, 0.16f, 0.002f, 0.16f)
                drawCylinderModel(ring, floatArrayOf(0.95f, 0.95f, 0.95f), 0.9f)
                val inner = m.copyOf()
                Matrix.translateM(inner, 0, 0f, 0.0015f, 0f)
                Matrix.scaleM(inner, 0, 0.12f, 0.002f, 0.12f)
                drawCylinderModel(inner, floatArrayOf(0.45f, 0.1f, 0.18f), 0.4f)
            }
        }
    }

    private fun program(vs: String, fs: String): Int {
        fun compile(kind: Int, src: String) = GLES30.glCreateShader(kind).also {
            GLES30.glShaderSource(it, src); GLES30.glCompileShader(it)
        }
        return GLES30.glCreateProgram().also { p ->
            GLES30.glAttachShader(p, compile(GLES30.GL_VERTEX_SHADER, vs))
            GLES30.glAttachShader(p, compile(GLES30.GL_FRAGMENT_SHADER, fs))
            GLES30.glLinkProgram(p)
        }
    }

    private companion object {
        fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }

        const val BG_VERTEX = """
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
"""
        const val BG_FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
varying vec2 vTexCoord;
uniform samplerExternalOES uCamera;
void main() { gl_FragColor = texture2D(uCamera, vTexCoord); }
"""
    }
}
