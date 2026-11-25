package com.forensim.app.feature.ar

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Surface
import com.forensim.app.core.designsystem.IconButton
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.WorkspaceList
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined._3dRotation
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.engine.renderer.EvidenceViewerRenderer
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SpecimenCategory

data class DeviceCapabilities(val camera: Boolean, val gyroscope: Boolean, val gles3: Boolean)

fun detectCapabilities(context: Context): DeviceCapabilities {
    val pm = context.packageManager
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    return DeviceCapabilities(
        camera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null || sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null,
        gles3 = am.deviceConfigurationInfo.reqGlEsVersion >= 0x30000,
    )
}

/** Identifying features of the main specimens (3D viewer notes and the AR evidence file). */
internal val specimenFeatures = mapOf(
    EvidenceType.CARTRIDGE_CASE to listOf("Head stamp and primer: firing pin impression appears here", "Rim and extractor groove: extractor and ejector marks", "Body: handle by the edges to protect marks and prints"),
    EvidenceType.LATENT_PRINT to listOf("Ridge flow forms the overall pattern (loop, whorl, arch)", "Minutiae: ridge endings and bifurcations", "Non-porous glass: powder and lift, after photographing"),
    EvidenceType.BLOODSTAIN to listOf("Parent stain and satellite drops", "Biological: swab, air-dry, package in paper", "Photograph with scale before sampling"),
    EvidenceType.QUESTIONED_DOCUMENT to listOf("Line quality and pen pressure", "Look for alterations or added entries", "Handle by edges; never write on top of it"),
    EvidenceType.HAIR_FIBER to listOf("Class evidence: compare colour, form and structure", "Collect with clean forceps", "Fold into paper and seal"),
    EvidenceType.SHOE_PRINT to listOf("Sole pattern and wear: links a shoe to the scene", "Photograph with scale, lit from a low angle", "Gel-lift dust prints; cast prints in soil"),
    EvidenceType.TOOL_MARK to listOf("Pry marks show the width and shape of the tool", "Photograph before anything else", "Silicone cast preserves fine striations"),
    EvidenceType.BROKEN_GLASS to listOf("Fracture lines show the direction of force", "Collect with forceps, wearing gloves", "Rigid box so fragments don't break further"),
    EvidenceType.KNIFE to listOf("Blade may carry blood; handle may carry prints", "Lift by the handle edges only", "Rigid knife box; never a bag"),
    EvidenceType.FIREARM to listOf("Serial number, make and model identify it", "Made safe by a qualified officer first", "Handle by textured grips; box it"),
    EvidenceType.BULLET to listOf("Rifling marks link it to one firearm", "Rubber-tipped forceps protect the marks", "Small box or paper fold"),
    EvidenceType.BLUNT_WEAPON to listOf("May carry blood, hair and fingerprints", "Air-dry before packaging", "Large paper bag, sealed and labelled"),
    EvidenceType.DRUG_PACKET to listOf("Never open or taste suspected drugs", "Weigh and field-test only in the lab", "Seal in a plastic evidence bag"),
    EvidenceType.DIGITAL_SCALE to listOf("Residue on the pan links it to drugs", "Check for fingerprints on the buttons", "Plastic bag or box"),
)

@Composable
fun ArHubScreen(openViewer: (String) -> Unit, openCamera: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val caps = remember { detectCapabilities(context) }
    val ar = rememberArSupport()
    // Full AR, or camera view on phones without ARCore; null when the phone can do neither.
    val camera = openCamera?.takeIf { ar != ArSupport.UNSUPPORTED || (caps.camera && caps.gyroscope) }
    var showDevice by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.title_evidence_lab)) { padding ->
        WorkspaceList(Modifier.padding(padding)) {
            item("ar-intro") {
                // How AR works here: pick a specimen, then "View in your room".
                ForensimCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        IconTile(Icons.Outlined.ViewInAr, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text("See evidence in your room", style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                ar != ArSupport.UNSUPPORTED -> "Point the camera at a table or the floor and tap to place a specimen life-size. Swipe left or right to go through them."
                                caps.camera && caps.gyroscope -> "This phone doesn't support full AR, so specimens open in camera view: each one floats in front of you. Swipe left or right to go through them."
                                else -> "This phone has no camera or motion sensor for AR. You can still inspect every specimen in 3D."
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    camera?.let { open ->
                        androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.md))
                        com.forensim.app.core.designsystem.PrimaryButton("Open camera",
                            onClick = { open(SpecimenCategory.ordered.first().key) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            // Specimens by group. Red herrings are never listed: that would tell students which objects to ignore.
            SpecimenCategory.shown.forEach { category ->
                item("cat-${category.name}") {
                    SectionHeader("${category.label} (${category.specimens.size})", trailing = camera?.let { open ->
                        {
                            TextButton(onClick = { open(category.specimens.first().key) }) {
                                Icon(Icons.Outlined.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.xs))
                                Text("Camera")
                            }
                        }
                    })
                    com.forensim.app.core.designsystem.ListGroup {
                        category.specimens.forEachIndexed { i, type ->
                            if (i > 0) com.forensim.app.core.designsystem.ListDivider()
                            com.forensim.app.core.designsystem.ListRow(type.displayName, subtitle = type.description.substringBefore(". ").removeSuffix("."),
                                icon = Icons.Outlined._3dRotation, onClick = { openViewer(type.key) })
                        }
                    }
                }
            }
            item("device") {
                SectionHeader(stringResource(R.string.device))
                com.forensim.app.core.designsystem.ListGroup {
                    com.forensim.app.core.designsystem.ListRow(stringResource(R.string.device_support), subtitle = stringResource(R.string.device_support_sub), onClick = { showDevice = !showDevice },
                        trailing = { Icon(if (showDevice) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                    if (showDevice) {
                        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
                            CapabilityRow(stringResource(R.string.cap_3d_graphics), caps.gles3)
                            CapabilityRow(stringResource(R.string.cap_camera), caps.camera)
                            CapabilityRow(stringResource(R.string.cap_motion), caps.gyroscope)
                            CapabilityRow(stringResource(R.string.cap_arcore), ar == ArSupport.SUPPORTED, note = when (ar) {
                                ArSupport.CHECKING -> "Checking…"
                                ArSupport.NEEDS_INSTALL -> "Supported · install on first use"
                                ArSupport.SUPPORTED -> null
                                ArSupport.UNSUPPORTED -> stringResource(R.string.unavailable)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, available: Boolean, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (available) Icons.Outlined.CheckCircle else Icons.Outlined.RemoveCircleOutline, contentDescription = null,
            tint = if (available) ForensimTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(note ?: if (available) stringResource(R.string.available) else stringResource(R.string.unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EvidenceViewerScreen(typeKey: String, onBack: () -> Unit, openAr: (() -> Unit)? = null) {
    val type = EvidenceType.from(typeKey) ?: EvidenceType.CARTRIDGE_CASE
    // Full AR where ARCore is (or can be) installed; camera view on phones with just a camera and
    // rotation sensor; nothing on phones with neither.
    val context = LocalContext.current
    val ar = rememberArSupport()
    val canCameraView = remember { detectCapabilities(context).let { it.camera && it.gyroscope } }
    val fullAr = ar == ArSupport.SUPPORTED || ar == ArSupport.NEEDS_INSTALL
    val arAction: Pair<String, () -> Unit>? = openAr?.let { open ->
        when {
            fullAr -> "View in your room (AR)" to open
            ar == ArSupport.UNSUPPORTED && canCameraView -> "View with your camera" to open
            else -> null
        }
    }
    val assets = androidx.compose.ui.platform.LocalContext.current.assets
    val renderer = remember(type) { EvidenceViewerRenderer(type, assets) }
    ScreenScaffold(title = type.displayName, onBack = onBack) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (maxWidth >= 650.dp) {
                Row(
                    Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    EvidenceViewport(type, renderer, Modifier.weight(1.15f).fillMaxSize())
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { EvidenceNotes(type, arAction) }
                }
            } else if (maxHeight >= 450.dp) {
                val viewportHeight = (maxHeight * 0.5f).coerceIn(240.dp, 380.dp)
                Column(Modifier.widthIn(max = Spacing.readableWidth).fillMaxSize().padding(horizontal = Spacing.md)) {
                    EvidenceViewport(type, renderer, Modifier.fillMaxWidth().height(viewportHeight))
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.md),
                    ) {
                        EvidenceNotes(type, arAction)
                    }
                }
            } else {
                val viewportHeight = 240.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    EvidenceViewport(type, renderer, Modifier.fillMaxWidth().height(viewportHeight))
                    EvidenceNotes(type, arAction)
                }
            }
        }
    }
}

@Suppress("DEPRECATION") // Rotation directions refer to the physical model in every locale.
@Composable
private fun EvidenceViewport(type: EvidenceType, renderer: EvidenceViewerRenderer, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
    val colors = ForensimTheme.colors
    fun rotate(delta: Float) {
        renderer.yawDeg += delta
        surface?.requestRender()
    }
    fun zoom(factor: Float) {
        renderer.distance = (renderer.distance * factor).coerceIn(0.2f, 1.4f)
        surface?.requestRender()
    }
    Surface(modifier, color = colors.hudScrim.copy(alpha = 1f), contentColor = colors.onHud, shape = MaterialTheme.shapes.large) {
        Column {
            Box(Modifier.fillMaxWidth().weight(1f)
                .semantics { contentDescription = "Interactive 3D model of ${type.displayName}" }
                .pointerInput(type) {
                    detectTransformGestures { _, pan, scale, _ ->
                        renderer.yawDeg -= pan.x * 0.4f
                        renderer.pitchDeg = (renderer.pitchDeg + pan.y * 0.3f).coerceIn(5f, 85f)
                        zoom(1f / scale)
                    }
                }) {
                AndroidView(factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(3)
                        setEGLConfigChooser(com.forensim.app.engine.renderer.HdConfigChooser())
                        setRenderer(renderer)
                        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                        surface = this
                    }
                }, modifier = Modifier.fillMaxSize())
                Text("10 cm scale", style = MaterialTheme.typography.labelMedium, color = colors.onHud,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm))
            }
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = { rotate(-25f) }) { Icon(Icons.Outlined.RotateLeft, "Rotate left") }
                IconButton(onClick = { rotate(25f) }) { Icon(Icons.Outlined.RotateRight, "Rotate right") }
                IconButton(onClick = { zoom(0.8f) }) { Icon(Icons.Outlined.ZoomIn, "Zoom in") }
                IconButton(onClick = { zoom(1.25f) }) { Icon(Icons.Outlined.ZoomOut, "Zoom out") }
                IconButton(onClick = {
                    renderer.yawDeg = 30f
                    renderer.pitchDeg = 25f
                    renderer.distance = renderer.defaultDistance
                    surface?.requestRender()
                }) { Icon(Icons.Outlined.RestartAlt, "Reset view") }
            }
        }
    }
    DisposableEffect(lifecycle, surface) {
        val currentSurface = surface
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) currentSurface?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) currentSurface?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentSurface?.onPause()
        }
    }
}

@Composable
private fun EvidenceNotes(type: EvidenceType, openAr: Pair<String, () -> Unit>?) {
    openAr?.let { (label, open) ->
        com.forensim.app.core.designsystem.PrimaryButton(label, onClick = open,
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm))
    }
    ForensimCard {
        Text(type.description, style = MaterialTheme.typography.bodyLarge)
        Text("Drag to rotate or pinch to zoom. You can also use the viewer controls.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm))
        SectionHeader("Identifying features")
        specimenFeatures[type].orEmpty().forEachIndexed { index, feature ->
            Row(Modifier.padding(bottom = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("${index + 1}.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(feature, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        SectionHeader("Collection procedure")
        Text("Collect: ${type.recommendedCollect.actionLabel.lowercase()}. Package: ${type.recommendedPackaging.joinToString(" or ") { it.label.lowercase() }}.",
            style = MaterialTheme.typography.bodyMedium)
    }
}
