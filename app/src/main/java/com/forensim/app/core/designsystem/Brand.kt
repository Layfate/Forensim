package com.forensim.app.core.designsystem

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Icon for each curriculum module (by slug). */
@Suppress("DEPRECATION")
fun moduleIcon(slug: String): ImageVector = when (slug) {
    "photography" -> Icons.Outlined.CameraAlt
    "personal-identification" -> Icons.Outlined.Fingerprint
    "chemistry-toxicology" -> Icons.Outlined.Science
    "questioned-documents" -> Icons.Outlined.Description
    "polygraph" -> Icons.Outlined.MonitorHeart
    "ballistics" -> Icons.Outlined.GpsFixed
    "crime-scene-investigation" -> Icons.Outlined.ManageSearch
    else -> Icons.Outlined.School
}

/** Rounded tonal square holding an icon; used for modules and quick actions. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, container: Color = MaterialTheme.colorScheme.primaryContainer,
             content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.52f))
    }
}

/** Initials avatar. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = name.split(' ', '.', '@').filter { it.isNotBlank() && it.first().isLetter() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        // Initials scale with the avatar (about 38% of its size) so large avatars don't look empty.
        Text(initials, style = MaterialTheme.typography.titleSmall.copy(
            fontSize = (size.value * 0.38f).sp, lineHeight = (size.value * 0.38f).sp, letterSpacing = 0.02.em),
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Appearance picker: Light, Dark and System as three phone mock-ups with a label and a radio
 * button underneath (System is split diagonally). The whole column is the touch target. The
 * mock-ups always show fixed light/dark colours, whatever theme is active.
 */
@Composable
fun ThemePicker(selected: ThemePreference, onSelect: (ThemePreference) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        listOf(ThemePreference.LIGHT, ThemePreference.DARK, ThemePreference.SYSTEM).forEach { preference ->
            val isSelected = selected == preference
            val label = stringResource(when (preference) {
                ThemePreference.SYSTEM -> R.string.theme_system
                ThemePreference.LIGHT -> R.string.theme_light
                ThemePreference.DARK -> R.string.theme_dark
            })
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                    .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(preference) })
                    .padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val mock = Modifier.fillMaxWidth(0.72f).widthIn(max = 88.dp).aspectRatio(0.58f)
                when (preference) {
                    ThemePreference.LIGHT -> PhoneMock(dark = false, mock)
                    ThemePreference.DARK -> PhoneMock(dark = true, mock)
                    ThemePreference.SYSTEM -> Box(mock) {
                        PhoneMock(dark = false, Modifier.fillMaxSize())
                        PhoneMock(dark = true, Modifier.fillMaxSize().clip(LowerRightTriangle))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                androidx.compose.material3.RadioButton(
                    selected = isSelected, onClick = null,
                    modifier = Modifier.padding(top = Spacing.xs).size(Spacing.touchTarget),
                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = cs.primary, unselectedColor = cs.outline),
                )
            }
        }
    }
}

/** Bottom-right half of a box, cut from the top-right corner to the bottom-left corner. */
private val LowerRightTriangle = androidx.compose.foundation.shape.GenericShape { size, _ ->
    moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
}

/** Miniature phone: frame, ForenSim badge, a title bar, three tiles and a content block. */
@Composable
private fun PhoneMock(dark: Boolean, modifier: Modifier) {
    val frame = if (dark) Color(0xFF3A3B40) else Palette.LightHair
    val screen = if (dark) Color(0xFF1C1D1F) else Palette.White
    val block = if (dark) Color(0xFF3A3B40) else Palette.LightHair
    val footer = if (dark) Color(0xFF2A2B2F) else Palette.Surface
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(screen).border(3.dp, frame, shape).padding(horizontal = 9.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.forensim_badge_dark), contentDescription = null,
            modifier = Modifier.size(20.dp).then(if (dark) Modifier.border(0.5.dp, Color(0xFF6A6B70), CircleShape) else Modifier),
        )
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(block))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(block)) }
        }
        Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth().weight(2f).background(block))
            Box(Modifier.fillMaxWidth().weight(1f).background(footer))
        }
    }
}
