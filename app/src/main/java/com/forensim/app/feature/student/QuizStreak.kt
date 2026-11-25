package com.forensim.app.feature.student

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forensim.app.R
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.Spacing

private val FlameHot = Color(0xFFFF3D00)
private val FlameMid = Color(0xFFFF9100)
private val FlameTip = Color(0xFFFFD740)

/** A fire icon that flickers (sways, stretches and glows) while [lit]; grey and still otherwise. */
@Composable
fun AnimatedFlame(lit: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val animate = lit && !LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "flame")
    val sway by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val stretch by t.animateFloat(0.94f, 1.08f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "stretch")
    val glow by t.animateFloat(0.25f, 0.55f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "shift")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (lit) {
            // Warm glow behind the flame.
            Box(Modifier.size(size).clip(CircleShape).background(
                Brush.radialGradient(listOf(FlameMid.copy(alpha = if (animate) glow else 0.4f), Color.Transparent))))
        }
        Icon(
            Icons.Filled.LocalFireDepartment, contentDescription = null,
            tint = if (lit) Color.White else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(size * 0.8f)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f) // flicker from the base, like a real flame
                    if (animate) { rotationZ = sway; scaleY = stretch; scaleX = 2f - stretch }
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (lit) {
                        // Fire gradient, bottom (hot red) to top (yellow tip), drifting a little.
                        val tip = if (animate) androidx.compose.ui.graphics.lerp(FlameTip, FlameMid, shift * 0.4f) else FlameTip
                        drawRect(Brush.verticalGradient(listOf(tip, FlameMid, FlameHot)), blendMode = BlendMode.SrcIn)
                    }
                },
        )
    }
}

/** Flame plus "3-day streak" and a short status line, for the top of the quiz-of-the-day card. */
@Composable
internal fun StreakBadge(streak: Int, best: Int, answeredToday: Boolean, correctToday: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val lit = streak > 0
    Row(
        modifier.clip(CircleShape).background(cs.surface.copy(alpha = 0.75f))
            .padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AnimatedFlame(lit, 44.dp)
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                // The number rolls up when the streak grows.
                AnimatedContent(streak, transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                }, label = "streak") { n ->
                    Text(n.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = if (lit) FlameHot else cs.onSurfaceVariant)
                }
                Text(" " + stringResource(R.string.streak_days), style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                when {
                    answeredToday && correctToday -> stringResource(R.string.streak_kept)
                    answeredToday -> stringResource(R.string.streak_lost)
                    lit -> stringResource(R.string.streak_keep_going)
                    else -> stringResource(R.string.streak_start)
                } + if (best > 1) "  ·  " + stringResource(R.string.streak_best, best) else "",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            )
        }
    }
}
