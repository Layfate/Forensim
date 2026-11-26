package com.forensim.app.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forensim.app.R

/**
 * The ForenSim mark: a white detective stencil in a dark circle, so it sits at the same shape as
 * a profile avatar. The same badge is used in both themes; on the dark theme a faint hairline
 * ring keeps the dark circle from melting into the near-black page.
 */
@Composable
fun ForensimMark(size: Dp, modifier: Modifier = Modifier) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ring = if (darkTheme) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape) else Modifier
    Image(
        painter = painterResource(R.drawable.forensim_badge_dark),
        contentDescription = null,
        modifier = modifier.size(size).then(ring),
    )
}

/** Mark + wordmark. The wordmark colour follows the theme like the mark. */
@Composable
fun ForensimLogo(
    modifier: Modifier = Modifier,
    markSize: Dp = 40.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(modifier.clearAndSetSemantics { contentDescription = "ForenSim" }, verticalAlignment = Alignment.CenterVertically) {
        ForensimMark(markSize)
        Spacer(Modifier.width(markSize * 0.25f))
        Text("ForenSim", style = textStyle, color = color)
    }
}
