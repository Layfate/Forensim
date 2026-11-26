package com.forensim.app.feature.classes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.moduleIcon
import kotlinx.coroutines.delay

/**
 * Large subject class card shared by the professor and student Classes tabs: a tinted header with
 * the subject icon and title, then details, small stat chips and an optional progress bar.
 */
@Composable
fun SubjectClassCard(
    slug: String,
    subjectNumber: Int,
    title: String,
    subtitle: String?,
    stats: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    code: String? = null,
    status: Pair<String, Tone>? = null,
    progress: Pair<Float, String>? = null,
    /** Small caps line above the title; defaults to "SUBJECT n". */
    label: String? = null,
    /** Card tint; defaults to the theme's subject tint. */
    tint: androidx.compose.ui.graphics.Color? = null,
    /** The professor's cover photo, shown as a banner across the top of the card. */
    coverPath: String? = null,
    /** Show a share button beside the class code (off on the student's cards). */
    shareCode: Boolean = true,
) {
    ForensimCard(modifier = modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      // One frame across the whole card: the professor's cover photo, or else the class tint.
      ClassCardBackground(coverPath, tint ?: classTint(ClassColor.DEFAULT)) { colors ->
        // Header
        Box(
            Modifier.fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = 12.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(moduleIcon(slug), size = 52.dp, container = colors.inner, content = colors.content)
                Column(Modifier.weight(1f)) {
                    Text((label ?: "Subject $subjectNumber").uppercase(), style = MaterialTheme.typography.labelMedium,
                        color = colors.label, fontWeight = FontWeight.SemiBold,
                        letterSpacing = TextUnit(0.1f, TextUnitType.Em))
                    Text(title, style = MaterialTheme.typography.titleLarge, color = colors.content,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                status?.let { (text, tone) -> StatusChip(text, tone) }
            }
        }
        val hasBody = !subtitle.isNullOrBlank() || code != null || stats.isNotEmpty() || progress != null
        if (hasBody) Column(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = 12.dp)) {
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.variant)
                Spacer(Modifier.height(Spacing.sm))
            }
            if (code != null) {
                ClassCodeRow(code, container = colors.inner, contentColor = colors.content, labelColor = colors.variant, share = shareCode)
                if (stats.isNotEmpty()) Spacer(Modifier.height(Spacing.sm))
            }
            if (stats.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    stats.forEach { StatusChip(it, Tone.NEUTRAL) }
                }
            }
            progress?.let { (fraction, label) ->
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Progress", style = MaterialTheme.typography.labelLarge, color = colors.content, modifier = Modifier.weight(1f))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = colors.variant)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    color = colors.content,
                    trackColor = colors.track,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
      }
    }
}

/** Class code with copy and share buttons, compact enough to sit inside a card. */
@Composable
fun ClassCodeRow(
    code: String,
    large: Boolean = false,
    container: androidx.compose.ui.graphics.Color? = null,
    /** Text and icon colors; default to the theme's (pass white when the box sits on a cover photo). */
    contentColor: androidx.compose.ui.graphics.Color? = null,
    labelColor: androidx.compose.ui.graphics.Color? = null,
    /** Show the share button (professors); students only get copy. */
    share: Boolean = true,
) {
    val context = LocalContext.current
    val fg = contentColor ?: MaterialTheme.colorScheme.onSurface
    Surface(shape = RoundedCornerShape(12.dp), color = container ?: MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = fg,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.xs, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Class code", style = MaterialTheme.typography.labelMedium, color = labelColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                Text(code, color = fg, fontWeight = FontWeight.Bold,
                    style = (if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall)
                        .copy(letterSpacing = TextUnit(0.12f, TextUnitType.Em)),
                    modifier = Modifier.semantics { contentDescription = "Class code ${code.toCharArray().joinToString(" ")}" })
            }
            // A check replaces the copy icon for a moment, plus a toast, so the tap visibly worked.
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
            IconButton(onClick = { copyCode(context, code); copied = true }) {
                if (copied) Icon(Icons.Outlined.Check, contentDescription = "Class code copied")
                else Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy class code")
            }
            if (share) IconButton(onClick = { shareCode(context, code) }) { Icon(Icons.Outlined.Share, contentDescription = "Share class code") }
        }
    }
}

fun copyCode(context: Context, code: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ForenSim class code", code))
    Toast.makeText(context, "Class code $code copied", Toast.LENGTH_SHORT).show()
}

fun shareCode(context: Context, code: String, subject: String? = null) {
    val text = buildString {
        append("Join my ")
        append(subject ?: "ForenSim")
        append(" class on ForenSim with the code: ")
        append(code)
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, "Share class code"))
}

/** Small round lock/unlock badge used beside lesson and quiz rows. */
@Composable
fun LockBadge(unlocked: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(28.dp).background(if (unlocked) cs.primaryContainer else cs.surfaceContainerHighest, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (unlocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            contentDescription = if (unlocked) "Unlocked" else "Locked",
            tint = if (unlocked) cs.onPrimaryContainer else cs.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    Spacer(Modifier.width(Spacing.sm))
}
