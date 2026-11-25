package com.forensim.app.feature.student

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
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined._3dRotation
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.ListDivider
import com.forensim.app.core.designsystem.ListGroup
import com.forensim.app.core.designsystem.ListRow
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.PresenceAvatar
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.RowValue
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.StatCardRow
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.designsystem.rememberIsOnline
import com.forensim.app.core.model.UserAccount
import com.forensim.app.feature.settings.SignOutDialog
import com.forensim.app.ui.appViewModel

/** Student "Profile" tab: identity, performance summary and account rows. */
@Composable
fun ProfileHubScreen(
    user: UserAccount,
    openClasses: () -> Unit,
    openSettings: () -> Unit,
    openControls: () -> Unit,
) {
    val vm = appViewModel(key = "profile-hub") { StudentDashboardViewModel(it) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val dashboard = (vm.state as? Load.Ready)?.data

    ScreenScaffold(title = stringResource(R.string.title_profile)) { padding ->
        WorkspaceList(Modifier.padding(padding)) {
            item("identity") { ProfileHeader(user) }
            item("performance") {
                SectionHeader(stringResource(R.string.student_performance))
                StudentPerformance(vm.state, onRetry = vm::load)
            }
            item("account") {
                SectionHeader(stringResource(R.string.section_account))
                ListGroup {
                    ListRow(stringResource(R.string.my_classes), icon = Icons.Outlined.School, onClick = openClasses, trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            dashboard?.classes?.size?.let { RowValue(it.toString()) }
                            com.forensim.app.core.designsystem.Chevron()
                        }
                    })
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

/** Large centred avatar with online dot, name, e-mail and role. Shared by every role's profile. */
@Composable
internal fun ProfileHeader(user: UserAccount) {
    val name = user.fullName.ifBlank { user.email }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        val photo by com.forensim.app.ui.LocalAppContainer.current.profile.avatar.collectAsStateWithLifecycle()
        PresenceAvatar(name, size = 96.dp, online = rememberIsOnline(), photo = photo)
        Spacer(Modifier.height(Spacing.md))
        Text(user.fullName.ifBlank { user.role.label }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        StatusChip(stringResource(when (user.role) {
            com.forensim.app.core.model.Role.STUDENT -> R.string.role_student
            com.forensim.app.core.model.Role.PROFESSOR -> R.string.role_professor
            com.forensim.app.core.model.Role.ADMIN -> R.string.role_admin
        }), Tone.PRIMARY)
    }
}

@Composable
internal fun LogoutGroup(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(Spacing.lg))
    ListGroup {
        ListRow(stringResource(R.string.log_out), icon = Icons.AutoMirrored.Outlined.Logout, iconContainer = cs.errorContainer, iconContent = cs.onErrorContainer,
            titleColor = cs.error, onClick = onClick, trailing = null)
    }
}

@Composable
private fun StudentPerformance(state: Load<StudentDashboard>, onRetry: () -> Unit) {
    when (state) {
        Load.Loading -> PerformanceLoading()
        is Load.Error -> PerformanceError(state.message, onRetry)
        is Load.Ready -> {
            val d = state.data
            val total = d.lessons.size
            val done = d.lessons.count { it.id in d.completed }
            val graded = d.attempts.filter { it.status == "submitted" && it.score != null }
            val quizAvg = if (graded.isEmpty()) "—" else graded.sumOf { it.score!! }.pct(graded.sumOf { it.maxScore ?: 0.0 })
            val cases = d.simAttempts.filter { it.status == "scored" && it.score != null }
            val caseAvg = if (cases.isEmpty()) "—" else cases.sumOf { it.score!! }.pct(cases.sumOf { it.maxScore ?: 0.0 })
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatCardRow(
                    Triple(Icons.AutoMirrored.Outlined.MenuBook, "$done/$total", stringResource(R.string.lessons)),
                    Triple(Icons.Outlined.Quiz, quizAvg, stringResource(R.string.quiz_average)),
                    Triple(Icons.Outlined._3dRotation, caseAvg, stringResource(R.string.case_average)),
                )
                ForensimCard {
                    ProgressRow(stringResource(R.string.course_progress), if (total == 0) 0f else done / total.toFloat(), if (total == 0) "—" else "${done * 100 / total}%")
                }
            }
        }
    }
}

@Composable
internal fun PerformanceLoading() {
    ForensimCard {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
internal fun PerformanceError(message: String, onRetry: () -> Unit) {
    ForensimCard {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
internal fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = com.forensim.app.core.designsystem.TabularNumbers),
            textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
