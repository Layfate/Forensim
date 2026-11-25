package com.forensim.app.feature.simulation

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.selected
import com.forensim.app.core.designsystem.Motion
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.feature.settings.SettingSwitch
import androidx.activity.compose.LocalActivity
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forensim.app.core.data.AppSettings
import com.forensim.app.core.data.ControlSettings
import com.forensim.app.core.data.GraphicsQuality
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.CoachTone
import com.forensim.app.engine.GameAction
import com.forensim.app.engine.HudState
import com.forensim.app.engine.SimMode
import com.forensim.app.R
import com.forensim.app.engine.SimulationGame
import com.forensim.app.engine.controls.GyroController
import com.forensim.app.engine.interaction.Tool
import com.forensim.app.engine.renderer.GameRenderer
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private fun toolIcon(t: Tool): ImageVector = when (t) {
    Tool.MARKER -> Icons.Outlined.Flag
    Tool.CAMERA -> Icons.Outlined.CameraAlt
    Tool.SCALE -> Icons.Outlined.Straighten
    Tool.HAND -> Icons.Outlined.FrontHand
    Tool.PRINT_KIT -> Icons.Outlined.Fingerprint
    Tool.SWAB_KIT -> Icons.Outlined.Vaccines
    Tool.FORCEPS -> Icons.Outlined.ContentCut
    Tool.CASTING_KIT -> Icons.Outlined.Layers
    Tool.PAPER_BAG, Tool.PLASTIC_BAG -> Icons.Outlined.Inventory2
    Tool.EVIDENCE_BOX -> Icons.Outlined.AllInbox
}

@Composable
fun GameScreen(game: SimulationGame, onFinish: () -> Unit, onTrainingFinish: (() -> Unit)? = null, onExit: (() -> Unit)? = null) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val controls = settings.controls
    val hud by game.hud.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<String?>(null) } // "pause" | "inventory"
    val scope = rememberCoroutineScope()
    val anchors = remember { GuideAnchors() }
    val guidedTool = (hud.guide as? com.forensim.app.engine.GuideTarget.ToolChip)?.tool

    ImmersiveLandscape()
    BackHandler { panel = if (panel == null) "pause" else null }

    // Sound effect + vibration for each coaching message (Settings > Sound).
    val appContext = LocalContext.current.applicationContext
    val sfx = remember { com.forensim.app.engine.audio.SoundFx(appContext) }
    DisposableEffect(sfx) { onDispose { sfx.release() } }
    val sound = settings.sound
    LaunchedEffect(hud.coach?.id) {
        val msg = hud.coach ?: return@LaunchedEffect
        val kind = when (msg.tone) {
            CoachTone.SUCCESS -> com.forensim.app.engine.audio.Sfx.SUCCESS
            CoachTone.WARNING -> com.forensim.app.engine.audio.Sfx.WARNING
            CoachTone.INFO -> com.forensim.app.engine.audio.Sfx.INFO
        }
        if (sound.effects) sfx.play(kind, sound.volume)
        if (sound.vibration) sfx.vibrate(kind)
    }

    // Optional gyro look (off by default, disabled when unsupported).
    val context = LocalContext.current
    val gyro = remember { GyroController(context) { yaw, pitch -> game.input.addLook(yaw, pitch) } }
    DisposableEffect(controls.gyroEnabled, panel) {
        gyro.gain = controls.gyroGain
        if (controls.gyroEnabled && gyro.isSupported && panel == null) gyro.start() else gyro.stop()
        onDispose { gyro.stop() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GameSurface(game, controls.graphics, controls.fieldOfView)

        // Touch layers: movement on one half, look on the other (swapped for left-handed play).
        Row(Modifier.fillMaxSize()) {
            val move: @Composable (Modifier) -> Unit = { m -> MoveZone(game, m) }
            val look: @Composable (Modifier) -> Unit = { m -> LookZone(game, controls, m) }
            if (controls.leftHanded) { look(Modifier.weight(1f)); move(Modifier.weight(1f)) } else { move(Modifier.weight(1f)); look(Modifier.weight(1f)) }
        }

        Reticle(hud.focus != null, Modifier.align(Alignment.Center))
        hud.focus?.let { f ->
            Surface(color = ForensimTheme.colors.hudScrim, contentColor = ForensimTheme.colors.onHud, shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.Center).padding(top = 72.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(f.detail, style = MaterialTheme.typography.labelSmall, color = ForensimTheme.colors.onHudMuted)
                }
            }
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.sm)) {
            StatusPanel(game.mode, hud, Modifier.align(Alignment.TopStart))
            Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (controls.gyroEnabled && gyro.isSupported) {
                    HudIconButton(Icons.Outlined.CenterFocusStrong, "Recenter view") { gyro.resetReference(); game.post(GameAction.Recenter) }
                }
                // Every item collected and packaged: offer the report right beside the bag.
                if (hud.allPackaged && !hud.reportSubmitted && game.trainingPart?.guided != true) {
                    SubmitReportButton(onFinish)
                }
                HudIconButton(Icons.Outlined.Backpack, "Inventory, ${hud.inventory.size} items", badge = hud.awaitingPackaging.size,
                    modifier = Modifier.guideAnchor(anchors, "inventory")) { panel = "inventory" }
                HudIconButton(Icons.Outlined.Pause, "Pause investigation", modifier = Modifier.guideAnchor(anchors, "pause")) { panel = "pause" }
            }
            CoachToast(hud, Modifier.align(Alignment.TopCenter).padding(top = 52.dp))

            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                val actions: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.widthIn(max = 176.dp),
                        horizontalAlignment = if (controls.leftHanded) Alignment.Start else Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        GloveButton(hud.glovesOn, Modifier.guideAnchor(anchors, "gloves")) { game.post(GameAction.ToggleGloves) }
                        InteractButton(hud.interactLabel, Modifier.guideAnchor(anchors, "interact")) { game.post(GameAction.Interact) }
                    }
                }
                // The bottom corner on the movement side is where the thumb lands for the joystick:
                // keep it clear so the toolbar sits in the middle and never takes those touches.
                val joystickGap: @Composable () -> Unit = { Spacer(Modifier.width(JOYSTICK_CLEARANCE)) }
                if (controls.leftHanded) actions() else joystickGap()
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    ToolBar(hud.selectedTool, guidedTool, anchors, Modifier) { game.post(GameAction.SelectTool(it)) }
                }
                if (controls.leftHanded) joystickGap() else actions()
            }
        }

        // Training: once this part's goal is met, a clear button ends the part (no report needed).
        if (onTrainingFinish != null && hud.trainingComplete && game.trainingPart?.guided == true) {
            com.forensim.app.core.designsystem.PrimaryButton(
                "Finish part ${game.trainingPart?.number ?: ""}",
                onClick = onTrainingFinish,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = Spacing.sm).widthIn(min = 200.dp)
                    .guideAnchor(anchors, "finish"),
            )
        }


        if (panel != null) {
            Box(Modifier.fillMaxSize().background(Color(0xCC05080F)).clickable(onClick = { panel = null }, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center).safeDrawingPadding().padding(Spacing.md).widthIn(max = 640.dp).fillMaxHeight(0.92f),
            ) {
                when (panel) {
                    "inventory" -> InventoryPanel(game, hud, anchors) { panel = null }
                    else -> PauseMenu(game, hud, controls, anchors,
                        onControls = { c -> scope.launch { container.settings.updateControls(c) } },
                        gyroSupported = gyro.isSupported,
                        onResume = { panel = null },
                        onFinish = onFinish,
                        onTrainingFinish = onTrainingFinish,
                        onExit = onExit)
                }
            }
        }

        // Training guide arrow (Parts 1-2): points at the next thing to do, in the scene or inside
        // the open menu / inventory.
        hud.guide?.let { GuideOverlay(game, it, anchors, controls.fieldOfView, panel) }
    }
}

@Composable
private fun ImmersiveLandscape() {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    DisposableEffect(activity, view) {
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNav = controller.isAppearanceLightNavigationBars
        controller.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            activity.requestedOrientation = previous
            controller.show(WindowInsetsCompat.Type.systemBars())
            // Rotation can reset bar appearance; restore the theme's icon colours.
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNav
            view.keepScreenOn = false
        }
    }
}

@Composable
private fun GameSurface(game: SimulationGame, quality: GraphicsQuality, fieldOfView: Float) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val assets = LocalContext.current.assets
    val renderer = remember(game) { GameRenderer(game, assets) }
    var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
    val renderScale by rememberUpdatedState(quality.renderScale)
    SideEffect { renderer.horizontalFovDegrees = fieldOfView }

    // Graphics preset: render at a fraction of native resolution on low-end phones. The buffer must
    // follow the view's *current* size; sizing it once (before the switch to landscape) left a
    // portrait-shaped image stretched across a landscape screen.
    fun GLSurfaceView.fitBuffer() {
        if (width <= 0 || height <= 0) return
        val w = (width * renderScale).toInt().coerceAtLeast(1)
        val h = (height * renderScale).toInt().coerceAtLeast(1)
        if (getTag(R.id.gl_buffer_size) != w to h) {
            setTag(R.id.gl_buffer_size, w to h)
            holder.setFixedSize(w, h)
        }
    }
    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setEGLConfigChooser(com.forensim.app.engine.renderer.HdConfigChooser())
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                contentDescription = "3D crime scene view"
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> (v as GLSurfaceView).fitBuffer() }
                surface = this
            }
        },
        update = { v -> v.post { v.fitBuffer() } },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(lifecycle, surface) {
        val s = surface
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> s?.onPause()
                Lifecycle.Event.ON_RESUME -> s?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            s?.onPause()
        }
    }
}

/** Floating joystick: the stick origin is wherever the thumb lands. */
@Composable
private fun MoveZone(game: SimulationGame, modifier: Modifier) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }
    Box(modifier.fillMaxHeight()
        .semantics { contentDescription = "Movement area. Drag to walk." }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                origin = down.position
                knob = Offset.Zero
                val id = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == id } ?: break
                    if (!change.pressed) break
                    val d = change.position - down.position
                    val len = sqrt(d.x * d.x + d.y * d.y)
                    val clamped = if (len > radiusPx) d * (radiusPx / len) else d
                    knob = clamped
                    game.input.moveX = clamped.x / radiusPx
                    game.input.moveY = -clamped.y / radiusPx
                    change.consume()
                }
                origin = null
                game.input.moveX = 0f
                game.input.moveY = 0f
            }
        }) {
        val o = origin
        if (o != null) {
            val base = Color.White.copy(alpha = 0.18f)
            val stick = Color.White.copy(alpha = 0.55f)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(base, radius = radiusPx, center = o)
                drawCircle(Color.White.copy(alpha = 0.35f), radius = radiusPx, center = o, style = Stroke(2.dp.toPx()))
                drawCircle(stick, radius = radiusPx * 0.42f, center = o + knob)
            }
        } else {
            Text("Drag to move", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        }
    }
}

@Composable
private fun LookZone(game: SimulationGame, controls: ControlSettings, modifier: Modifier) {
    Box(modifier.fillMaxHeight()
        .semantics { contentDescription = "Look area. Drag to turn the camera." }
        .pointerInput(controls.lookSensitivity, controls.invertY) {
            val k = 0.0042f * controls.lookSensitivity
            detectDragGestures { change, drag ->
                change.consume()
                game.input.addLook(drag.x * k, (if (controls.invertY) drag.y else -drag.y) * k)
            }
        })
}

@Composable
private fun Reticle(active: Boolean, modifier: Modifier) {
    val color = if (active) ForensimTheme.colors.hudAttention else Color.White.copy(alpha = 0.8f)
    Canvas(modifier.size(28.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = size.minDimension / 2.6f, center = c, style = Stroke(4.dp.toPx()))
        drawCircle(color, radius = size.minDimension / 2.6f, center = c, style = Stroke(2.dp.toPx()))
        drawCircle(color, radius = 2.dp.toPx(), center = c)
    }
}

@Composable
private fun StatusPanel(mode: SimMode, hud: HudState, modifier: Modifier) {
    Surface(color = ForensimTheme.colors.hudScrim, contentColor = ForensimTheme.colors.onHud, shape = RoundedCornerShape(10.dp), modifier = modifier.widthIn(max = 280.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(when (mode) { SimMode.TUTORIAL -> "Training"; SimMode.PRACTICE -> "Practice"; SimMode.ASSESSMENT -> "Graded assessment" },
                style = MaterialTheme.typography.labelMedium, color = if (mode == SimMode.ASSESSMENT) ForensimTheme.colors.hudAttention else ForensimTheme.colors.onHud)
            Text(
                buildString {
                    append(if (hud.logSigned) "Log signed" else "Log not signed")
                    append(" · ")
                    append(if (hud.glovesOn) "Gloves on" else "No gloves")
                    append(" · Facing ${hud.heading} (door ${hud.doorWall})")
                },
                style = MaterialTheme.typography.labelSmall, color = ForensimTheme.colors.onHudMuted,
            )
            Text(
                hud.total?.let { "Found ${hud.found}/$it · Packaged ${hud.packaged}/$it" } ?: "Found ${hud.found} · Packaged ${hud.packaged}",
                style = MaterialTheme.typography.labelSmall, color = ForensimTheme.colors.onHudMuted,
            )
            hud.tutorialStep?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
private fun CoachToast(hud: HudState, modifier: Modifier) {
    val msg = hud.coach
    var visible by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(msg?.id) {
        if (msg != null) { visible = true; delay(if (msg.tone == CoachTone.WARNING) 5000 else 2800); visible = false }
    }
    AnimatedVisibility(visible && msg != null, modifier = modifier,
        enter = if (reduced) androidx.compose.animation.EnterTransition.None else fadeIn(androidx.compose.animation.core.tween(Motion.ENTER_MS)),
        exit = if (reduced) androidx.compose.animation.ExitTransition.None else fadeOut(androidx.compose.animation.core.tween(Motion.EXIT_MS))) {
        val m = msg ?: return@AnimatedVisibility
        val (bg, fg) = when (m.tone) {
            CoachTone.WARNING -> ForensimTheme.colors.attentionContainer to ForensimTheme.colors.onAttentionContainer
            CoachTone.SUCCESS -> ForensimTheme.colors.successContainer to ForensimTheme.colors.onSuccessContainer
            CoachTone.INFO -> ForensimTheme.colors.hudScrim to ForensimTheme.colors.onHud
        }
        Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(10.dp), modifier = Modifier.widthIn(max = 420.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun HudIconButton(icon: ImageVector, description: String, badge: Int = 0, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier) {
        Surface(onClick = onClick, color = ForensimTheme.colors.hudScrim, contentColor = ForensimTheme.colors.onHud, shape = CircleShape,
            modifier = Modifier.size(Spacing.touchTarget).semantics { contentDescription = description }) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        if (badge > 0) {
            Surface(color = ForensimTheme.colors.hudAttention, contentColor = Color.Black, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).size(18.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("$badge", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/** Shown beside the bag once every item is collected and packaged: straight to the case report. */
@Composable
private fun SubmitReportButton(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp),
        color = ForensimTheme.colors.successContainer, contentColor = ForensimTheme.colors.onSuccessContainer,
        border = BorderStroke(1.dp, ForensimTheme.colors.success),
        modifier = Modifier.heightIn(min = Spacing.touchTarget).semantics { contentDescription = "All evidence packaged. Submit report" }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Submit report", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun GloveButton(on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp),
        color = if (on) ForensimTheme.colors.successContainer else ForensimTheme.colors.hudScrim,
        contentColor = if (on) ForensimTheme.colors.onSuccessContainer else ForensimTheme.colors.onHud,
        border = BorderStroke(1.dp, if (on) ForensimTheme.colors.success else Color.White.copy(alpha = 0.3f)),
        modifier = modifier.heightIn(min = Spacing.touchTarget).semantics { contentDescription = "Gloves"; stateDescription = if (on) "On" else "Off" }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (on) "Gloves on" else "Put on gloves", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun InteractButton(label: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val enabled = label != null
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(36.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else ForensimTheme.colors.hudScrim,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else ForensimTheme.colors.onHudMuted,
        modifier = modifier.heightIn(min = 56.dp).widthIn(min = 120.dp, max = 176.dp)) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label ?: "Aim at an object", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Width kept free beside the toolbar on the joystick side (the stick's 56 dp radius plus a thumb's margin). */
private val JOYSTICK_CLEARANCE = 150.dp

@Composable
private fun ToolBar(selected: Tool, guided: Tool?, anchors: GuideAnchors, modifier: Modifier, onSelect: (Tool) -> Unit) {
    Surface(color = ForensimTheme.colors.hudScrim, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Row(Modifier.selectableGroup().horizontalScroll(rememberScrollState()).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            Tool.sceneTools.forEach { t ->
                val sel = t == selected
                // The toolbar scrolls; bring the tool the guide points at into view.
                val reveal = remember { BringIntoViewRequester() }
                LaunchedEffect(guided == t) { if (guided == t) reveal.bringIntoView() }
                Surface(onClick = { onSelect(t) }, shape = RoundedCornerShape(11.dp),
                    color = if (sel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else ForensimTheme.colors.onHud,
                    modifier = Modifier.heightIn(min = Spacing.touchTarget).semantics { this.selected = sel }
                        .bringIntoViewRequester(reveal).guideAnchor(anchors, toolAnchorKey(t))) {
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp).widthIn(min = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(toolIcon(t), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(t.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryPanel(game: SimulationGame, hud: HudState, anchors: GuideAnchors, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Evidence & chain of custody", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            com.forensim.app.core.designsystem.IconButton(onClick = onClose, modifier = Modifier.guideAnchor(anchors, "inv:close")) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (hud.inventory.isEmpty()) {
                Text("No evidence collected yet. Document an item, then collect it with a suitable technique.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hud.inventory.forEach { e ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text((e.itemNumber?.let { "Item $it · " } ?: "Unmarked · ") + e.description, style = MaterialTheme.typography.titleSmall)
                        Text("Location: ${e.location}", style = MaterialTheme.typography.bodySmall)
                        Text("Collected by ${e.collectedBy} · ${e.collectionMethod.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        if (e.contaminated) Text("Possible contamination: collected without gloves", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (e.packaging != null) {
                            Text("Sealed in ${e.packaging.lowercase()}", style = MaterialTheme.typography.bodySmall, color = ForensimTheme.colors.success)
                        } else if (e.evidenceId in hud.awaitingPackaging) {
                            Spacer(Modifier.height(Spacing.sm))
                            Text("Package and seal:", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                val guide = hud.guide as? com.forensim.app.engine.GuideTarget.Package
                                Tool.packaging.forEach { p ->
                                    if (guide?.evidenceId == e.evidenceId && guide.tool == p) {
                                        // Training: the right container is filled in, so it stands out.
                                        val reveal = remember { BringIntoViewRequester() }
                                        LaunchedEffect(Unit) { reveal.bringIntoView() }
                                        com.forensim.app.core.designsystem.Button(onClick = { game.post(GameAction.Package(e.evidenceId, p)) },
                                            modifier = Modifier.heightIn(min = Spacing.touchTarget).bringIntoViewRequester(reveal)
                                                .guideAnchor(anchors, packageAnchorKey(e.evidenceId, p))) { Text("${p.label} ✓") }
                                    } else {
                                        OutlinedButton(onClick = { game.post(GameAction.Package(e.evidenceId, p)) }, modifier = Modifier.heightIn(min = Spacing.touchTarget)) {
                                            Text(p.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseMenu(
    game: SimulationGame, hud: HudState, controls: ControlSettings, anchors: GuideAnchors, gyroSupported: Boolean,
    onControls: (ControlSettings) -> Unit, onResume: () -> Unit, onFinish: () -> Unit, onTrainingFinish: (() -> Unit)?,
    onExit: (() -> Unit)?,
) {
    var confirmExit by remember { mutableStateOf(false) }
    // Control settings open as their own page inside the menu, behind a Settings button.
    var showSettings by remember { mutableStateOf(false) }
    // Training Parts 1-2 end with "Finish part", not the case report.
    val guidedPart = game.trainingPart?.takeIf { it.guided }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        if (showSettings) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.forensim.app.core.designsystem.IconButton(onClick = { showSettings = false }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to the menu")
                }
                Text("Control settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.sm))
            LabeledSlider("Look sensitivity", controls.lookSensitivity, 0.3f..2.5f) { onControls(controls.copy(lookSensitivity = it)) }
            SettingSwitch("Gyroscope look", if (gyroSupported) null else "This device uses touch controls.", controls.gyroEnabled && gyroSupported, enabled = gyroSupported) {
                onControls(controls.copy(gyroEnabled = it))
            }
            if (controls.gyroEnabled && gyroSupported) {
                LabeledSlider("Gyroscope gain", controls.gyroGain, 0.3f..2.5f) { onControls(controls.copy(gyroGain = it)) }
            }
            SettingSwitch("Invert vertical look", null, controls.invertY) { onControls(controls.copy(invertY = it)) }
            SettingSwitch("Left-handed layout", null, controls.leftHanded) { onControls(controls.copy(leftHanded = it)) }
            Text("Graphics", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GraphicsQuality.entries.forEach { q ->
                    androidx.compose.material3.FilterChip(selected = controls.graphics == q, onClick = { onControls(controls.copy(graphics = q)) },
                        label = { Text(q.label) }, modifier = Modifier.heightIn(min = Spacing.touchTarget))
                }
            }
            Spacer(Modifier.height(Spacing.md))
            com.forensim.app.core.designsystem.PrimaryButton("Done", onClick = { showSettings = false }, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        Text("Investigation paused", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.md))

        // The main actions first: Resume, then finish (report or training part), then Exit.
        com.forensim.app.core.designsystem.PrimaryButton("Resume", onClick = onResume,
            modifier = Modifier.fillMaxWidth().guideAnchor(anchors, "menu:resume"))
        Spacer(Modifier.height(Spacing.sm))
        if (guidedPart != null) {
            if (hud.trainingComplete && onTrainingFinish != null) {
                com.forensim.app.core.designsystem.PrimaryButton("Finish part ${guidedPart.number}", onClick = onTrainingFinish,
                    modifier = Modifier.fillMaxWidth().guideAnchor(anchors, "menu:finish"))
                Spacer(Modifier.height(Spacing.sm))
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) { Text("Stop this part") }
        } else {
            val reveal = remember { BringIntoViewRequester() }
            LaunchedEffect(hud.guide) { if (hud.guide == com.forensim.app.engine.GuideTarget.Report) reveal.bringIntoView() }
            // Solid green: finishing with the report is the goal of the case, so it stands out from Exit.
            com.forensim.app.core.designsystem.Button(onClick = onFinish,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ForensimTheme.colors.success, contentColor = com.forensim.app.core.designsystem.Palette.Ink),
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget).bringIntoViewRequester(reveal).guideAnchor(anchors, "menu:report")) {
                Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Finish and write case report")
            }
        }
        // Leave without a report and go back to where the case was opened from.
        if (onExit != null) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = { confirmExit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Exit")
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            when {
                guidedPart != null -> "This part ends with the Finish part button once its goal is met. Stopping now means doing the part again."
                hud.awaitingPackaging.isNotEmpty() -> "${hud.awaitingPackaging.size} collected item(s) are not packaged yet."
                else -> "When you have processed the scene, finish to write your case report."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Training: what to do next, so the menu never leaves the student stuck.
        if (hud.guide != null) hud.tutorialStep?.let { step ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(guidedPart?.let { "Training · Part ${it.number}: next step" }
                        ?: if (game.mode == SimMode.PRACTICE) "Practice: next step" else "Tutorial: next step",
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.md))
        SectionHeader("Case objectives")
        game.layout.template.objectives.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(Spacing.md))
        OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text("Settings")
        }
    }
    if (confirmExit && onExit != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(if (game.trainingPart != null) "Leave this training part?" else "Leave this case?") },
            text = {
                Text(if (game.trainingPart != null) "Your progress here won't be saved. You can start again later."
                    else "Your work so far is saved. Resume this case any time from Simulate.")
            },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; onExit() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Exit") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column {
        Row { Text(label, Modifier.weight(1f)); Text("%.1fx".format(local), style = MaterialTheme.typography.labelMedium) }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range)
    }
}

