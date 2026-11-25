package com.forensim.app.core.designsystem

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.PressInteraction
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieProperty
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.forensim.app.R

/**
 * Forensim's Lottie animations (authored for the app in LottieFiles Creator, see res/raw):
 *  - [SUCCESS]: badge pops, check draws on, fingerprint arcs and sparks burst. Plays once.
 *  - [SCAN]: fingerprint ridges with a scan beam. Loops; used for loading.
 *  - [REVIEW]: magnifier sweeping a case file. Loops; used for "keep practising" and empty states.
 */
object ForensimAnimations {
    @RawRes val SUCCESS = R.raw.forensim_success
    @RawRes val SCAN = R.raw.forensim_scan
    @RawRes val REVIEW = R.raw.forensim_review
}

/**
 * Plays a Lottie from res/raw. Decorative: hidden from accessibility services, so the text next
 * to it must carry the meaning. With reduced motion a one-shot animation shows its final frame
 * and a loop shows a representative still.
 */
@Composable
fun ForensimLottie(
    @RawRes res: Int,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    size: Dp = 160.dp,
    stillProgress: Float = if (loop) 0.35f else 1f,
    /** Layers (by name) whose fills and strokes are recoloured, e.g. to sit on the hero gradient. */
    tintLayers: List<String> = emptyList(),
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val reduced = LocalReducedMotion.current
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        isPlaying = !reduced,
        // Many phones turn the system animation speed to 0 (battery saver, "Remove animations",
        // developer options), which makes Lottie jump straight to its last frame and look frozen.
        // Play our own animations anyway; the in-app "Reduced motion" setting still stops them.
        ignoreSystemAnimatorScale = true,
    )
    val argb = tint.toArgb()
    val dynamic = rememberLottieDynamicProperties(
        *tintLayers.flatMap { layer ->
            listOf(
                rememberLottieDynamicProperty(LottieProperty.STROKE_COLOR, argb, layer, "**"),
                rememberLottieDynamicProperty(LottieProperty.COLOR, argb, layer, "**"),
            )
        }.toTypedArray(),
    )
    Box(modifier.size(size).clearAndSetSemantics {}) {
        LottieAnimation(
            composition = composition,
            progress = { if (reduced) stillProgress else progress },
            dynamicProperties = dynamic,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Fades and lifts content in after [delayMs], for staggered reveals on result screens.
 * No-op under reduced motion.
 */
@Composable
fun Reveal(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val t = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) t.animateTo(1f, tween(Motion.REVEAL_MS, delayMillis = delayMs, easing = FastOutSlowInEasing))
    }
    Box(modifier.graphicsLayer { alpha = t.value; translationY = (1f - t.value) * 24.dp.toPx() }) { content() }
}

/** One press feel for the whole app: how far each kind of element dips while pressed. */
object PressScale {
    const val BUTTON = 0.96f
    const val CARD = 0.98f
    const val ICON = 0.88f
}

/**
 * Tactile press feedback shared by every button and card: the element dips to [pressedScale]
 * while a finger is on it and springs back on release. A quick tap still shows a short dip (in
 * scrolling lists Compose reports press and release together), so every tap feels acknowledged.
 * Runs in the graphics layer: no recomposition per frame, full display refresh rate.
 */
@Composable
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.InteractionSource, pressedScale: Float = PressScale.BUTTON): Modifier {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interaction, reduced, pressedScale) {
        if (reduced) { scale.snapTo(1f); return@LaunchedEffect }
        interaction.interactions.collectLatest { i ->
            when (i) {
                is PressInteraction.Press ->
                    scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1400f))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    // Fast tap: make sure the dip is visible before springing back.
                    if (scale.value > 1f - (1f - pressedScale) * 0.6f) scale.animateTo(pressedScale, tween(60))
                    scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f))
                }
            }
        }
    }
    return this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
