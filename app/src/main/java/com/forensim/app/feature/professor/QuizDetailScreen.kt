package com.forensim.app.feature.professor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.outOf
import com.forensim.app.core.model.QuizAttempt
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One answer-visibility choice: server key, icon, title and a plain explanation. */
private data class PolicyOption(val key: String, val icon: ImageVector, val title: String, val hint: String)

private val policyOptions = listOf(
    PolicyOption("after_submit", Icons.Outlined.Visibility, "Right after submitting",
        "Each student sees the correct answers as soon as they submit."),
    PolicyOption("after_close", Icons.Outlined.Schedule, "When the quiz closes",
        "Correct answers appear for everyone once the quiz's time window ends."),
    PolicyOption("never", Icons.Outlined.VisibilityOff, "Never",
        "Students only see their score, never the correct answers."),
)

/** Full-width, equal-height radio rows for "Show answers". */
@Composable
private fun AnswerVisibilityPicker(selected: String, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        policyOptions.forEach { o ->
            val on = o.key == selected
            val shape = MaterialTheme.shapes.medium
            // Smooth hand-over between options instead of an instant jump.
            val spec = tween<Color>(220)
            val container by animateColorAsState(if (on) cs.primaryContainer.copy(alpha = 0.5f) else cs.surfaceContainer, spec, label = "policyBg")
            val outline by animateColorAsState(if (on) cs.primary else cs.outlineVariant, spec, label = "policyBorder")
            val iconBg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, spec, label = "policyIconBg")
            val iconFg by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, spec, label = "policyIcon")
            val borderWidth by animateDpAsState(if (on) 2.dp else 1.dp, tween(220), label = "policyBorderWidth")
            Surface(
                shape = shape,
                color = container,
                border = BorderStroke(borderWidth, outline),
                // Clip before the click so the press highlight follows the rounded corners.
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { if (!on) onSelect(o.key) }),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        Modifier.size(40.dp).background(iconBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(o.icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                        Text(o.hint, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    RadioButton(selected = on, onClick = null)
                }
            }
        }
    }
}

/** One of the professor's class quizzes: who submitted, their scores, and answer visibility. */
@Composable
internal fun ProfessorQuizDetail(vm: ProfessorViewModel, quizId: String, onBack: () -> Unit) {
    LaunchedEffect(vm.message) {
        if (vm.message != null) { kotlinx.coroutines.delay(3000); vm.message = null }
    }
    val title = (vm.state as? Load.Ready)?.data?.quizzes?.firstOrNull { it.id == quizId }?.title ?: "Quiz"
    ScreenScaffold(title = "Quiz", onBack = onBack) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            val q = d.quizzes.firstOrNull { it.id == quizId }
            if (q == null) {
                Banner("This quiz was deleted.", Tone.ATTENTION, modifier = Modifier.padding(inner).padding(Spacing.md))
                return@LoadContent
            }
            val k = q.classId?.let { d.classById(it) }
            val roster = k?.let { d.activeRoster(it) }.orEmpty()
            val submitted = d.quizAttempts.filter { it.quizId == q.id && it.status == "submitted" }
            // Latest submission per student, newest first.
            val byStudent = submitted.groupBy { it.studentId }
            val latest = byStudent.mapValues { (_, list) -> list.maxBy { it.attemptNumber } }
                .values.sortedByDescending { it.submittedAt ?: "" }
            val waiting = roster.filter { it.studentId !in byStudent }

            WorkspaceList(Modifier.padding(inner)) {
                item("header") {
                    ForensimCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Text(if (q.isGraded) "Graded" else "Practice", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = q.published, onCheckedChange = { vm.setQuizPublished(q, it) },
                                modifier = Modifier.semantics { contentDescription = "Quiz open to students" })
                        }
                        Text(if (q.published) "Open to students" else "Hidden from students", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                vm.message?.let { msg -> item("feedback") { Spacer(Modifier.height(Spacing.sm)); Banner(msg, Tone.PRIMARY) } }
                item("stats") {
                    Spacer(Modifier.height(Spacing.sm))
                    DashboardStats(
                        "Submitted" to if (roster.isEmpty()) latest.size.toString() else "${latest.size}/${roster.size}",
                        "Average" to avgPct(latest.map { it.score to it.maxScore }).replace("-", "—"),
                    )
                }

                if (q.isGraded) item("policy") {
                    SectionHeader("Show answers")
                    Text("When can students see the correct answers for this graded quiz?",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm))
                    AnswerVisibilityPicker(q.reviewPolicy) { vm.setPolicy(q, it) }
                }

                item("results-header") {
                    SectionHeader("Results (${latest.size})", trailing = if (latest.isEmpty() && roster.isEmpty()) null else {
                        { DownloadScoresButton(vm, title, latest, byStudent, waiting.map { it.studentId }, d::name) }
                    })
                }
                item("results") {
                    if (latest.isEmpty()) {
                        Text("No one has submitted this quiz yet.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else ForensimCard {
                        latest.forEachIndexed { i, a ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ResultRow(d.name(a.studentId), a, attempts = byStudent[a.studentId]?.size ?: 1)
                        }
                    }
                }

                if (waiting.isNotEmpty()) {
                    item("waiting-header") { SectionHeader("Not submitted (${waiting.size})") }
                    item("waiting") {
                        ForensimCard {
                            waiting.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(d.name(e.studentId), style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm))
                            }
                        }
                    }
                }
                item("bottom-space") { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

/**
 * Saves the quiz's score list as a CSV file (opens in Excel / Google Sheets): every student in the
 * class, alphabetically, with score, percent, attempts and when they submitted. Students who
 * haven't submitted are included as "Not submitted" so the list is complete.
 */
@Composable
private fun DownloadScoresButton(
    vm: ProfessorViewModel, quizTitle: String, latest: List<QuizAttempt>, byStudent: Map<String, List<QuizAttempt>>,
    waiting: List<String>, name: (String) -> String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val csv = androidx.compose.runtime.remember(latest, waiting) { scoresCsv(latest, byStudent, waiting, name) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.message = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                // Byte-order mark so Excel reads accented names correctly.
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(csv.toByteArray(Charsets.UTF_8))
            } ?: error("no stream")
            "Scores downloaded."
        }.getOrElse { "Couldn't save the file. Try again." }
    }
    com.forensim.app.core.designsystem.TextButton(onClick = {
        val safe = quizTitle.replace(Regex("[\\/:*?\"<>|]"), " ").trim().ifEmpty { "Quiz" }
        save.launch("$safe - scores.csv")
    }) {
        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.padding(start = Spacing.xs))
        Text("Download")
    }
}

private fun scoresCsv(latest: List<QuizAttempt>, byStudent: Map<String, List<QuizAttempt>>, waiting: List<String>, name: (String) -> String): String {
    fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    fun num(d: Double?) = d?.let { if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(java.util.Locale.US, it) } ?: ""
    val rows = latest.map { a ->
        val pct = if (a.score != null && (a.maxScore ?: 0.0) > 0) "%.1f%%".format(java.util.Locale.US, a.score / a.maxScore!! * 100) else ""
        listOf(name(a.studentId), if (a.late) "Late" else "Submitted", num(a.score), num(a.maxScore), pct,
            byStudent[a.studentId]?.size ?: 1, a.submittedAt?.let(::submittedLabel) ?: "")
    } + waiting.map { listOf(name(it), "Not submitted", "", "", "", 0, "") }
    return buildString {
        appendLine("No.,Student,Status,Score,Max score,Percent,Attempts,Submitted")
        rows.sortedBy { (it[0] as String).lowercase() }.forEachIndexed { i, r ->
            appendLine((listOf(i + 1) + r).joinToString(",") { cell(it) })
        }
    }
}

@Composable
private fun ResultRow(name: String, a: QuizAttempt, attempts: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    a.submittedAt?.let(::submittedLabel),
                    if (attempts > 1) "Attempt ${a.attemptNumber} of $attempts" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (a.late) {
            StatusChip("Late", Tone.ATTENTION)
            Spacer(Modifier.padding(start = Spacing.xs))
        }
        Text(a.score?.outOf(a.maxScore) ?: "—", style = MaterialTheme.typography.titleSmall)
    }
}

private fun submittedLabel(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
}.getOrNull()
