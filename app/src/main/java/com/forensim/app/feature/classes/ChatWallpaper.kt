package com.forensim.app.feature.classes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Faded forensic pattern behind the class chat (fingerprints, microscope, magnifier, DNA, evidence
 * markers...), like a chat wallpaper. Kept very light so messages always stay easy to read.
 * The layout is fixed (not random per frame), so it doesn't shimmer while you scroll or type.
 */
@Composable
fun Modifier.forensicWallpaper(): Modifier {
    // Follows the app's own theme (it can differ from the phone's), so check the actual background.
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val ink = MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.10f else 0.07f)
    val painters = listOf(
        Icons.Outlined.Fingerprint, Icons.Outlined.Biotech, Icons.Outlined.Search, Icons.Outlined.CameraAlt,
        Icons.Outlined.Policy, Icons.Outlined.Science, Icons.Outlined.Gavel, Icons.Outlined.FindInPage, Icons.Outlined.Straighten,
    ).map { rememberVectorPainter(it) }
    val filter = remember(ink) { ColorFilter.tint(ink) }
    return drawBehind {
        val cell = 92.dp.toPx()
        val icon = 30.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        for (row in 0 until rows) for (col in 0 until cols) {
            // Staggered rows, and each cell gets its own motif and tilt from a fixed hash.
            val x = col * cell + if (row % 2 == 1) cell / 2 else 0f
            val y = row * cell + cell * 0.25f
            val h = (row * 73 + col * 151 + row * col * 7) and 0x7fffffff
            val tilt = (h % 41) - 20f
            val center = Offset(x, y)
            rotate(tilt, center) {
                when (val kind = h % (painters.size + 2)) {
                    painters.size -> dnaHelix(center, icon, ink)
                    painters.size + 1 -> evidenceMarker(center, icon, ink)
                    else -> translate(center.x - icon / 2, center.y - icon / 2) {
                        with(painters[kind]) { draw(Size(icon, icon), colorFilter = filter) }
                    }
                }
            }
        }
    }
}

private fun Color.luminanceIsDark() = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** Two twisting strands with rungs, drawn upright in a [s]-sized box around [c]. */
private fun DrawScope.dnaHelix(c: Offset, s: Float, color: Color) {
    val top = c.y - s / 2
    val w = s * 0.32f
    val stroke = s * 0.07f
    val a = Path()
    val b = Path()
    val steps = 24
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = top + t * s
        val dx = sin(t * 2 * PI).toFloat() * w
        if (i == 0) { a.moveTo(c.x + dx, y); b.moveTo(c.x - dx, y) } else { a.lineTo(c.x + dx, y); b.lineTo(c.x - dx, y) }
    }
    drawPath(a, color, style = Stroke(stroke, cap = StrokeCap.Round))
    drawPath(b, color, style = Stroke(stroke, cap = StrokeCap.Round))
    for (i in 1..5) {
        val t = i / 6f
        val y = top + t * s
        val dx = sin(t * 2 * PI).toFloat() * w
        drawLine(color, Offset(c.x - dx, y), Offset(c.x + dx, y), strokeWidth = stroke * 0.7f)
    }
}

/** An evidence tent marker with a "1" on it. */
private fun DrawScope.evidenceMarker(c: Offset, s: Float, color: Color) {
    val tent = Path().apply {
        moveTo(c.x, c.y - s * 0.42f)
        lineTo(c.x + s * 0.42f, c.y + s * 0.38f)
        lineTo(c.x - s * 0.42f, c.y + s * 0.38f)
        close()
    }
    drawPath(tent, color, style = Stroke(s * 0.07f))
    // The "1", drawn as strokes so no text has to be measured while drawing.
    val ny = c.y + s * 0.08f
    val h = s * 0.2f
    drawLine(color, Offset(c.x, ny - h), Offset(c.x, ny + h), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x - h * 0.5f, ny - h * 0.6f), Offset(c.x, ny - h), strokeWidth = s * 0.07f, cap = StrokeCap.Round)
}
