package com.forensim.app.feature.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.OutlinedButton
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.scene.EvidenceType
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.delay

/** Whether this phone can run ARCore, as far as is known right now. */
enum class ArSupport { CHECKING, SUPPORTED, NEEDS_INSTALL, UNSUPPORTED }

/** Once ARCore has said "installed and ready", keep that for this run of the app. */
@Volatile private var arReady = false

private const val AR_PREFS = "forensim_ar"
private const val KEY_AR_UNUSABLE = "arcore_unusable"

/**
 * Remember on this phone that full AR can't be used: ARCore refused to start here (e.g. "device
 * not compatible": no calibration for this model, though its quick check says "supported"), or
 * the student chose camera view. From then on the camera opens straight in camera view instead
 * of showing the "install Google Play Services for AR" page every time.
 */
fun rememberArUnusable(context: Context) {
    context.getSharedPreferences(AR_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AR_UNUSABLE, true).apply()
}

private fun arUnusable(context: Context) =
    context.getSharedPreferences(AR_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AR_UNUSABLE, false)

fun arSupport(context: Context): ArSupport {
    if (arReady) return ArSupport.SUPPORTED
    if (arUnusable(context)) return ArSupport.UNSUPPORTED
    return runCatching {
        val a = ArCoreApk.getInstance().checkAvailability(context)
        when {
            a.isTransient -> ArSupport.CHECKING
            a == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.SUPPORTED.also { arReady = true }
            a.isSupported -> ArSupport.NEEDS_INSTALL // not installed, or too old
            else -> ArSupport.UNSUPPORTED
        }
    }.getOrDefault(ArSupport.UNSUPPORTED)
}

/** ARCore's support check can take a moment the first time; this re-checks until it knows. */
@Composable
fun rememberArSupport(): ArSupport {
    val context = LocalContext.current
    var support by remember { mutableStateOf(arSupport(context)) }
    LaunchedEffect(support) {
        while (support == ArSupport.CHECKING) { delay(200); support = arSupport(context) }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { support = arSupport(context); onPauseOrDispose { } }
    return support
}

@Suppress("DEPRECATION")
internal fun displayRotation(context: Context): Int =
    (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * The specimen through the phone camera, in the best mode the phone supports:
 *  - real AR (ARCore): the student taps a table or the floor to place it life-size and walks round it;
 *  - camera view (no ARCore, but a camera and rotation sensor): it floats in front of them over the
 *    live camera and stays put as they turn the phone;
 *  - neither: back to the 3D viewer.
 * Before that: installing "Google Play Services for AR" if needed, then camera permission.
 */
@Composable
fun ArCameraScreen(typeKey: String, onBack: () -> Unit) {
    // The specimen on screen; swiping (or the arrows and category chips) changes it in place.
    var typeKeyNow by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(typeKey) }
    val type = EvidenceType.from(typeKeyNow) ?: EvidenceType.CARTRIDGE_CASE
    val select: (EvidenceType) -> Unit = { typeKeyNow = it.key }
    val context = LocalContext.current
    val support = rememberArSupport()
    val canCameraView = remember { detectCapabilities(context).let { it.camera && it.gyroscope } }
    // Set when the student picks camera view, or when ARCore turns out not to work here.
    var cameraView by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraAsked by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraGranted = ok; cameraAsked = true
    }
    var installError by remember { mutableStateOf<String?>(null) }
    var installRequested by remember { mutableStateOf(false) }

    // Coming back from the Play Store: finish ARCore's install handshake.
    androidx.lifecycle.compose.LifecycleResumeEffect(installRequested) {
        if (installRequested) {
            context.activity()?.let { act ->
                runCatching { ArCoreApk.getInstance().requestInstall(act, false) }
                    .onFailure { e -> if (e is UnavailableUserDeclinedInstallationException) installError = "AR was not installed." }
            }
            installRequested = false
        }
        onPauseOrDispose { }
    }

    val useCameraView = cameraView || (support == ArSupport.UNSUPPORTED && canCameraView)
    val title = if (useCameraView) "Evidence · camera view" else "Evidence in AR"
    val cameraViewOffer: Pair<String, () -> Unit>? = if (canCameraView) "Use camera view instead" to {
        rememberArUnusable(context); cameraView = true
    } else null

    ScreenScaffold(title = title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when {
                !useCameraView && support == ArSupport.CHECKING -> Message("Checking whether this phone supports AR…")
                !useCameraView && support == ArSupport.UNSUPPORTED -> Message(
                    "This phone has no camera or motion sensor for AR. You can still turn the ${type.displayName.lowercase()} " +
                        "around in the 3D viewer.", action = "Back to the 3D viewer" to onBack)
                !useCameraView && support == ArSupport.NEEDS_INSTALL -> Message(
                    installError ?: "Full AR needs the free \"Google Play Services for AR\" app. Install it, then come back.",
                    action = "Install" to {
                        context.activity()?.let { act ->
                            try {
                                val status = ArCoreApk.getInstance().requestInstall(act, true)
                                installRequested = status == ArCoreApk.InstallStatus.INSTALL_REQUESTED
                            } catch (e: UnavailableException) {
                                installError = "AR couldn't be installed on this phone."
                            }
                        }
                    },
                    secondary = cameraViewOffer)
                !cameraGranted -> Message(
                    if (cameraAsked) "This needs the camera to show your room. Allow camera access in the phone's Settings > Apps > Forensim > Permissions."
                    else "The evidence appears in your real room through the camera. Nothing is recorded or uploaded.",
                    action = "Allow camera" to { askCamera.launch(Manifest.permission.CAMERA) })
                useCameraView -> CameraView(type, select, fullArMissing = support == ArSupport.UNSUPPORTED)
                else -> ArView(type, select, onUnavailable = if (canCameraView) ({ cameraView = true }) else null)
            }
        }
    }
}

@Composable
private fun Message(text: String, action: Pair<String, () -> Unit>? = null, secondary: Pair<String, () -> Unit>? = null) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.lg), contentAlignment = Alignment.Center) {
        ForensimCard(Modifier.widthIn(max = 480.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            action?.let { (label, run) ->
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(label, onClick = run, modifier = Modifier.fillMaxWidth())
            }
            secondary?.let { (label, run) ->
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = run, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}

/** The running AR session: camera view, gestures and the hint/controls on top. */
@Composable
private fun ArView(type: EvidenceType, onSelect: (EvidenceType) -> Unit, onUnavailable: (() -> Unit)?) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember { mutableStateOf<String?>(null) }
    val started = remember {
        runCatching {
            Session(context).also { s ->
                s.configure(Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                })
            }
        }
    }
    val session = started.getOrNull()
    if (session == null) {
        // e.g. ARCore says this phone isn't certified after all: switch to camera view.
        val e = started.exceptionOrNull()
        // Not just "needs an update": this phone can't run ARCore. Don't ask again next time.
        if (e is com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException ||
            e is com.google.ar.core.exceptions.UnavailableSdkTooOldException) {
            LaunchedEffect(Unit) { rememberArUnusable(context) }
        }
        if (onUnavailable != null) { LaunchedEffect(Unit) { onUnavailable() }; return }
        Message("AR couldn't start: ${e?.message ?: e?.javaClass?.simpleName ?: "unknown error"}")
        return
    }

    val renderer = remember(session) {
        ArEvidenceRenderer(type, session, context.assets, displayRotation = { displayRotation(context) })
    }
    val status by renderer.status.collectAsState()
    val labelPoint by renderer.labelPoint.collectAsState()
    var fileOpen by remember { mutableStateOf<EvidenceType?>(null) }
    // The arrows, chips and swipe change it on the same spot; the camera keeps running.
    LaunchedEffect(type) { renderer.show(type) }
    val swipe = rememberSpecimenSwipe(renderer, type, onSelect)
    var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
    // Declared first so it is disposed last: stop drawing, pause ARCore, then close it.
    DisposableEffect(session) {
        onDispose {
            runCatching { surface?.onPause() }
            runCatching { session.pause() }
            session.close()
        }
    }
    var scaleLabel by remember { mutableStateOf("Life size") }
    LaunchedEffect(type) { scaleLabel = "Life size" }

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
            modifier = Modifier.fillMaxSize()
                .pointerInput(renderer) { detectTapGestures { p -> renderer.tap(p.x, p.y) } }
                .specimenGestures(
                    swipe = swipe,
                    onTransform = { zoom, rotation ->
                        renderer.yawDeg -= rotation
                        renderer.scale = (renderer.scale * zoom).coerceIn(0.5f, 4f)
                        scaleLabel = if (renderer.scale in 0.95f..1.05f) "Life size" else "%.1f×".format(renderer.scale)
                    },
                ),
        )

        error?.let { Message(it) }

        if (fileOpen == null) EvidenceCallout(labelPoint, type, onOpen = { fileOpen = type })

        SpecimenSwitcher(type, swipe, Modifier.align(Alignment.TopCenter).padding(top = Spacing.md))

        // Hint and controls at the bottom.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val hint = status.problem ?: when (status.phase) {
                ArPhase.STARTING -> "Starting the camera…"
                ArPhase.SEARCHING -> "Move your phone slowly over a table or the floor to find a surface."
                ArPhase.READY_TO_PLACE -> "Tap to place the ${type.displayName.lowercase()} where the ring is."
                ArPhase.PLACED -> "Walk around it. Swipe for the next specimen; pinch to enlarge, twist with two fingers to turn. Tap elsewhere to move it."
                ArPhase.TRACKING_LOST -> "Point back at the surface where you placed it."
            }
            Surface(shape = MaterialTheme.shapes.large, color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm))
            }
            if (status.phase == ArPhase.PLACED || status.phase == ArPhase.TRACKING_LOST) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = {
                        renderer.scale = 1f; renderer.yawDeg = 0f; scaleLabel = "Life size"
                    }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null); Spacer(Modifier.width(Spacing.xs)); Text(scaleLabel)
                    }
                    OutlinedButton(onClick = renderer::clear,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null); Spacer(Modifier.width(Spacing.xs)); Text("Remove")
                    }
                }
            }
        }
        EvidenceFile(fileOpen, onClose = { fileOpen = null })
    }

    // ARCore and the GL view follow the screen: camera on while visible, off when it isn't.
    DisposableEffect(lifecycle, surface, session) {
        fun start() {
            try {
                session.resume()
                surface?.onResume()
            } catch (e: CameraNotAvailableException) {
                error = "The camera is in use by another app. Close it and try again."
            }
        }
        fun stop() { runCatching { surface?.onPause() }; runCatching { session.pause() } }
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
