package com.forensim.app.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.TextButton

/** Reasons a user can give when reporting content (keys match content_reports.reason). */
val CONTENT_REASONS = listOf(
    "offensive" to "Offensive or inappropriate",
    "harassment" to "Harassment or bullying",
    "sexual" to "Sexual content",
    "violent" to "Violent or dangerous content",
    "hate" to "Hate speech",
    "spam" to "Spam",
    "other" to "Something else",
)

/** Reasons for reporting an AI answer or draft: adds "wrong or misleading". */
val AI_REASONS = listOf(
    "wrong_info" to "Wrong or misleading information",
    "offensive" to "Offensive or inappropriate",
    "harassment" to "Harmful or hurtful",
    "sexual" to "Sexual content",
    "violent" to "Violent or dangerous content",
    "hate" to "Hate speech",
    "other" to "Something else",
)

/**
 * Report dialog shared by class chat, the AI tutor and AI quiz drafts. The user picks a reason and
 * may add a note; [onSubmit] sends it. Reports go to the administrators without leaving the app.
 */
@Composable
fun ReportDialog(
    title: String,
    explanation: String,
    reasons: List<Pair<String, String>> = CONTENT_REASONS,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.selectableGroup()) {
                    reasons.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .selectable(selected = reason == key, onClick = { reason = key }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = reason == key, onClick = null)
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.sm))
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = details, onValueChange = { if (it.length <= 500) details = it },
                    label = { Text("Details (optional)") }, maxLines = 4,
                    colors = com.forensim.app.core.designsystem.forensimFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let { onSubmit(it, details) } }, enabled = reason != null) { Text("Send report") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
