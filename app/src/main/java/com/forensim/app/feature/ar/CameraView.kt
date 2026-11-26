package com.forensim.app.feature.ar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.Print
import java.util.concurrent.Executors
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forensim.app.core.designsystem.OutlinedButton
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.scene.EvidenceType

/** Lens data of the bound camera, for measuring the marker (null if the phone doesn't publish it). */
@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
private fun lensInfo(camera: androidx.camera.core.Camera): LensInfo? = runCatching {
    val info = androidx.camera.camera2.interop.Camera2CameraInfo.from(camera.cameraInfo)
    @Suppress("UNCHECKED_CAST")
    LensInfo.from { key -> info.getCameraCharacteristic(key as android.hardware.camera2.CameraCharacteristics.Key<Any>) }
}.getOrNull()

/**
 * AR on phones without ARCore: the live back camera fills the screen and the specimen stands on
 * the printed ForenSim marker when it is in view (OpenCV: true size, stays put as the student
 * walks around). Without the marker it is held in place by the rotation sensor (turning only).
 */
@Composable
internal fun CameraView(type: EvidenceType, onSelect: (EvidenceType) -> Unit, fullArMissing: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle = lifecycleOwner.lifecycle
    // The camera image is drawn inside the 3D view (one opaque picture), not under it.
    val feed = remember { CameraFeed(context) }
    val renderer = remember { CameraViewRenderer(type, context.assets, feed) }
    // The arrows, chips and swipe change it in place; the camera keeps running.
    LaunchedEffect(type) { renderer.show(type) }
    var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var scaleLabel by remember { mutableStateOf("Life size") }
    LaunchedEffect(type) { scaleLabel = "Life size" }
    val swipe = rememberSpecimenSwipe(renderer, type, onSelect)

    val markerVisible by renderer.markerVisible.collectAsState()
    val labelPoint by renderer.labelPoint.collectAsState()
    // The evidence file opened from the "INSPECT …" tag (null = closed).
    var fileOpen by remember { mutableStateOf<EvidenceType?>(null) }
    val markerAvailable = remember { MarkerTracker.available }

    // The camera follows this screen's lifecycle (CameraX stops it when the screen closes).
    // Two streams of the same 4:3 picture: the preview on screen, and small frames for OpenCV to
    // find the marker in (same shape, so what OpenCV measures lines up with what is shown).
    DisposableEffect(feed) {
        var provider: ProcessCameraProvider? = null
        val fourByThree = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(fourByThree).build().also { it.setSurfaceProvider(feed.surfaceProvider) }
        val analysisThread = Executors.newSingleThreadExecutor()
        val analysis = if (!markerAvailable) null else ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                provider = future.get().also { p ->
                    p.unbindAll()
                    val useCases = listOfNotNull(preview, analysis).toTypedArray()
                    val camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
                    if (analysis != null) {
                        lensInfo(camera)?.let { lens ->
                            val tracker = MarkerTracker(lens)
                            renderer.tracker = tracker
                            analysis.setAnalyzer(analysisThread, tracker)
                        }
                    }
                }
            } catch (e: Exception) {
                cameraError = "The camera couldn't start. Close other apps using it and try again."
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { analysis?.clearAnalyzer() }
            runCatching { provider?.unbind(*listOfNotNull(preview, analysis).toTypedArray()) }
            analysisThread.shutdown()
            renderer.tracker = null
            feed.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    preserveEGLContextOnPause = true
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    surface = this
                }
            },
            modifier = Modifier.fillMaxSize().specimenGestures(
                swipe = swipe,
                onTransform = { zoom, rotation ->
                    renderer.yawDeg -= rotation
                    renderer.scale = (renderer.scale * zoom).coerceIn(0.5f, 4f)
                    scaleLabel = if (renderer.scale in 0.95f..1.05f) "Life size" else "%.1f×".format(renderer.scale)
                },
            ),
        )

        if (fileOpen == null) EvidenceCallout(labelPoint, type, onOpen = { fileOpen = type })

        val glassButton = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
        SpecimenSwitcher(type, swipe, Modifier.align(Alignment.TopCenter).padding(top = Spacing.md), below = {
            // Reset size and turn: a compact round button; shows the size while enlarged.
            OutlinedButton(onClick = { renderer.scale = 1f; renderer.yawDeg = 0f; scaleLabel = "Life size"; renderer.bringToFront() },
                colors = glassButton, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                modifier = Modifier.semantics { contentDescription = "Reset to life size" }) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                if (scaleLabel != "Life size") { Spacer(Modifier.width(Spacing.xs)); Text(scaleLabel) }
            }
        })

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val hint = cameraError ?: when {
                markerVisible -> "On the marker: walk around it. Keep the marker in view. Swipe for the next specimen."
                markerAvailable -> "Point the camera at the ForenSim marker (Print marker), or point down at a table and tap Place on table."
                else -> "Point the camera down at a table and tap Place on table. Swipe for the next specimen, pinch to enlarge."
            }
            Surface(shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            }
            val buttonColors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)
            // Two equal buttons side by side, a little taller than standard so they read as the
            // main actions of the screen.
            if (!markerVisible) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val big = Modifier.weight(1f).heightIn(min = 52.dp)
                val label = MaterialTheme.typography.titleSmall
                OutlinedButton(onClick = renderer::bringToFront, colors = buttonColors, modifier = big) {
                    Icon(Icons.Outlined.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.xs)); Text("Place on table", style = label, maxLines = 1)
                }
                if (markerAvailable) {
                    // Filled maroon (the app's primary colour): the main next step without a marker.
                    OutlinedButton(onClick = { runCatching { MarkerSheet.print(context) } }, modifier = big,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Spacing.xs)); Text("Print marker", style = label, maxLines = 1)
                    }
                }
            }
        }
        EvidenceFile(fileOpen, onClose = { fileOpen = null })
    }

    // Rotation sensor and the 3D layer run only while the screen is visible.
    DisposableEffect(lifecycle, surface) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val raw = FloatArray(9)
        val out = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(raw, event.values)
                // Phone axes as the screen shows them (landscape swaps x and y).
                val (ax, ay) = when (displayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(raw, ax, ay, out)
                renderer.onRotation(out)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun start() {
            sensor?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            surface?.onResume()
        }
        fun stop() {
            sm.unregisterListener(listener)
            surface?.onPause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
}
