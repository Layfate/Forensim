package com.forensim.app.feature.tutor

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.LocalReducedMotion
import com.forensim.app.core.designsystem.WorkspaceList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AssistChip
import com.forensim.app.core.designsystem.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.data.ChatTurn
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.model.TutorReference
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val references: List<TutorReference> = emptyList(), val notice: Boolean = false)

class TutorViewModel(private val c: AppContainer, private val moduleTitle: String?) : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
    var sending by mutableStateOf(false); private set
    var locked by mutableStateOf(false); private set

    init {
        viewModelScope.launch { locked = runCatching { c.learning.hasActiveAssessment() }.getOrDefault(false) }
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || sending) return
        val history = messages.filter { !it.notice }.map { ChatTurn(if (it.fromUser) "user" else "assistant", it.text) }
        messages += ChatMessage(true, q)
        sending = true
        viewModelScope.launch {
            try {
                val r = c.tutor.ask(q, moduleTitle?.takeIf { it.isNotBlank() }, history)
                if (r.reason == "active_assessment") locked = true
                messages += ChatMessage(false, r.answer, r.references, notice = r.blocked)
            } catch (e: ApiException) {
                messages += ChatMessage(false, e.message, notice = true)
            } finally {
                sending = false
            }
        }
    }
}

private val suggestions = listOf(
    "Explain chain of custody in simple terms",
    "Why photograph evidence with and without a scale?",
    "Which module should I review next?",
    "What are the limits of polygraph results?",
)

@Composable
fun TutorScreen(moduleTitle: String?, openLesson: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val vm = appViewModel(key = "tutor_${moduleTitle.orEmpty()}") { TutorViewModel(it, moduleTitle) }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val reduceMotion = LocalReducedMotion.current
    val messageCount = vm.messages.size
    val sending = vm.sending
    val locked = vm.locked
    LaunchedEffect(messageCount, sending, locked, reduceMotion) {
        if (messageCount > 0) {
            val last = messageCount - 1 + (if (locked) 1 else 0) + (if (sending) 1 else 0)
            if (reduceMotion) listState.scrollToItem(last) else listState.animateScrollToItem(last)
        }
    }

    ScreenScaffold(title = "AI Tutor", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            WorkspaceList(Modifier.weight(1f), state = listState) {
                if (locked) {
                    item("assessment-lock") {
                        Banner("The tutor is paused while a graded attempt is open. Submit the attempt to continue studying.", Tone.ATTENTION, Icons.Outlined.Lock)
                    }
                } else if (messageCount == 0) {
                    item("welcome") {
                        FeaturePanel(
                            title = moduleTitle?.takeIf(String::isNotBlank) ?: "Study with the tutor",
                            description = "Ask about course material and open the cited lessons to verify an answer.",
                            icon = Icons.Outlined.AutoAwesome,
                        )
                    }
                    item("suggestions") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            com.forensim.app.core.designsystem.SectionHeader("Try a question")
                            suggestions.forEach { question ->
                                AssistChip(onClick = { vm.send(question) }, label = { Text(question) },
                                    leadingIcon = { Icon(Icons.Outlined.School, contentDescription = null) },
                                    modifier = Modifier.heightIn(min = Spacing.touchTarget))
                            }
                        }
                    }
                }
                items(messageCount, key = { "message-$it" }) { index -> Bubble(vm.messages[index], openLesson) }
                if (sending) item("pending") {
                    Row(Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (!reduceMotion) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Finding relevant lessons…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = input, onValueChange = { if (it.length <= 1500) input = it },
                            label = { Text("Ask about your lessons") },
                            enabled = !locked,
                            maxLines = 4,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f),
                        )
                        FilledIconButton(
                            onClick = { vm.send(input); input = "" },
                            enabled = input.isNotBlank() && !sending && !locked,
                            modifier = Modifier.size(Spacing.touchTarget),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send question") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, openLesson: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = when {
                m.fromUser -> MaterialTheme.colorScheme.primaryContainer
                m.notice -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            shape = RoundedCornerShape(16.dp, 16.dp, if (m.fromUser) 4.dp else 16.dp, if (m.fromUser) 16.dp else 4.dp),
            modifier = Modifier.widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(if (m.fromUser) "You" else if (m.notice) "Tutor notice" else "AI Tutor",
                    style = MaterialTheme.typography.labelMedium)
                Text(m.text, style = MaterialTheme.typography.bodyLarge)
                if (m.references.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Sources", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    m.references.forEach { r ->
                        AssistChip(onClick = { openLesson(r.lessonId) }, label = { Text(r.title) },
                            modifier = Modifier.heightIn(min = Spacing.touchTarget))
                    }
                }
            }
        }
    }
}

/** Entry point to the AI assistant, shown on the Home screen of students and professors. */
@Composable
fun AssistantCard(onClick: () -> Unit, subtitle: String = stringResource(R.string.assistant_default_sub)) {
    com.forensim.app.core.designsystem.ForensimCard(onClick = onClick) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.forensim.app.core.designsystem.Spacing.md),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            com.forensim.app.core.designsystem.IconTile(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, size = 44.dp)
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_assistant), style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null,
                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
