package com.forensim.app.feature.ar

import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.Objdetect
import kotlin.math.abs

/**
 * One sighting of the marker: where it is relative to the camera, in the camera sensor's own
 * orientation (OpenCV: x right, y down, z forward, metres), plus what's needed to draw it.
 */
class MarkerDetection(
    /** Marker-to-camera rotation, 3x3 row-major. */
    val rotation: FloatArray,
    /** Marker centre in camera space, metres. */
    val translation: FloatArray,
    val imageWidth: Int,
    val imageHeight: Int,
    /** Clockwise degrees to turn the camera image upright on screen. */
    val rotationDegrees: Int,
    val fx: Float, val fy: Float, val cx: Float, val cy: Float,
    val timeNanos: Long,
)

/** Camera lens numbers needed to measure the marker, read from the phone's camera information. */
class LensInfo(
    private val focalMm: Float,
    private val sensorWidthMm: Float,
    private val pixelArrayWidth: Int,
    private val activeWidth: Int,
    private val activeHeight: Int,
    /** fx, fy, cx, cy in active-array pixels when the phone publishes them; otherwise null. */
    private val calibration: FloatArray?,
) {
    /** fx, fy, cx, cy for an analysis image of [w] x [h] covering the active area. */
    fun forImage(w: Int, h: Int): FloatArray {
        val scale = w.toFloat() / activeWidth
        // A 4:3 image of a slightly different-shaped sensor is centre-cropped vertically.
        val yOffset = (activeHeight - h / scale) / 2f
        val c = calibration
        if (c != null && c[0] > 0f && c[1] > 0f) {
            return floatArrayOf(c[0] * scale, c[1] * scale, c[2] * scale, (c[3] - yOffset) * scale)
        }
        val fxPixelArray = focalMm / sensorWidthMm * pixelArrayWidth
        val f = fxPixelArray * activeWidth / pixelArrayWidth * scale
        return floatArrayOf(f, f, w / 2f, h / 2f)
    }

    companion object {
        fun from(get: (CameraCharacteristics.Key<*>) -> Any?): LensInfo? {
            val focal = (get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) as? FloatArray)?.firstOrNull() ?: return null
            val physical = get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) as? android.util.SizeF ?: return null
            val pixels = get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) as? android.util.Size ?: return null
            val active = get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) as? android.graphics.Rect ?: return null
            val calibration = runCatching { get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) as? FloatArray }.getOrNull()
            return LensInfo(focal, physical.width, pixels.width, active.width(), active.height(), calibration)
        }
    }
}

/**
 * Finds the printed ForenSim marker (ArUco, 4x4 dictionary) in each camera frame and measures the
 * phone's position and angle relative to it (OpenCV solvePnP). With that, a specimen can sit on
 * the marker and stay put while the student walks around it, on phones without ARCore.
 * Runs on CameraX's analysis thread; [latest] is read by the renderer.
 */
class MarkerTracker(private val lens: LensInfo) : ImageAnalysis.Analyzer {
    @Volatile var latest: MarkerDetection? = null; private set

    private val detector = ArucoDetector(Objdetect.getPredefinedDictionary(Objdetect.DICT_4X4_50))
    private val half = MARKER_SIZE_M / 2
    // Corner order matches ArUco's: top-left, top-right, bottom-right, bottom-left (marker y up).
    private val objectPoints = MatOfPoint3f(
        Point3(-half, half, 0.0), Point3(half, half, 0.0), Point3(half, -half, 0.0), Point3(-half, -half, 0.0),
    )
    private val noDistortion = MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0)
    private var loggedError = false
    private var loggedPose = false

    override fun analyze(image: ImageProxy) {
        try {
            detect(image)
        } catch (e: Exception) {
            if (!loggedError) { loggedError = true; Log.w("ForensimAR", "Marker detection failed: ${e.message}") }
        } finally {
            image.close()
        }
    }

    private fun detect(image: ImageProxy) {
        val plane = image.planes[0]
        val w = image.width
        val h = image.height
        // The Y (brightness) plane is already a greyscale picture: wrap it, no copy.
        val gray = Mat(h, w, CvType.CV_8UC1, plane.buffer, plane.rowStride.toLong())
        val corners = ArrayList<Mat>()
        val ids = Mat()
        try {
            detector.detectMarkers(gray, corners, ids)
            if (corners.isEmpty()) return
            // Only the ForenSim marker (id 0), and only when it's big enough to be the real sheet:
            // tiny look-alikes in a busy scene (keyboard keys, fabric patterns) are ignored.
            val idList = IntArray(ids.rows()).also { if (it.isNotEmpty()) ids.get(0, 0, it) }
            val minArea = (MIN_SIDE_PX * MIN_SIDE_PX).toDouble()
            val best = corners.indices
                .filter { i -> idList.getOrNull(i) == MARKER_ID && area(corners[i]) >= minArea }
                .maxByOrNull { i -> area(corners[i]) }
                ?.let { corners[it] } ?: return
            val k = lens.forImage(w, h)
            val camera = Mat(3, 3, CvType.CV_64F).apply {
                put(0, 0, k[0].toDouble(), 0.0, k[2].toDouble(), 0.0, k[1].toDouble(), k[3].toDouble(), 0.0, 0.0, 1.0)
            }
            // The four corners as plain points (wrapping the detector's Mat directly is rejected
            // by solvePnP on some builds: it reads the point count wrongly).
            val p = FloatArray(8).also { best.get(0, 0, it) }
            val points = MatOfPoint2f(
                Point(p[0].toDouble(), p[1].toDouble()), Point(p[2].toDouble(), p[3].toDouble()),
                Point(p[4].toDouble(), p[5].toDouble()), Point(p[6].toDouble(), p[7].toDouble()),
            )
            val rvec = Mat()
            val tvec = Mat()
            val rot = Mat()
            try {
                if (!Calib3d.solvePnP(objectPoints, points, camera, noDistortion, rvec, tvec, false, Calib3d.SOLVEPNP_IPPE_SQUARE)) return
                Calib3d.Rodrigues(rvec, rot)
                val r = DoubleArray(9).also { rot.get(0, 0, it) }
                val t = DoubleArray(3).also { tvec.get(0, 0, it) }
                if (t[2] <= 0.0) return // behind the camera: a bad solution
                if (!loggedPose) {
                    loggedPose = true
                    Log.i("ForensimAR", "Marker found ${w}x$h, fx=${"%.0f".format(k[0])}, distance=${"%.2f".format(t[2])} m")
                }
                latest = MarkerDetection(
                    FloatArray(9) { r[it].toFloat() }, FloatArray(3) { t[it].toFloat() },
                    w, h, image.imageInfo.rotationDegrees, k[0], k[1], k[2], k[3], System.nanoTime(),
                )
            } finally {
                camera.release(); points.release(); rvec.release(); tvec.release(); rot.release()
            }
        } finally {
            gray.release(); ids.release(); corners.forEach { it.release() }
        }
    }

    private fun area(c: Mat): Double {
        val p = FloatArray(8).also { c.get(0, 0, it) }
        var a = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            a += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return abs(a) / 2
    }

    companion object {
        /** Side of the black marker square on the printed sheet. */
        const val MARKER_SIZE_M = 0.10
        const val MARKER_ID = 0
        /** Smallest marker side, in analysis pixels (640 wide), that counts: about 2 m away for a 10 cm sheet. */
        const val MIN_SIDE_PX = 24

        /** Loads OpenCV's native code once; false where it isn't packaged (x86 emulators). */
        val available: Boolean by lazy {
            runCatching { OpenCVLoader.initLocal() }.getOrDefault(false).also {
                if (!it) Log.w("ForensimAR", "OpenCV not available: marker mode off")
            }
        }
    }
}
