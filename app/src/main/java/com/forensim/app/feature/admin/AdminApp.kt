package com.forensim.app.feature.admin

import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.WorkspaceDestination
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.WorkspaceScaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.model.AdminUser
import com.forensim.app.core.model.AuditEntry
import com.forensim.app.core.model.Course
import com.forensim.app.core.model.CourseClass
import com.forensim.app.core.model.Role
import com.forensim.app.core.model.SystemSetting
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.feature.settings.ProfileScreen
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AdminData(
    val users: List<AdminUser>,
    val courses: List<Course>,
    val classes: List<CourseClass>,
    val stats: Map<String, Long>,
    val audit: List<AuditEntry>,
    val settings: List<SystemSetting>,
)

class AdminViewModel(private val c: AppContainer) : ViewModel() {
    var state by mutableStateOf<Load<AdminData>>(Load.Loading); private set
    var message by mutableStateOf<String?>(null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            state = try {
                val users = async { c.staff.adminUsers() }
                val courses = async { c.staff.courses() }
                val classes = async { c.staff.classes() }
                val stats = async { c.staff.platformStats() }
                val audit = async { c.staff.auditLog() }
                val settings = async { c.staff.settings() }
                Load.Ready(AdminData(users.await(), courses.await(), classes.await(), stats.await(), audit.await(), settings.await()))
            } catch (e: ApiException) {
                Load.Error(e.message)
            }
        }
    }

    fun setRole(u: AdminUser, role: Role, status: String) {
        viewModelScope.launch {
            message = try {
                c.staff.adminSetRole(u.userId, role, status); load(); "Updated ${u.fullName.ifBlank { u.email }}"
            } catch (e: ApiException) { e.message }
        }
    }

    var accountBusy by mutableStateOf(false); private set
    var accountError by mutableStateOf<String?>(null)

    fun createAccount(email: String, password: String, name: String, role: Role, onDone: () -> Unit) {
        if (accountBusy) return
        accountBusy = true; accountError = null
        viewModelScope.launch {
            try {
                c.staff.adminCreateUser(email, password, name, role)
                message = "Created ${role.label.lowercase()} account for ${email.trim()}"
                load(); onDone()
            } catch (e: ApiException) {
                accountError = e.message
            } finally {
                accountBusy = false
            }
        }
    }

    fun deleteAccount(u: AdminUser) {
        viewModelScope.launch {
            message = try {
                c.staff.adminDeleteUser(u.userId); load(); "Deleted ${u.fullName.ifBlank { u.email ?: "account" }}"
            } catch (e: ApiException) { e.message }
        }
    }

    fun saveSetting(key: String, value: kotlinx.serialization.json.JsonElement) {
        viewModelScope.launch {
            message = try { c.staff.updateSetting(key, value); load(); "Setting saved" } catch (e: ApiException) { e.message }
        }
    }
}

private enum class AdminTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    USERS("Users", Icons.Outlined.ManageAccounts), COURSES("Courses", Icons.Outlined.School), REPORTS("Reports", Icons.Outlined.Assessment),
    AUDIT("Audit", Icons.Outlined.History), SETTINGS("Settings", Icons.Outlined.Settings)
}

@Composable
fun AdminApp(user: UserAccount) {
    val vm = appViewModel { AdminViewModel(it) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Triple<AdminUser, Role, String>?>(null) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var userQuery by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AdminUser?>(null) }
    val current = AdminTab.entries[tab]
    val tabStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    androidx.activity.compose.BackHandler(showProfile) { showProfile = false }
    WorkspaceScaffold(
        destinations = AdminTab.entries.mapIndexed { index, destination ->
            val compactLabel = when (destination) {
                AdminTab.REPORTS -> "Stats"
                AdminTab.SETTINGS -> "Setup"
                else -> destination.label
            }
            WorkspaceDestination(destination.label, destination.icon, selected = index == tab, compactLabel = compactLabel) {
                tab = index
                showProfile = false
            }
        },
        showNavigation = !showProfile,
    ) {
        if (showProfile) {
            ProfileScreen(user, onBack = { showProfile = false })
        } else tabStates.SaveableStateProvider(current.name) {
            ScreenScaffold(title = "Administration") { inner ->
                LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
                    WorkspaceList(Modifier.padding(inner)) {
                        item("overview") {
                            val description = when (current) {
                                AdminTab.USERS -> "Find accounts, review access and manage roles."
                                AdminTab.COURSES -> "Review courses, class join codes and assigned professors."
                                AdminTab.REPORTS -> "Track activity across classes, assessments and the AI tutor."
                                AdminTab.AUDIT -> "Review recorded changes made by administrators."
                                AdminTab.SETTINGS -> "Manage the AI tutor, system notice and your account."
                            }
                            PageIntro(current.label, description = description)
                        }
                        vm.message?.let { m -> item("feedback") { Banner(m, Tone.PRIMARY) } }
                        when (current) {
                            AdminTab.USERS -> {
                                item("user-add") {
                                    PrimaryButton("Add account", onClick = { vm.accountError = null; creating = true },
                                        modifier = Modifier.fillMaxWidth())
                                }
                                item("user-search") {
                                    OutlinedTextField(
                                        value = userQuery, onValueChange = { userQuery = it },
                                        label = { Text("Search by name or email") }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                val query = userQuery.trim()
                                val users = d.users.filter { it.fullName.contains(query, ignoreCase = true) ||
                                    it.email.orEmpty().contains(query, ignoreCase = true) }
                                if (users.isEmpty()) item("empty-users") { Banner("No matching accounts.", Tone.NEUTRAL) }
                                items(users, key = { it.userId }) { u ->
                                    ForensimCard {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(u.fullName.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleSmall)
                                                Text(u.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            StatusChip(if (u.status == "active") "Active" else "Suspended", if (u.status == "active") Tone.SUCCESS else Tone.ERROR)
                                        }
                                        Spacer(Modifier.height(Spacing.sm))
                                        androidx.compose.foundation.layout.FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Role.entries.forEach { r ->
                                                FilterChip(selected = u.role.equals(r.name, true), onClick = {
                                                    if (!u.role.equals(r.name, true)) pending = Triple(u, r, u.status)
                                                }, label = { Text(r.label) }, enabled = u.userId != user.id)
                                            }
                                            if (u.userId != user.id) {
                                                TextButton(onClick = { pending = Triple(u, Role.from(u.role) ?: Role.STUDENT, if (u.status == "active") "suspended" else "active") }) {
                                                    Text(if (u.status == "active") "Suspend" else "Reactivate")
                                                }
                                                TextButton(onClick = { deleting = u },
                                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                                    Text("Delete")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            AdminTab.COURSES -> {
                                items(d.courses, key = { it.id }) { course ->
                                    ForensimCard {
                                        Text(course.title, style = MaterialTheme.typography.titleMedium)
                                        Text(course.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(Spacing.sm))
                                        d.classes.filter { it.courseId == course.id }.forEach { k ->
                                            val prof = d.users.firstOrNull { it.userId == k.professorId }
                                            Text("${k.name} · ${k.term ?: ""} · code ${k.joinCode} · ${prof?.fullName ?: "no professor assigned"}",
                                                style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                            AdminTab.REPORTS -> {
                                val stats = d.stats
                                item("people") {
                                    SectionHeader("Accounts")
                                    DashboardStats("Students" to "${stats["students"] ?: 0}",
                                        "Professors" to "${stats["professors"] ?: 0}", "Admins" to "${stats["admins"] ?: 0}")
                                }
                                item("course-activity") {
                                    SectionHeader("Course activity")
                                    DashboardStats("Classes" to "${stats["classes"] ?: 0}",
                                        "Published modules" to "${stats["published_modules"] ?: 0}",
                                        "Quiz attempts" to "${stats["quiz_attempts"] ?: 0}",
                                        "Simulation runs" to "${stats["simulation_attempts"] ?: 0}")
                                }
                                item("tutor-activity") {
                                    SectionHeader("AI tutor · last 7 days")
                                    DashboardStats("Requests" to "${stats["ai_requests_7d"] ?: 0}",
                                        "Blocked" to "${stats["ai_blocked_7d"] ?: 0}")
                                }
                            }
                            AdminTab.AUDIT -> {
                                if (d.audit.isEmpty()) item { Text("No privileged actions recorded yet.", style = MaterialTheme.typography.bodyMedium) }
                                items(d.audit, key = { it.id }) { a ->
                                    ForensimCard {
                                        Text(a.action, style = MaterialTheme.typography.titleSmall)
                                        Text("${a.objectType} ${a.objectId?.take(8) ?: ""} · by ${d.users.firstOrNull { it.userId == a.actorId }?.fullName ?: "system"}",
                                            style = MaterialTheme.typography.bodySmall)
                                        Text(fmt(a.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        a.details?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                            AdminTab.SETTINGS -> item { SettingsPanel(d, vm, onProfile = { showProfile = true }) }
                        }
                    }
                }
            }
        }
    }
    if (creating) CreateAccountDialog(vm, onDismiss = { creating = false })
    deleting?.let { u ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete account?") },
            text = { Text("${u.fullName.ifBlank { u.email ?: "This account" }} and all of their progress, quiz attempts and case results will be permanently deleted. This cannot be undone. To keep their history, suspend the account instead.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAccount(u); deleting = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    pending?.let { (u, role, status) ->
        val action = when {
            status == u.status -> "Change role"
            status == "suspended" -> "Suspend account"
            else -> "Reactivate account"
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(action) },
            text = { Text("${u.fullName.ifBlank { u.email ?: "This account" }} will be ${status.lowercase()} with the ${role.label} role. The change is recorded in the audit log.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.setRole(u, role, status); pending = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = if (status != u.status && status == "suspended") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface),
                ) { Text(action) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsPanel(d: AdminData, vm: AdminViewModel, onProfile: () -> Unit) {
    fun value(key: String) = (d.settings.firstOrNull { it.key == key }?.value as? JsonPrimitive)?.contentOrNull ?: ""
    var limit by rememberSaveable(d) { mutableStateOf(value("ai_daily_limit")) }
    var notice by rememberSaveable(d) { mutableStateOf(value("system_notice")) }
    Column {
        SectionHeader("AI tutor")
        OutlinedTextField(value = limit, onValueChange = { limit = it.filter(Char::isDigit).take(4) }, label = { Text("Daily questions per user") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("Provider keys are configured as server secrets in Supabase, never in the app.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Spacing.xs))
        PrimaryButton("Save limit", onClick = { limit.toIntOrNull()?.let { vm.saveSetting("ai_daily_limit", JsonPrimitive(it)) } }, enabled = limit.toIntOrNull() != null)
        SectionHeader("System notice")
        OutlinedTextField(value = notice, onValueChange = { notice = it.take(280) }, label = { Text("Shown on every student's home screen") },
            modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(Spacing.xs))
        PrimaryButton("Publish notice", onClick = { vm.saveSetting("system_notice", JsonPrimitive(notice)) })
        SectionHeader("Account")
        TextButton(onClick = onProfile) { Text("Profile, appearance and sign out") }
    }
}

private fun fmt(iso: String) = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))
}.getOrDefault(iso)

@Composable
private fun CreateAccountDialog(vm: AdminViewModel, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(Role.STUDENT) }
    val emailOk = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""").matches(email.trim())
    val passwordOk = com.forensim.app.core.auth.PasswordPolicy.isValid(password)
    AlertDialog(
        onDismissRequest = { if (!vm.accountBusy) onDismiss() },
        title = { Text("Add account") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Temporary password") }, singleLine = true,
                    isError = password.isNotEmpty() && !passwordOk,
                    supportingText = { Text("10+ characters with upper and lower case, a number and a symbol. Share it privately; they can change it after signing in.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.xs))
                Text("Role", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Role.entries.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }) }
                }
                vm.accountError?.let { Spacer(Modifier.height(Spacing.xs)); Banner(it, Tone.ERROR) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.createAccount(email, password, name, role, onDone = onDismiss) },
                enabled = !vm.accountBusy && name.trim().length >= 2 && emailOk && passwordOk,
            ) { Text(if (vm.accountBusy) "Creating…" else "Create account") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.accountBusy) { Text("Cancel") } },
    )
}
