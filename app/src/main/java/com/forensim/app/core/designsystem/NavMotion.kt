package com.forensim.app.core.designsystem

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Screen transitions (Material 3 motion):
 *  - Push / pop between a screen and its detail: shared-axis X. The new screen slides in a short
 *    distance with a fade, the old one drifts the other way; back reverses the direction.
 *  - Switching bottom-bar tabs: fade-through with a slight scale, no sideways movement.
 *
 * Durations stay short (200-320 ms) and use the emphasized curve, so screens settle fast and
 * motion never blocks input. Every animation here is a graphics-layer transform (translation,
 * alpha, scale) and runs at the display's full refresh rate without recomposition.
 */
object NavMotion {
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val SLIDE_MS = 320
    private const val FADE_IN_MS = 210
    private const val FADE_OUT_MS = 150

    fun enter(tab: Boolean): EnterTransition =
        if (tab) fadeIn(tween(FADE_IN_MS, delayMillis = 60, easing = LinearOutSlowInEasing)) +
            scaleIn(tween(SLIDE_MS, easing = Emphasized), initialScale = 0.97f)
        else slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 8 } +
            fadeIn(tween(FADE_IN_MS, easing = LinearOutSlowInEasing))

    fun exit(tab: Boolean): ExitTransition =
        if (tab) fadeOut(tween(90))
        else slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 14 } + fadeOut(tween(FADE_OUT_MS))

    fun popEnter(tab: Boolean): EnterTransition =
        if (tab) enter(true)
        else slideInHorizontally(tween(SLIDE_MS, easing = Emphasized)) { -it / 14 } +
            fadeIn(tween(FADE_IN_MS, easing = LinearOutSlowInEasing))

    fun popExit(tab: Boolean): ExitTransition =
        if (tab) exit(true)
        else slideOutHorizontally(tween(SLIDE_MS, easing = Emphasized)) { it / 8 } + fadeOut(tween(FADE_OUT_MS))
}
