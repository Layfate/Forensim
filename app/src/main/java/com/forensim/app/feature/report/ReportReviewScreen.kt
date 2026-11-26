package com.forensim.app.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.TextButton
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.model.ContentReport
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Open reports the signed-in user may review: every report for administrators, and reports about
 * their own classes for professors (row-level security decides which rows come back).
 */
class ReportReviewViewModel(private val c: AppContainer) : ViewModel() {
    var state by mutableStateOf<Load<List<ContentReport>>>(Load.Loading); private set
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            state = try { Load.Ready(c.reports.open()) } catch (e: ApiException) { Load.Error(e.message) }
        }
    }

    /** Marks [r] reviewed; with [deleteMessage], also removes the reported chat message. */
    fun resolve(r: ContentReport, deleteMessage: Boolean) {
        viewModelScope.launch {
            message = try {
                if (deleteMessage) r.messageId?.let { c.chat.delete(it) }
                c.reports.resolve(r.id)
                load()
                if (deleteMessage) "Message removed and report closed." else "Report closed."
            } catch (e: ApiException) { e.message }
        }
    }
}

private val KIND_LABELS = mapOf(
    "chat_message" to "Chat message",
    "user" to "Person",
    "ai_tutor" to "AI tutor answer",
    "ai_quiz" to "AI quiz draft",
)
private val REASON_LABELS = (CONTENT_REASONS + AI_REASONS).toMap()

/** Review queue for reported chat messages, people and AI output (admins and professors). */
@Composable
fun ReportReviewScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportReviewViewModel(it) }
    ScreenScaffold(title = "Reports", onBack = onBack) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { reports, _ ->
            WorkspaceList(Modifier.padding(inner)) {
                item("intro") {
                    PageIntro("Reported content", description = "Reports from class chats and the AI tools. Remove what breaks the rules, then close the report. To suspend someone, use Users (administrators) or remove them from the class.")
                }
                vm.message?.let { m -> item("feedback") { Banner(m, Tone.PRIMARY) } }
                if (reports.isEmpty()) item("empty") { Banner("No open reports.", Tone.SUCCESS) }
                items(reports, key = { it.id }) { r -> ReportCard(r, vm) }
            }
        }
    }
}

@Composable
private fun ReportCard(r: ContentReport, vm: ReportReviewViewModel) {
    ForensimCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(KIND_LABELS[r.kind] ?: r.kind, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusChip(REASON_LABELS[r.reason] ?: r.reason, Tone.ATTENTION)
        }
        Text(fmt(r.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        r.excerpt?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it.take(600), style = MaterialTheme.typography.bodyMedium)
        }
        r.details?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text("Reporter's note: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), horizontalArrangement = Arrangement.End) {
            if (r.kind == "chat_message" && r.messageId != null) {
                TextButton(onClick = { vm.resolve(r, deleteMessage = true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove message") }
            }
            TextButton(onClick = { vm.resolve(r, deleteMessage = false) }) { Text("Close report") }
        }
    }
}

private fun fmt(iso: String) = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
}.getOrDefault(iso)
