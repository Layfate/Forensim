package com.forensim.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Result header shared by quizzes and 3D cases.
 *
 * Layout follows the familiar "lesson complete" pattern: a large animation on a soft glow, an
 * eyebrow chip, a headline, one line of encouragement, then a score card with an animated ring
 * and count-up. Elements reveal in a short stagger so the eye moves top to bottom.
 *
 * [celebrate] picks the animation: the success badge for a strong result, the magnifier sweep
 * ("keep investigating") otherwise. Neither says "failed"; the score speaks for itself.
 */
@Composable
fun ResultHero(
    celebrate: Boolean,
    eyebrow: String,
    headline: String,
    message: String,
    score: Double?,
    maxScore: Double?,
    modifier: Modifier = Modifier,
    stats: List<Pair<String, String>> = emptyList(),
) {
    val fx = ForensimTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = Spacing.sm)) {
            GlowDisc(if (celebrate) fx.success.copy(alpha = 0.22f) else fx.glow, 230.dp)
            ForensimLottie(
                if (celebrate) ForensimAnimations.SUCCESS else ForensimAnimations.REVIEW,
                loop = !celebrate,
                size = if (celebrate) 220.dp else 190.dp,
            )
        }
        Reveal(250) {
            StatusChip(eyebrow, if (celebrate) Tone.SUCCESS else Tone.ATTENTION)
        }
        Spacer(Modifier.height(Spacing.sm + Spacing.xs))
        Reveal(350) {
            Text(headline, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(Spacing.sm))
        Reveal(450) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        }
        if (score != null && maxScore != null && maxScore > 0) {
            Spacer(Modifier.height(Spacing.lg))
            Reveal(560) { ScoreCard(score, maxScore, celebrate, stats) }
        }
    }
}

@Composable
private fun ScoreCard(score: Double, maxScore: Double, celebrate: Boolean, stats: List<Pair<String, String>>) {
    val cs = MaterialTheme.colorScheme
    val fraction = (score / maxScore).toFloat().coerceIn(0f, 1f)
    val reduced = LocalReducedMotion.current
    val anim = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(fraction) {
        if (!reduced) anim.animateTo(1f, tween(Motion.COUNT_UP_MS, delayMillis = 650, easing = FastOutSlowInEasing))
    }
    val pct = (fraction * 100).toInt()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Score ${score.toInt()} of ${maxScore.toInt()}, $pct percent" },
    ) {
        Row(Modifier.padding(Spacing.md + Spacing.xs).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ScoreRing(fraction * anim.value, if (celebrate) ForensimTheme.colors.success else cs.primary, 104.dp)
                // How many out of the total in the middle (e.g. 2/10), the percentage under it.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val outOf = "${(score * anim.value).toInt()}/${maxScore.toInt()}"
                    Text(outOf, style = (if (outOf.length > 5) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium)
                        .copy(fontFeatureSettings = TabularNumbers))
                    Text("${(pct * anim.value).toInt()}%", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Spacing.md + Spacing.xs))
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = Spacing.xs), color = cs.outlineVariant)
            Spacer(Modifier.width(Spacing.md + Spacing.xs))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
                StatLine("Points", "${(score * anim.value).toInt()} / ${maxScore.toInt()}")
                stats.forEach { (label, value) -> StatLine(label, value) }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNumbers))
    }
}

/** Circular progress ring with a round cap on a soft track. */
@Composable
fun ScoreRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 10.dp) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        if (fraction > 0f) {
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

/** Soft radial glow used behind hero animations and icons. */
@Composable
fun GlowDisc(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Brush.radialGradient(listOf(color, Color.Transparent))))
}
