package com.forensim.app.feature.ar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SpecimenCategory
import com.forensim.app.engine.scene.collectTip
import com.forensim.app.engine.scene.packagingTip
import kotlin.math.roundToInt

/** Forensic HUD colours for the AR overlay. */
private val HudCyan = Color(0xFF3DE3F2)
private val HudYellow = Color(0xFFF4DC3C)
private val HudInk = Color(0xFF12161A)
private val HudPanel = Color(0xF20A1419)
private val HudMuted = Color(0xFF8FB3BA)

/**
 * The callout on the specimen in the camera/AR view: a pulsing ring on the item, a leader line,
 * and a yellow "INSPECT …" tag that follows it on screen. Tapping the tag calls [onOpen].
 * [point] is where the top of the specimen is (pixels, from the renderer); null hides it.
 */
@Composable
internal fun EvidenceCallout(point: ScreenPoint?, type: EvidenceType, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "ring",
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val p = point ?: return@BoxWithConstraints
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        with(density) {
            val margin = Spacing.md.toPx()
            // Keep clear of the top bar and the bottom panel.
            val top = 150.dp.toPx()
            val bottom = h - 230.dp.toPx()
            val lw = labelSize.width.toFloat()
            val lh = labelSize.height.toFloat()
            val reach = 44.dp.toPx()
            // Up and to the right of the item; to the left when there's no room on the right.
            val right = p.x + reach + lw + margin <= w
            val lx = (if (right) p.x + reach else p.x - reach - lw).coerceIn(margin, (w - lw - margin).coerceAtLeast(margin))
            val ly = (p.y - 96.dp.toPx() - lh).coerceIn(top, bottom.coerceAtLeast(top))
            // The line meets the tag's lower corner nearest the item.
            val attach = Offset(if (right) lx else lx + lw, ly + lh)
            val dot = Offset(p.x, p.y)

            Canvas(Modifier.fillMaxSize()) {
                // Pulsing ring and dot on the item.
                drawCircle(HudYellow.copy(alpha = 0.9f * (1f - pulse)), radius = 6.dp.toPx() + 16.dp.toPx() * pulse, center = dot,
                    style = Stroke(1.5.dp.toPx()))
                drawCircle(HudYellow, radius = 3.5.dp.toPx(), center = dot)
                // Leader line: up from the item, then across to the tag.
                val elbow = Offset(dot.x + (attach.x - dot.x) * 0.25f, attach.y)
                drawLine(HudYellow, dot, elbow, strokeWidth = 1.5.dp.toPx())
                drawLine(HudYellow, elbow, attach, strokeWidth = 1.5.dp.toPx())
            }

            Box(
                Modifier.offset { IntOffset(lx.roundToInt(), ly.roundToInt()) }
                    .onSizeChanged { labelSize = it }
                    .widthIn(max = 240.dp)
                    .drawBehind { tagFrame() }
                    .clickable(onClick = onOpen)
                    .semantics { role = Role.Button; contentDescription = "Inspect ${type.displayName}: open the evidence file" }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text("INSPECT ${type.displayName.uppercase()}", color = HudInk, fontWeight = FontWeight.Black,
                    fontSize = 16.sp, letterSpacing = 0.6.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Yellow tag with a thin dark inner line and corner ticks. */
private fun DrawScope.tagFrame() {
    drawRect(HudYellow)
    val i = 3.dp.toPx()
    drawRect(HudInk.copy(alpha = 0.55f), topLeft = Offset(i, i), size = Size(size.width - 2 * i, size.height - 2 * i),
        style = Stroke(1.dp.toPx()))
    val t = 7.dp.toPx()
    val s = 1.5.dp.toPx()
    for ((x, y, dx, dy) in listOf(
        listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f), listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f),
    )) {
        drawLine(HudInk, Offset(x, y), Offset(x + t * dx, y), strokeWidth = s)
        drawLine(HudInk, Offset(x, y), Offset(x, y + t * dy), strokeWidth = s)
    }
}

/** Dark glass panel with a faint cyan outline and bright cyan corner brackets. */
private fun DrawScope.hudFrame(fill: Color = HudPanel) {
    drawRect(fill)
    drawRect(HudCyan.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    val t = 22.dp.toPx()
    val s = 2.5.dp.toPx()
    for ((x, y, dx, dy) in listOf(
        listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f), listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f),
    )) {
        drawLine(HudCyan, Offset(x, y), Offset(x + t * dx, y), strokeWidth = s)
        drawLine(HudCyan, Offset(x, y), Offset(x, y + t * dy), strokeWidth = s)
    }
}

/**
 * The evidence file opened from the callout: what the item is, its size and category, how to
 * recognise it, and how to collect and package it, on a forensic HUD panel. Tap outside or ✕ to close.
 */
@Composable
internal fun EvidenceFile(type: EvidenceType?, onClose: () -> Unit) {
    // Keep the last item while the panel animates out.
    var shown by remember { mutableStateOf(type) }
    if (type != null) shown = type
    // Back closes the file instead of leaving the camera.
    androidx.activity.compose.BackHandler(enabled = type != null, onBack = onClose)
    AnimatedVisibility(type != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
        val t = shown ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(Spacing.md).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 640.dp)
                    .drawBehind { hudFrame() }
                    // Taps on the panel itself don't close it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FileHeader(t, onClose)
                Field("Description", t.description)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Field("Category", SpecimenCategory.of(t).label, Modifier.weight(1f))
                    Field("Approx. size", sizeText(t), Modifier.weight(1f))
                }
                specimenFeatures[t]?.takeIf { it.isNotEmpty() }?.let { features ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Caption("Identifying features")
                        features.forEach { f ->
                            Text(f.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
                            DashedRule()
                        }
                    }
                }
                Field("Collect with: ${t.recommendedCollect.label}", t.collectTip)
                Field("Package in: ${t.recommendedPackaging.joinToString(" or ") { it.label.lowercase() }}", t.packagingTip)
            }
        }
    }
}

@Composable
private fun FileHeader(t: EvidenceType, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = HudCyan, modifier = Modifier.size(30.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(t.displayName.uppercase(), color = HudCyan, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text("EVIDENCE FILE · ${SpecimenCategory.of(t).label.uppercase()}", color = HudMuted, fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
        }
        // Close: yellow bracket box with an X.
        Box(
            Modifier.size(44.dp).drawBehind {
                val t2 = 9.dp.toPx(); val s = 2.dp.toPx()
                for ((x, y, dx, dy) in listOf(
                    listOf(0f, 0f, 1f, 1f), listOf(size.width, 0f, -1f, 1f), listOf(0f, size.height, 1f, -1f), listOf(size.width, size.height, -1f, -1f),
                )) {
                    drawLine(HudYellow, Offset(x, y), Offset(x + t2 * dx, y), strokeWidth = s)
                    drawLine(HudYellow, Offset(x, y), Offset(x, y + t2 * dy), strokeWidth = s)
                }
            }.clickable(onClick = onClose).semantics { contentDescription = "Close evidence file" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = HudYellow) }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text.uppercase(), color = HudCyan.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
private fun Field(caption: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Caption(caption)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
        DashedRule()
    }
}

@Composable
private fun DashedRule() {
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(HudCyan.copy(alpha = 0.35f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
    }
}

/** "9 × 2 × 2 cm" from the specimen's real size (metres). */
private fun sizeText(t: EvidenceType): String {
    val (w, h, d) = t.size
    fun cm(v: Float) = if (v * 100 < 1f) "<1" else "%.0f".format(v * 100)
    return "${cm(w)} × ${cm(d)} × ${cm(h)} cm"
}
