package com.forensim.app.feature.professor

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.Avatar
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.model.UserAccount
import com.forensim.app.feature.settings.SignOutDialog
import com.forensim.app.core.designsystem.Chevron
import com.forensim.app.core.designsystem.ListDivider
import com.forensim.app.core.designsystem.ListGroup
import com.forensim.app.core.designsystem.ListRow
import com.forensim.app.core.designsystem.RowValue
import com.forensim.app.feature.student.LogoutGroup
import com.forensim.app.feature.student.PerformanceError
import com.forensim.app.feature.student.PerformanceLoading
import com.forensim.app.core.designsystem.StatCardRow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined._3dRotation
import com.forensim.app.feature.student.ProfileHeader

/** Professor "Profile" tab: identity, student performance across their classes, and account cards. */
@Composable
internal fun ProfessorProfileHub(
    user: UserAccount,
    vm: ProfessorViewModel,
    openClasses: () -> Unit,
    openAssistant: () -> Unit,
    openSettings: () -> Unit,
    openControls: () -> Unit,
    openReports: () -> Unit,
) {
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val data = (vm.state as? Load.Ready)?.data

    ScreenScaffold(title = stringResource(R.string.title_profile)) { padding ->
        WorkspaceList(Modifier.padding(padding)) {
            item("identity") { ProfileHeader(user) }
            item("performance") {
                SectionHeader(stringResource(R.string.student_performance))
                StudentPerformanceCard(vm.state, onRetry = vm::load)
            }
            item("account") {
                SectionHeader(stringResource(R.string.section_account))
                ListGroup {
                    ListRow(stringResource(R.string.my_classes), icon = Icons.Outlined.Groups, onClick = openClasses, trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            data?.classes?.size?.let { RowValue(it.toString()) }
                            Chevron()
                        }
                    })
                    ListDivider()
                    ListRow(stringResource(R.string.ai_assistant), icon = Icons.Outlined.AutoAwesome, onClick = openAssistant)
                    ListDivider()
                    ListRow(stringResource(R.string.reported_content), icon = Icons.Outlined.Flag, onClick = openReports)
                    ListDivider()
                    ListRow(stringResource(R.string.settings), icon = Icons.Outlined.Settings, onClick = openSettings)
                    ListDivider()
                    ListRow(stringResource(R.string.controls_accessibility), icon = Icons.Outlined.SportsEsports, onClick = openControls)
                }
            }
            item("logout") { LogoutGroup { confirmLogout = true } }
        }
    }
    if (confirmLogout) SignOutDialog(onDismiss = { confirmLogout = false })
}

@Composable
private fun StudentPerformanceCard(state: Load<TeachingData>, onRetry: () -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    when (state) {
        Load.Loading -> PerformanceLoading()
        is Load.Error -> PerformanceError(state.message, onRetry)
        is Load.Ready -> {
            val d = state.data
            val submitted = d.quizAttempts.filter { it.status == "submitted" }
            val scored = d.simAttempts.filter { it.status == "scored" }
            val ownLessons = d.lessons.filter { l -> d.ownModules.any { it.id == l.moduleId } }.map { it.id }.toSet()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCardRow(
                    Triple(Icons.Outlined.Groups, d.students.size.toString(), stringResource(R.string.students)),
                    Triple(Icons.Outlined.Quiz, avgPct(submitted.map { it.score to it.maxScore }), stringResource(R.string.quiz_average)),
                    Triple(Icons.Outlined._3dRotation, avgPct(scored.map { it.score to it.maxScore }), stringResource(R.string.case_average)),
                )
                if (d.students.isEmpty()) {
                    ForensimCard {
                        Text(stringResource(R.string.no_students_yet),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }
                val possible = ownLessons.size * d.students.size
                val done = d.progress.count { it.lessonId in ownLessons }
                ForensimCard {
                    ProgressRow(stringResource(R.string.lesson_completion), if (possible == 0) 0f else done / possible.toFloat(),
                        if (possible == 0) "—" else "${done * 100 / possible}%")
                }
                val rows = d.students.map { id ->
                    val lessonsDone = d.progress.count { it.studentId == id && it.lessonId in ownLessons }
                    val quiz = avgPct(submitted.filter { it.studentId == id }.map { it.score to it.maxScore })
                    Triple(id, lessonsDone, quiz)
                }.sortedBy { d.name(it.first).lowercase() }
                ListGroup {
                    (if (showAll) rows else rows.take(5)).forEachIndexed { i, (id, lessonsDone, quiz) ->
                        if (i > 0) ListDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Avatar(d.name(id), size = 36.dp)
                            Column(Modifier.weight(1f)) {
                                Text(d.name(id), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.lessons_count, lessonsDone, ownLessons.size), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RowValue(quiz, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (rows.size > 5) {
                        ListDivider(inset = 0.dp)
                        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showAll) stringResource(R.string.show_fewer) else stringResource(R.string.show_all_students, rows.size))
                        }
                    }
                }
            }
        }
    }
}
