package com.forensim.app.feature.ar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextAlign
import com.forensim.app.core.designsystem.IconButton
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SpecimenCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The next (+1) or previous (-1) specimen in the swipe order, wrapping round at the ends. */
internal fun neighbour(type: EvidenceType, step: Int): EvidenceType {
    val all = SpecimenCategory.ordered
    val i = all.indexOf(type).coerceAtLeast(0)
    return all[Math.floorMod(i + step, all.size)]
}

/**
 * Drives the swipe between specimens in the camera/AR view, like a photo gallery: the specimen
 * follows the finger ([drag]); let go past about a fifth of the screen, or flick, and it glides
 * off while the next one glides in from the other side; otherwise it springs back.
 */
internal class SpecimenSwipe(
    private val scope: CoroutineScope,
    private val renderer: SpecimenRenderer,
    private val current: () -> EvidenceType,
    private val onSelect: (EvidenceType) -> Unit,
) {
    private var pos = 0f
    private var job: Job? = null

    private fun set(v: Float) { pos = v; renderer.slide = v }

    private fun stop() {
        job?.cancel()
        // Interrupted mid-change: start the next drag from the middle, not from off-screen.
        if (abs(pos) > 1f) set(0f)
    }

    /** Finger moved by [dx] screen widths (negative = left). */
    fun drag(dx: Float) {
        stop()
        set((pos + dx).coerceIn(-1.2f, 1.2f))
    }

    /** Finger lifted, moving at [velocity] screen widths per second. */
    fun release(velocity: Float) {
        when {
            (pos < -0.2f && velocity < 0.8f) || velocity < -1f -> change(neighbour(current(), 1), toLeft = true, velocity)
            (pos > 0.2f && velocity > -0.8f) || velocity > 1f -> change(neighbour(current(), -1), toLeft = false, velocity)
            else -> settle(velocity)
        }
    }

    /** Spring back to the middle (e.g. a short drag, or a second finger came down). */
    fun settle(velocity: Float = 0f) {
        stop()
        job = scope.launch {
            animate(pos, 0f, velocity, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> set(v) }
        }
    }

    /** Arrows: next (+1) or previous (-1). */
    fun step(step: Int) = change(neighbour(current(), step), toLeft = step > 0)

    /** Category chips: jump straight to [target]. */
    fun jump(target: EvidenceType) {
        if (target == current()) return
        val all = SpecimenCategory.ordered
        change(target, toLeft = all.indexOf(target) > all.indexOf(current()))
    }

    private fun change(next: EvidenceType, toLeft: Boolean, velocity: Float = 0f) {
        stop()
        job = scope.launch {
            val out = if (toLeft) -1.15f else 1.15f
            animate(pos, out, velocity, tween(140, easing = FastOutLinearInEasing)) { v, _ -> set(v) }
            renderer.show(next)
            onSelect(next)
            set(-out)
            animate(-out, 0f, 0f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> set(v) }
        }
    }
}

@Composable
internal fun rememberSpecimenSwipe(renderer: SpecimenRenderer, type: EvidenceType, onSelect: (EvidenceType) -> Unit): SpecimenSwipe {
    val scope = rememberCoroutineScope()
    val currentType by rememberUpdatedState(type)
    val select by rememberUpdatedState(onSelect)
    return remember(renderer) { SpecimenSwipe(scope, renderer, { currentType }, { select(it) }) }
}

/**
 * Camera gestures: one finger dragged sideways swipes between specimens ([swipe]); two fingers
 * pinch to resize and twist to turn ([onTransform]). A swipe consumes its moves, so it never also
 * counts as a tap (placing in AR); a plain tap is left for the tap detector.
 */
internal fun Modifier.specimenGestures(swipe: SpecimenSwipe, onTransform: (zoom: Float, rotationDeg: Float) -> Unit) =
    pointerInput(swipe) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var twoFingers = false
            var dragging = false
            var totalX = 0f
            var totalY = 0f
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    if (dragging) { dragging = false; swipe.settle() }
                    twoFingers = true
                    onTransform(event.calculateZoom(), event.calculateRotation())
                } else if (!twoFingers) {
                    val change = event.changes.first()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val d = change.positionChange()
                    totalX += d.x; totalY += d.y
                    if (!dragging && abs(totalX) > slop && abs(totalX) > abs(totalY)) dragging = true
                    if (dragging) {
                        swipe.drag(d.x / size.width)
                        change.consume()
                    }
                }
            } while (event.changes.any { it.pressed })
            if (dragging) swipe.release(tracker.calculateVelocity().x / size.width)
        }
    }

/**
 * Top of the camera/AR view, one row: a category dropdown (jump to a group) and, beside it, the
 * current specimen with arrows and "2 of 6". The name slides in the direction of the swipe.
 */
@Composable
internal fun SpecimenSwitcher(
    type: EvidenceType,
    swipe: SpecimenSwipe,
    modifier: Modifier = Modifier,
    /** Shown under the dropdown, at the left (the reset button). */
    below: (@Composable () -> Unit)? = null,
) {
    // Same 16 dp margin from the screen edges as the panel at the bottom; 8 dp between rows.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SwitcherRow(type, swipe)
        below?.let { Box(Modifier.padding(horizontal = Spacing.md)) { it() } }
    }
}

@Composable
private fun SwitcherRow(type: EvidenceType, swipe: SpecimenSwipe) {
    val category = SpecimenCategory.of(type)
    var menuOpen by remember { mutableStateOf(false) }
    val glass = Color.Black.copy(alpha = 0.6f)
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        // Category dropdown.
        Box(Modifier.fillMaxHeight()) {
            Surface(onClick = { menuOpen = true }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.fillMaxHeight().widthIn(max = 150.dp)
                    .semantics { contentDescription = "Category: ${category.label}. Change category" }) {
                Row(Modifier.padding(start = Spacing.md, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.shortLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SpecimenCategory.shown.forEach { c ->
                    DropdownMenuItem(
                        text = { Text("${c.label} (${c.specimens.size})") },
                        onClick = { menuOpen = false; swipe.jump(c.specimens.first()) },
                        trailingIcon = if (c == category) ({ Icon(Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                    )
                }
            }
        }
        // Current specimen with arrows.
        Surface(shape = MaterialTheme.shapes.large, color = glass, contentColor = Color.White, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { swipe.step(-1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous specimen", tint = Color.White)
                }
                AnimatedContent(type, modifier = Modifier.weight(1f), label = "specimen", transitionSpec = {
                    val forward = SpecimenCategory.ordered.indexOf(targetState) > SpecimenCategory.ordered.indexOf(initialState)
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
                }) { t ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.displayName, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val c = SpecimenCategory.of(t)
                        Text("${c.specimens.indexOf(t) + 1} of ${c.specimens.size}",
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
                IconButton(onClick = { swipe.step(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next specimen", tint = Color.White)
                }
            }
        }
    }
}
