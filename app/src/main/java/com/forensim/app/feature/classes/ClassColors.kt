package com.forensim.app.feature.classes

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.TextButton
import kotlinx.coroutines.flow.first

/**
 * Colors a professor can give a class. Each has a soft light-mode tint and a deep dark-mode tint,
 * both quiet enough for the theme's normal text colors to stay readable on top.
 */
enum class ClassColor(val label: String, val light: Color?, val dark: Color?) {
    DEFAULT("Default", null, null),
    RED("Red", Color(0xFFF9DADA), Color(0xFF4A2326)),
    ORANGE("Orange", Color(0xFFFBE3CF), Color(0xFF4A3020)),
    YELLOW("Yellow", Color(0xFFF7EDC4), Color(0xFF443B1C)),
    GREEN("Green", Color(0xFFD9EFD9), Color(0xFF233D27)),
    TEAL("Teal", Color(0xFFD2EEEA), Color(0xFF1E3D3A)),
    BLUE("Blue", Color(0xFFDAE6F8), Color(0xFF22324A)),
    PURPLE("Purple", Color(0xFFE6DDF6), Color(0xFF34284A)),
    PINK("Pink", Color(0xFFF6DCEB), Color(0xFF47263A)),
    GRAY("Gray", Color(0xFFE6E6E6), Color(0xFF333333));

    /** Value stored in classes.color (null for DEFAULT). */
    val key: String? get() = if (this == DEFAULT) null else name.lowercase()

    companion object {
        fun from(key: String?): ClassColor = entries.firstOrNull { it.key != null && it.key == key } ?: DEFAULT
    }
}

/** The tint a card should use for [color]; DEFAULT falls back to the theme's subject tint. */
@Composable
fun classTint(color: ClassColor): Color {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return (if (dark) color.dark else color.light) ?: cs.primaryContainer.copy(alpha = 0.55f)
}

/**
 * A slightly deeper shade of a card [tint] for the pieces inside it (icon tile, class code box),
 * so they belong to the card's colour instead of sitting on it as grey patches.
 */
@Composable
fun classInnerTint(tint: Color): Color {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val solid = tint.compositeOver(cs.surfaceContainer)
    return lerp(solid, if (dark) Color.White else Color.Black, if (dark) 0.08f else 0.07f)
}

private val Context.classColorStore by preferencesDataStore(name = "class_colors")

/**
 * Colors picked before class colors were saved on the server (classes.color). They only lived on
 * the professor's phone; the professor screens copy them to the server once and then forget them.
 */
object LegacyClassColors {
    suspend fun read(context: Context): Map<String, ClassColor> =
        context.applicationContext.classColorStore.data.first().asMap().mapNotNull { (key, value) ->
            val c = ClassColor.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
            key.name to c
        }.toMap()

    suspend fun forget(context: Context, classId: String) {
        context.applicationContext.classColorStore.edit { it.remove(stringPreferencesKey(classId)) }
    }
}

/** Swatch picker: tapping a color saves it and closes the dialog. */
@Composable
fun ClassColorDialog(current: ClassColor, onPick: (ClassColor) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Class color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Tints this class's card and header for you and your students.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                ClassColor.entries.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        row.forEach { c ->
                            val picked = c == current
                            Box(
                                Modifier.size(44.dp).clip(CircleShape)
                                    .background(classTint(c))
                                    .border(if (picked) 3.dp else 1.dp, if (picked) cs.primary else cs.outlineVariant, CircleShape)
                                    .clickable(role = Role.RadioButton) { onPick(c) }
                                    .semantics { contentDescription = c.label; selected = picked },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (picked) Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.onSurface, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
