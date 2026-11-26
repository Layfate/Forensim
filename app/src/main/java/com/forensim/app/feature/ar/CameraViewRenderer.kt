package com.forensim.app.feature.ar

import android.content.res.AssetManager
import android.opengl.Matrix
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.scene.EvidenceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.exp

/**
 * AR for phones without ARCore. The live camera image ([CameraFeed]) is drawn full-screen, then
 * the specimen, in one of two ways:
 *
 *  - **Marker mode** (the printed ForenSim marker is in view): OpenCV measures where the marker is
 *    ([MarkerTracker]), so the specimen stands on it at true size and stays there as the student
 *    moves and walks around it, like ARCore but only around the marker.
 *  - **Sensor mode** (no marker): the phone's rotation sensor holds the specimen still while the
 *    phone turns (not while it moves). Pointing down at a table puts it on a surface ~40 cm below.
 *
 * Sensor-mode world axes: x east, y up, z south (looking north = looking along -z, like OpenGL).
 */
class CameraViewRenderer(type: EvidenceType, assets: AssetManager, private val feed: CameraFeed) : SpecimenRenderer(type, assets) {
    private var viewWidth = 1
    private var viewHeight = 1

    private val lock = Any()
    private val rotation = FloatArray(9)
    @Volatile private var hasRotation = false
    @Volatile private var recenterRequested = true
    private var anchor: Vec3? = null
    /** Sensor mode: true when the specimen was put on the (estimated) table, false when it floats. */
    @Volatile var onTable = false; private set
    private val r = FloatArray(9)
    private val base = FloatArray(16)

    /** Set when OpenCV and the camera's lens data are available. */
    @Volatile var tracker: MarkerTracker? = null
    private val markerTarget = FloatArray(16)
    private val markerSmooth = FloatArray(16)
    private var markerSeen = false
    private val _markerVisible = MutableStateFlow(false)
    /** Whether the specimen is standing on the marker right now (for the hint on screen). */
    val markerVisible: StateFlow<Boolean> = _markerVisible.asStateFlow()

    /** How far in front of the phone the specimen floats: further for big items so they fit. */
    private val distance get() = (extent * 1.8f).coerceIn(0.35f, 1.4f)

    // A new specimen appears where the phone points now, at a distance that fits its size.
    override fun onSpecimenChanged() { recenterRequested = true }

    init {
        clearColor = floatArrayOf(0f, 0f, 0f)
        ambient = 0.5f
        flashlight = 0.5f
    }

    /**
     * Latest phone orientation: a 3x3 row-major matrix mapping phone axes (x right, y up the
     * screen, z out of the screen) to east-north-up, already adjusted for the screen rotation.
     */
    fun onRotation(m: FloatArray) {
        synchronized(lock) { System.arraycopy(m, 0, rotation, 0, 9) }
        hasRotation = true
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        super.onSurfaceCreated(gl, config)
        feed.onGlCreated()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        super.onSurfaceChanged(gl, width, height)
        viewWidth = width; viewHeight = height
    }

    // The live camera image, behind the specimen.
    override fun drawBackground() = feed.draw(viewWidth, viewHeight)

    private companion object {
        /** How far below the phone the table is assumed to be: a phone held over a desk, in metres. */
        const val TABLE_DROP = 0.4f
        /** A marker sighting older than this no longer counts as "in view". */
        const val MARKER_FRESH_NS = 350_000_000L
        const val NEAR = 0.02f
        const val FAR = 20f
    }

    /** Put the specimen back in the middle of the screen (on the table if the phone points down at one). */
    fun bringToFront() { recenterRequested = true }

    // Roughly a phone camera's view, so the item's size looks right against the room.
    override fun fieldOfView() = if (aspect > 1f) 48f else 62f
    override fun clipRange() = NEAR to FAR

    override fun update(dt: Float) {
        val d = tracker?.latest?.takeIf { System.nanoTime() - it.timeNanos < MARKER_FRESH_NS }
        if (d != null) {
            updateFromMarker(d, dt)
            _markerVisible.value = true
            return
        }
        if (markerSeen) {
            // Lost the marker: back to sensor mode, starting again where the phone points.
            markerSeen = false
            recenterRequested = true
        }
        _markerVisible.value = false
        updateFromSensor()
    }

    /**
     * Marker mode: the camera is the origin (view = identity) and the specimen's matrix is the
     * marker's measured pose. OpenCV works in the sensor's orientation (x right, y down, z
     * forward); the screen shows the image turned upright, and OpenGL has y up and z backward.
     */
    private fun updateFromMarker(det: MarkerDetection, dt: Float) {
        val rot = Math.floorMod(det.rotationDegrees, 360)
        // D: sensor camera -> upright screen camera (turn about the viewing axis).
        val dm = when (rot) {
            90 -> floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
            180 -> floatArrayOf(-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
            270 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        }
        // G = diag(1, -1, -1): OpenCV camera -> OpenGL camera. A: model (y up, z front) -> marker
        // (x right, y up the sheet, z out of the sheet): columns x, z_marker, -y_marker.
        val a = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        val gd = mul3(floatArrayOf(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f), dm)
        val rm = mul3(mul3(gd, det.rotation), a)
        val t = det.translation
        val tm = FloatArray(3) { i -> gd[i * 3] * t[0] + gd[i * 3 + 1] * t[1] + gd[i * 3 + 2] * t[2] }
        Matrix.setIdentityM(markerTarget, 0)
        for (row in 0..2) for (col in 0..2) markerTarget[col * 4 + row] = rm[row * 3 + col]
        markerTarget[12] = tm[0]; markerTarget[13] = tm[1]; markerTarget[14] = tm[2]

        if (!markerSeen) {
            markerSeen = true
            System.arraycopy(markerTarget, 0, markerSmooth, 0, 16)
            yawDeg = 0f // face the way the sheet is printed
        } else {
            // Smooth out the measurement's tremble while staying quick to follow real motion.
            val k = 1f - exp(-dt * 22f)
            for (i in 0 until 16) markerSmooth[i] += (markerTarget[i] - markerSmooth[i]) * k
        }

        Matrix.setIdentityM(view, 0)
        setEye(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -1f))
        setMarkerProjection(det, rot)
        // Light from above the marker, a little towards the student.
        roomLight = Vec3(markerSmooth[12] + markerSmooth[4] + markerSmooth[8] * 0.3f,
            markerSmooth[13] + markerSmooth[5] + markerSmooth[9] * 0.3f,
            markerSmooth[14] + markerSmooth[6] + markerSmooth[10] * 0.3f)
    }

    /**
     * Projection from the camera's real lens numbers, cropped exactly like the camera image on
     * screen ([CameraFeed] fills the view the same way), so the specimen lines up with the marker.
     */
    private fun setMarkerProjection(det: MarkerDetection, rot: Int) {
        val turned = rot == 90 || rot == 270
        val w = (if (turned) det.imageHeight else det.imageWidth).toFloat()
        val h = (if (turned) det.imageWidth else det.imageHeight).toFloat()
        val (fx, fy, cx, cy) = when (rot) {
            90 -> listOf(det.fy, det.fx, det.imageHeight - det.cy, det.cx)
            180 -> listOf(det.fx, det.fy, det.imageWidth - det.cx, det.imageHeight - det.cy)
            270 -> listOf(det.fy, det.fx, det.cy, det.imageWidth - det.cx)
            else -> listOf(det.fx, det.fy, det.cx, det.cy)
        }
        val bufAspect = w / h
        val viewAspect = viewWidth.toFloat() / viewHeight.coerceAtLeast(1)
        val sx = if (viewAspect < bufAspect) viewAspect / bufAspect else 1f
        val sy = if (viewAspect < bufAspect) 1f else bufAspect / viewAspect
        java.util.Arrays.fill(projection, 0f)
        projection[0] = 2f * fx / (w * sx)
        projection[5] = 2f * fy / (h * sy)
        projection[8] = (w - 2f * cx) / (w * sx)
        projection[9] = (2f * cy - h) / (h * sy)
        projection[10] = -(FAR + NEAR) / (FAR - NEAR)
        projection[11] = -1f
        projection[14] = -2f * FAR * NEAR / (FAR - NEAR)
    }

    private fun updateFromSensor() {
        if (!hasRotation) return
        Matrix.perspectiveM(projection, 0, fieldOfView(), aspect, NEAR, FAR)
        synchronized(lock) { System.arraycopy(rotation, 0, r, 0, 9) }
        // Camera-to-world (ENU -> x east, y up, z south): rows are R's rows 0, 2 and -1.
        val c = arrayOf(
            floatArrayOf(r[0], r[1], r[2]),
            floatArrayOf(r[6], r[7], r[8]),
            floatArrayOf(-r[3], -r[4], -r[5]),
        )
        // View = inverse rotation = transpose; Matrix wants column-major: view[col*4+row] = c[col][row].
        Matrix.setIdentityM(view, 0)
        for (row in 0..2) for (col in 0..2) view[col * 4 + row] = c[col][row]
        val forward = Vec3(-c[0][2], -c[1][2], -c[2][2])
        setEye(Vec3(0f, 0f, 0f), forward)

        if (recenterRequested || anchor == null) {
            recenterRequested = false
            // Pointing down at a table: put it where the view meets a flat surface [TABLE_DROP]
            // below the phone, so it rests on the table instead of floating. Otherwise (looking
            // ahead) float it straight in front, a little below the centre line.
            val hit = if (forward.y < -0.3f) TABLE_DROP / -forward.y else Float.NaN
            if (!hit.isNaN() && hit in 0.2f..1.6f) {
                anchor = Vec3(forward.x * hit, -TABLE_DROP, forward.z * hit)
                onTable = true
            } else {
                anchor = Vec3(forward.x * distance, forward.y * distance - 0.05f, forward.z * distance)
                onTable = false
            }
            // Turn its front (+z) towards the student.
            yawDeg = Math.toDegrees(atan2(-forward.x.toDouble(), -forward.z.toDouble())).toFloat()
        }
        anchor?.let { roomLight = Vec3(it.x, it.y + 1.2f, it.z + 0.4f) }
    }

    override fun draw() {
        if (markerSeen) {
            // Standing on the marker: no stand, a contact shadow on the sheet.
            drawSpecimen(markerSmooth, pedestal = false)
            return
        }
        if (!hasRotation) return
        val a = anchor ?: return
        Matrix.setIdentityM(base, 0)
        Matrix.translateM(base, 0, a.x, a.y, a.z)
        // On the table it stands on the real surface; in the air it gets a small stand.
        drawSpecimen(base, pedestal = !onTable)
    }

    /** 3x3 row-major product a * b. */
    private fun mul3(a: FloatArray, b: FloatArray) = FloatArray(9) { i ->
        val row = i / 3; val col = i % 3
        a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
    }
}
