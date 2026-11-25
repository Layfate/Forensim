package com.forensim.app.feature.simulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.engine.GuideTarget
import com.forensim.app.engine.SimulationGame
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Where the HUD controls the training guide can point at are on screen. */
class GuideAnchors {
    val rects = mutableStateMapOf<String, Rect>()
}

fun Modifier.guideAnchor(anchors: GuideAnchors, key: String): Modifier =
    onGloballyPositioned { anchors.rects[key] = it.boundsInRoot() }

fun toolAnchorKey(tool: com.forensim.app.engine.interaction.Tool) = "tool:${tool.name}"
fun packageAnchorKey(evidenceId: String, tool: com.forensim.app.engine.interaction.Tool) = "pkg:$evidenceId:${tool.name}"

/** Which control the arrow points at, and its label, given what is open ("pause", "inventory" or nothing). */
private fun GuideTarget.anchorFor(panel: String?): Pair<String, String?>? = when (panel) {
    null -> anchorKey()?.let { it to hint() }
    "inventory" -> if (this is GuideTarget.Package) packageAnchorKey(evidenceId, tool) to "Seal it in this"
        else "inv:close" to "Back to the scene"
    else -> when (this) {
        GuideTarget.FinishPart -> "menu:finish" to "Tap to finish"
        GuideTarget.Report -> "menu:report" to "Tap"
        // The inventory is reached from the scene, so close the menu first.
        else -> "menu:resume" to "Back to the scene"
    }
}

private fun GuideTarget.anchorKey(): String? = when (this) {
    is GuideTarget.World -> null
    GuideTarget.Interact -> "interact"
    GuideTarget.Gloves -> "gloves"
    is GuideTarget.ToolChip -> toolAnchorKey(tool)
    is GuideTarget.Package -> "inventory"
    GuideTarget.Report -> "pause"
    GuideTarget.FinishPart -> "finish"
}

private fun GuideTarget.hint(): String? = when (this) {
    is GuideTarget.World -> null
    GuideTarget.Interact -> "Tap"
    GuideTarget.Gloves -> "Put on gloves"
    is GuideTarget.ToolChip -> "Select ${tool.label}"
    is GuideTarget.Package -> "Open inventory"
    GuideTarget.Report -> "Open the menu"
    GuideTarget.FinishPart -> null
}

/**
 * Training guide arrow. For a spot in the room it tracks the camera every frame: a bouncing arrow
 * over the spot when it is in view, or an arrow at the screen edge saying which way to turn. For a
 * HUD control it outlines the control and points at it. Draw-only: it never takes touches.
 */
@Composable
fun GuideOverlay(game: SimulationGame, target: GuideTarget, anchors: GuideAnchors, fovDegrees: Float, panel: String? = null, modifier: Modifier = Modifier) {
    val color = ForensimTheme.colors.hudAttention
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { time = it } }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val t = time / 1_000_000_000f
        val bob = if (reduced) 0f else (sin(t * 5f) * 0.5f + 0.5f) * 10.dp.toPx()
        if (panel == null && target is GuideTarget.World) {
            drawWorldGuide(game, target, fovDegrees, bob, color, measurer, style)
        } else {
            val (key, hint) = target.anchorFor(panel) ?: return@Canvas
            anchors.rects[key]?.let { r -> drawAnchorGuide(r.translate(-origin), hint, bob, color, measurer, style) }
        }
    }
}

private fun DrawScope.drawWorldGuide(
    game: SimulationGame, target: GuideTarget.World, fovDegrees: Float, bob: Float,
    color: Color, measurer: TextMeasurer, style: TextStyle,
) {
    val p = game.player
    val eye = p.eye
    val yaw = p.yaw
    val pitch = p.pitch
    val dx = target.point.x - eye.x
    val dy = target.point.y - eye.y
    val dz = target.point.z - eye.z
    // Camera basis (see PlayerController.forward): right, up, forward.
    val sy = sin(yaw); val cyw = cos(yaw); val sp = sin(pitch); val cp = cos(pitch)
    val camX = dx * cyw + dz * sy
    val camY = -dx * sy * sp + dy * cp + dz * cyw * sp
    val camZ = dx * sy * cp + dy * sp - dz * cyw * cp
    val tanH = tan(Math.toRadians(fovDegrees / 2.0)).toFloat()
    val tanV = tanH / (size.width / size.height).coerceAtLeast(0.1f)
    val distance = "%.1f m".format(sqrt(dx * dx + dz * dz))

    if (camZ > 0.05f) {
        val ndcX = camX / camZ / tanH
        val ndcY = camY / camZ / tanV
        if (abs(ndcX) < 0.85f && abs(ndcY) < 0.8f) {
            val spot = Offset((ndcX + 1f) / 2f * size.width, (1f - ndcY) / 2f * size.height)
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 16.dp.toPx() + bob * 0.4f, center = spot, style = Stroke(5.dp.toPx()))
            drawCircle(color, radius = 16.dp.toPx() + bob * 0.4f, center = spot, style = Stroke(3.dp.toPx()))
            val tip = Offset(spot.x, spot.y - 24.dp.toPx() - bob)
            drawGuideArrow(tip, Offset(0f, 1f), color)
            drawGuideLabel(measurer, distance, style, tip, Offset(0f, 1f))
            return
        }
    }
    // Off screen: point from the centre toward it; if it is behind, say which way to turn.
    var dir = if (camZ > 0.05f) Offset(camX / camZ / tanH * size.width, -camY / camZ / tanV * size.height)
        else Offset(if (camX >= 0f) 1f else -1f, 0f)
    val len = sqrt(dir.x * dir.x + dir.y * dir.y)
    dir = if (len < 0.001f) Offset(1f, 0f) else dir / len
    val margin = 64.dp.toPx()
    val hw = size.width / 2 - margin
    val hh = size.height / 2 - margin
    val reach = minOf(if (abs(dir.x) > 0.001f) hw / abs(dir.x) else Float.MAX_VALUE, if (abs(dir.y) > 0.001f) hh / abs(dir.y) else Float.MAX_VALUE)
    val tip = center + dir * (reach + bob)
    drawGuideArrow(tip, dir, color)
    val turn = when {
        abs(dir.x) >= abs(dir.y) -> if (dir.x > 0) "Turn right" else "Turn left"
        dir.y < 0 -> "Look up"
        else -> "Look down"
    }
    drawGuideLabel(measurer, "$turn · $distance", style, tip, dir)
}

private fun DrawScope.drawAnchorGuide(r: Rect, hint: String?, bob: Float, color: Color, measurer: TextMeasurer, style: TextStyle) {
    val pad = 4.dp.toPx() + bob * 0.3f
    val ring = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
    val corner = CornerRadius(minOf(ring.width, ring.height) / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.45f), ring.topLeft, ring.size, corner, style = Stroke(5.dp.toPx()))
    drawRoundRect(color, ring.topLeft, ring.size, corner, style = Stroke(3.dp.toPx()))
    val gap = 10.dp.toPx() + bob
    // Controls on the side edges get a sideways arrow so it doesn't cover the control stacked above.
    val (tip, dir) = when {
        r.center.y > size.height / 2 && r.center.x > size.width * 0.75f -> Offset(r.left - gap, r.center.y) to Offset(1f, 0f)
        r.center.y > size.height / 2 && r.center.x < size.width * 0.25f -> Offset(r.right + gap, r.center.y) to Offset(-1f, 0f)
        r.center.y > size.height / 2 -> Offset(r.center.x, r.top - gap) to Offset(0f, 1f)
        else -> Offset(r.center.x, r.bottom + gap) to Offset(0f, -1f)
    }
    drawGuideArrow(tip, dir, color)
    hint?.let { drawGuideLabel(measurer, it, style, tip, dir) }
}

private const val HEAD_DP = 22f
private const val SHAFT_DP = 18f

/** A filled arrow whose point is at [tip], pointing along the unit vector [dir]. */
private fun DrawScope.drawGuideArrow(tip: Offset, dir: Offset, color: Color) {
    val head = HEAD_DP.dp.toPx()
    val headHalf = 14.dp.toPx()
    val shaft = SHAFT_DP.dp.toPx()
    val shaftHalf = 5.dp.toPx()
    val perp = Offset(-dir.y, dir.x)
    val base = tip - dir * head
    val tail = base - dir * shaft
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        (base + perp * headHalf).let { lineTo(it.x, it.y) }
        (base + perp * shaftHalf).let { lineTo(it.x, it.y) }
        (tail + perp * shaftHalf).let { lineTo(it.x, it.y) }
        (tail - perp * shaftHalf).let { lineTo(it.x, it.y) }
        (base - perp * shaftHalf).let { lineTo(it.x, it.y) }
        (base - perp * headHalf).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(4.dp.toPx()))
    drawPath(path, color)
}

/** A dark pill label just behind the arrow's tail, kept on screen. */
private fun DrawScope.drawGuideLabel(measurer: TextMeasurer, text: String, style: TextStyle, tip: Offset, dir: Offset) {
    val layout = measurer.measure(text, style)
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val padX = 10.dp.toPx()
    val padY = 4.dp.toPx()
    val extent = abs(dir.x) * (w / 2 + padX) + abs(dir.y) * (h / 2 + padY)
    val c = tip - dir * ((HEAD_DP + SHAFT_DP + 6f).dp.toPx() + extent)
    val left = (c.x - w / 2).coerceIn(padX, (size.width - w - padX).coerceAtLeast(padX))
    val top = (c.y - h / 2).coerceIn(padY, (size.height - h - padY).coerceAtLeast(padY))
    drawRoundRect(Color.Black.copy(alpha = 0.7f), Offset(left - padX, top - padY), Size(w + 2 * padX, h + 2 * padY), CornerRadius(10.dp.toPx()))
    drawText(layout, topLeft = Offset(left, top))
}
