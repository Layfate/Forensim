package com.forensim.app.feature.settings
import androidx.compose.ui.res.stringResource
import com.forensim.app.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.forensim.app.core.data.AppLanguage
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.forensim.app.core.designsystem.ThemePreference
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forensim.app.core.data.AppSettings
import com.forensim.app.core.data.ControlSettings
import com.forensim.app.core.data.GraphicsQuality
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.ForensimMark
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ListGroup
import com.forensim.app.core.designsystem.ListRow
import com.forensim.app.core.designsystem.PresenceAvatar
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.ThemePicker
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.rememberIsOnline
import com.forensim.app.core.model.UserAccount
import com.forensim.app.engine.controls.GyroController
import com.forensim.app.ui.LocalAppContainer
import kotlinx.coroutines.launch

/** Admin profile tab: the settings menu plus sign-out. */
@Composable
fun ProfileScreen(user: UserAccount, onBack: (() -> Unit)?, openControls: (() -> Unit)? = null, openAccount: (() -> Unit)? = null) {
    var confirm by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    section?.let { s ->
        SettingsSectionScreen(s, onBack = { section = null })
        return
    }
    ScreenScaffold(title = stringResource(R.string.title_settings), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                SettingsMenu(user, openAccount, openControls, onOpen = { section = it })
                Spacer(Modifier.height(Spacing.lg))
                OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.log_out))
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
    if (confirm) SignOutDialog(onDismiss = { confirm = false })
}

/**
 * Settings page opened from the Profile tab: a short menu of card buttons. Each card opens its own
 * page with the options inside (Appearance, Video and performance, Sound, About).
 */
@Composable
fun SettingsScreen(user: UserAccount, onBack: () -> Unit, openAccount: () -> Unit, openControls: (() -> Unit)? = null) {
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    section?.let { s ->
        SettingsSectionScreen(s, onBack = { section = null })
        return
    }
    ScreenScaffold(title = stringResource(R.string.title_settings), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                SettingsMenu(user, openAccount, openControls, onOpen = { section = it })
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

enum class SettingsSection(@androidx.annotation.StringRes val title: Int) {
    APPEARANCE(R.string.appearance), NOTIFICATIONS(R.string.notifications), FONT(R.string.font), LANGUAGE(R.string.language),
    VIDEO(R.string.video_performance), SOUND(R.string.sound), ADVANCED(R.string.advanced),
    ABOUT(R.string.about), HELP(R.string.help)
}

// ----------------------------------------------------------------------------- menu

@Composable
private fun SettingsMenu(
    user: UserAccount,
    openAccount: (() -> Unit)?,
    openControls: (() -> Unit)?,
    onOpen: (SettingsSection) -> Unit,
) {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val controls = settings.controls
    val sound = settings.sound

    if (openAccount != null) {
        SectionHeader(stringResource(R.string.section_account))
        AccountCard(user, openAccount)
    }
    SectionHeader(stringResource(R.string.section_preferences))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val themeSummary = stringResource(when (settings.theme) {
            ThemePreference.SYSTEM -> R.string.theme_system_summary
            ThemePreference.DARK -> R.string.theme_dark_summary
            ThemePreference.LIGHT -> R.string.theme_light_summary
        }) + if (settings.reducedMotion) stringResource(R.string.reduced_motion_suffix) else ""
        MenuCard(Icons.Outlined.Palette, stringResource(R.string.appearance), themeSummary) { onOpen(SettingsSection.APPEARANCE) }
        val notificationsOn = rememberNotificationsEnabled()
        val n = settings.notifications
        val notificationSummary = if (notificationsOn)
            stringResource(R.string.notifications_on_summary, listOf(n.coursework, n.grades, n.announcements, n.reminders).count { it })
            else stringResource(R.string.notifications_off_summary)
        MenuCard(Icons.Outlined.Notifications, stringResource(R.string.notifications), notificationSummary) { onOpen(SettingsSection.NOTIFICATIONS) }
        MenuCard(Icons.Outlined.Speed, stringResource(R.string.video_performance),
            stringResource(R.string.video_summary, stringResource(qualityLabel(controls.graphics)), controls.fieldOfView.toInt())) {
            onOpen(SettingsSection.VIDEO)
        }
        MenuCard(Icons.Outlined.SettingsSuggest, stringResource(R.string.advanced), stringResource(R.string.advanced_summary)) {
            onOpen(SettingsSection.ADVANCED)
        }
        val soundSummary = if (sound.effects) stringResource(R.string.sound_on_summary, (sound.volume * 100).toInt()) +
            (if (sound.vibration) stringResource(R.string.vibration_suffix) else "") else stringResource(R.string.sound_off_summary)
        MenuCard(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.sound), soundSummary) { onOpen(SettingsSection.SOUND) }
        if (openControls != null) {
            MenuCard(Icons.Outlined.SportsEsports, stringResource(R.string.controls_accessibility), stringResource(R.string.controls_summary), onClick = openControls)
        }
        MenuCard(Icons.Outlined.TextFields, stringResource(R.string.font), settings.font.label) { onOpen(SettingsSection.FONT) }
        MenuCard(Icons.Outlined.Language, stringResource(R.string.language), AppLanguage.current().nativeName) { onOpen(SettingsSection.LANGUAGE) }
    }
    SectionHeader(stringResource(R.string.section_support))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        MenuCard(Icons.Outlined.Info, stringResource(R.string.about_forensim), stringResource(R.string.version, com.forensim.app.BuildConfig.VERSION_NAME)) {
            onOpen(SettingsSection.ABOUT)
        }
        MenuCard(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.help), stringResource(R.string.help_summary)) {
            onOpen(SettingsSection.HELP)
        }
    }
}

/** One settings category as a tappable card: icon, title, current value, chevron. */
@Composable
private fun MenuCard(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ForensimCard(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(icon, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            com.forensim.app.core.designsystem.Chevron()
        }
    }
}

@Composable
private fun AccountCard(user: UserAccount, openAccount: () -> Unit) {
    val c = LocalAppContainer.current
    val photo by c.profile.avatar.collectAsStateWithLifecycle()
    val profile by c.profile.profile.collectAsStateWithLifecycle()
    ForensimCard(onClick = openAccount) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PresenceAvatar(user.fullName.ifBlank { user.email }, size = 56.dp, online = rememberIsOnline(), photo = photo)
            Column(Modifier.weight(1f)) {
                Text(profile?.fullName?.ifBlank { null } ?: user.fullName, style = MaterialTheme.typography.titleMedium)
                Text(profile?.email ?: user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(if (user.role == com.forensim.app.core.model.Role.STUDENT) R.string.account_summary_student else R.string.account_summary),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.forensim.app.core.designsystem.Chevron()
        }
    }
}

// ----------------------------------------------------------------------------- section pages

@Composable
internal fun SettingsSectionScreen(section: SettingsSection, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    ScreenScaffold(title = stringResource(section.title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                Spacer(Modifier.height(Spacing.sm))
                when (section) {
                    SettingsSection.APPEARANCE -> AppearanceSettings()
                    SettingsSection.NOTIFICATIONS -> NotificationSettingsPage()
                    SettingsSection.FONT -> FontSettings()
                    SettingsSection.LANGUAGE -> LanguageSettings()
                    SettingsSection.VIDEO -> VideoSettings()
                    SettingsSection.SOUND -> SoundSettings()
                    SettingsSection.ADVANCED -> AdvancedSettings()
                    SettingsSection.ABOUT -> AboutSettings()
                    SettingsSection.HELP -> HelpSettings()
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun AppearanceSettings() {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    Spacer(Modifier.height(Spacing.lg))
    ThemePicker(settings.theme, onSelect = { t -> scope.launch { c.settings.setTheme(t) } })
    Text(stringResource(R.string.theme_hint), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.lg))
    SectionHeader(stringResource(R.string.motion))
    SettingSwitchCard(stringResource(R.string.reduce_motion), stringResource(R.string.fewer_animations), settings.reducedMotion) { on -> scope.launch { c.settings.setReducedMotion(on) } }
}

/** System-level switch for this app's notifications, re-read whenever the screen resumes. */
@Composable
private fun rememberNotificationsEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun read() = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    var enabled by remember { mutableStateOf(read()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        enabled = read()
        onPauseOrDispose { }
    }
    return enabled
}

@Composable
private fun NotificationSettingsPage() {
    val c = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        enabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    fun openSystemSettings() {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    // Android 13+: ask in-app first; once the user has said no, only system settings can turn it on.
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> enabled = granted || androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(); if (!granted) openSystemSettings() }
    val cs = MaterialTheme.colorScheme
    val fx = com.forensim.app.core.designsystem.ForensimTheme.colors
    ForensimCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(if (enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff, size = 44.dp,
                container = if (enabled) fx.successContainer else cs.surfaceContainerHigh,
                content = if (enabled) fx.success else cs.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (enabled) R.string.notifications_allowed else R.string.notifications_blocked), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (enabled) R.string.notifications_allowed_body else R.string.notifications_blocked_body),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        if (enabled) {
            com.forensim.app.core.designsystem.OutlinedButton(onClick = ::openSystemSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.notifications_system_settings))
            }
        } else {
            PrimaryButton(stringResource(R.string.notifications_turn_on), onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 33) permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else openSystemSettings()
            }, modifier = Modifier.fillMaxWidth())
        }
    }
    SectionHeader(stringResource(R.string.notifications_what))
    val n = settings.notifications
    fun save(new: com.forensim.app.core.data.NotificationSettings) = scope.launch { c.settings.updateNotifications(new) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SettingSwitchCard(stringResource(R.string.notify_coursework), stringResource(R.string.notify_coursework_sub), n.coursework, enabled) { save(n.copy(coursework = it)) }
        SettingSwitchCard(stringResource(R.string.notify_grades), stringResource(R.string.notify_grades_sub), n.grades, enabled) { save(n.copy(grades = it)) }
        SettingSwitchCard(stringResource(R.string.notify_announcements), stringResource(R.string.notify_announcements_sub), n.announcements, enabled) { save(n.copy(announcements = it)) }
        SettingSwitchCard(stringResource(R.string.notify_reminders), stringResource(R.string.notify_reminders_sub), n.reminders, enabled) { save(n.copy(reminders = it)) }
        if (enabled && n.reminders) {
            com.forensim.app.core.designsystem.TextButton(onClick = { com.forensim.app.feature.student.StreakReminder.sendTest(context) }) {
                Text(stringResource(R.string.reminder_test))
            }
        }
    }
}

@Composable
private fun VideoSettings() {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val controls = settings.controls
    val scope = rememberCoroutineScope()
    fun save(new: ControlSettings) = scope.launch { c.settings.updateControls(new) }
    SectionHeader(stringResource(R.string.graphics_quality))
    ForensimCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GraphicsQuality.entries.forEach { quality ->
                FilterChip(selected = controls.graphics == quality, onClick = { save(controls.copy(graphics = quality)) },
                    label = { Text(stringResource(qualityLabel(quality))) }, modifier = Modifier.heightIn(min = Spacing.touchTarget))
            }
        }
        Text(
            stringResource(when (controls.graphics) {
                GraphicsQuality.LOW -> R.string.quality_low_desc
                GraphicsQuality.MEDIUM -> R.string.quality_medium_desc
                GraphicsQuality.HIGH -> R.string.quality_high_desc
            }),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionHeader(stringResource(R.string.camera))
    ForensimCard {
        SettingSlider(stringResource(R.string.field_of_view), controls.fieldOfView, range = 70f..110f, format = { "${it.toInt()}°" }) {
            save(controls.copy(fieldOfView = it))
        }
        Text(stringResource(R.string.fov_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SoundSettings() {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val sound = settings.sound
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val preview = remember { lazy { com.forensim.app.engine.audio.SoundFx(appContext) } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (preview.isInitialized()) preview.value.release() } }
    SectionHeader(stringResource(R.string.effects))
    ForensimCard {
        SettingSwitch(stringResource(R.string.sound_effects), stringResource(R.string.sound_effects_sub), sound.effects) { on ->
            scope.launch { c.settings.updateSound(sound.copy(effects = on)) }
        }
        if (sound.effects) {
            SettingSlider(stringResource(R.string.volume), sound.volume, range = 0f..1f, format = { "${(it * 100).toInt()}%" }) { v ->
                scope.launch { c.settings.updateSound(sound.copy(volume = v)) }
                preview.value.play(com.forensim.app.engine.audio.Sfx.SUCCESS, v)
            }
        }
    }
    SectionHeader(stringResource(R.string.haptics))
    SettingSwitchCard(stringResource(R.string.vibration), stringResource(R.string.vibration_sub), sound.vibration) { on ->
            scope.launch { c.settings.updateSound(sound.copy(vibration = on)) }
    }
}

@Composable
private fun AboutSettings() {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        ForensimMark(72.dp)
        Spacer(Modifier.height(Spacing.md))
        Text("Forensim", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        StatusChip(stringResource(R.string.version, com.forensim.app.BuildConfig.VERSION_NAME), Tone.NEUTRAL)
    }
    ForensimCard {
        Text(stringResource(R.string.about_disclaimer),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionHeader(stringResource(R.string.licences))
    ForensimCard {
        Text("Inter typeface", style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.inter_licence), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Terms of Use and Privacy Policy, also reachable from the sign-in screen (Play Store requirement).
    var legal by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<com.forensim.app.feature.legal.LegalDocument?>(null) }
    SectionHeader(stringResource(R.string.legal_section))
    com.forensim.app.core.designsystem.ListGroup {
        com.forensim.app.core.designsystem.ListRow(stringResource(R.string.terms_of_use), icon = Icons.Outlined.Description,
            onClick = { legal = com.forensim.app.feature.legal.LegalDocument.TERMS })
        com.forensim.app.core.designsystem.ListDivider()
        com.forensim.app.core.designsystem.ListRow(stringResource(R.string.privacy_policy), icon = Icons.Outlined.PrivacyTip,
            onClick = { legal = com.forensim.app.feature.legal.LegalDocument.PRIVACY })
    }
    legal?.let { doc ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { legal = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                com.forensim.app.feature.legal.LegalScreen(doc, onBack = { legal = null })
            }
        }
    }
}

/** Files in the app cache folder that are not offline lesson PDFs (safe to delete at any time). */
private fun tempFiles(context: android.content.Context): List<java.io.File> =
    context.cacheDir.listFiles()?.filter { it.name != "lesson-pdfs" }.orEmpty()

@Composable
private fun AdvancedSettings() {
    val c = LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var tempBytes by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmEverywhere by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var busyEverywhere by remember { mutableStateOf(false) }
    var ping by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    // Result message shown at the bottom: text plus whether it is an error.
    var done by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    suspend fun measureStorage() {
        sizeBytes = c.cache.offlineSizeBytes()
        tempBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            tempFiles(context).sumOf { f -> f.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { measureStorage() }
    fun fmt(bytes: Long?) = bytes?.let { android.text.format.Formatter.formatShortFileSize(context, it) } ?: "…"
    val buttonMod = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
    val sub = MaterialTheme.colorScheme.onSurfaceVariant

    // ---------------------------------------------------------------- offline study
    SectionHeader(stringResource(R.string.section_offline_study))
    ForensimCard {
        Text(stringResource(R.string.download_offline), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.download_offline_body), style = MaterialTheme.typography.bodySmall, color = sub)
        Spacer(Modifier.height(Spacing.sm))
        val savedMsg = R.string.download_offline_done
        OutlinedButton(onClick = {
            downloading = true; done = null
            scope.launch {
                done = try {
                    val n = c.learning.downloadForOffline()
                    measureStorage()
                    context.getString(savedMsg, n) to false
                } catch (e: Exception) {
                    (e.message ?: "Download failed.") to true
                }
                downloading = false
            }
        }, enabled = !downloading, modifier = buttonMod) {
            Text(stringResource(if (downloading) R.string.downloading else R.string.download_offline))
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    SettingSwitchCard(stringResource(R.string.ask_mobile_video), stringResource(R.string.ask_mobile_video_sub), settings.askBeforeMobileVideo) { on ->
            scope.launch { c.settings.setAskBeforeMobileVideo(on) }
    }

    // ---------------------------------------------------------------- storage
    SectionHeader(stringResource(R.string.storage))
    ForensimCard {
        Text(stringResource(R.string.offline_content), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.offline_content_size, fmt(sizeBytes)), style = MaterialTheme.typography.bodySmall, color = sub)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = { confirmClear = true }, enabled = (sizeBytes ?: 0L) > 0L, modifier = buttonMod) {
            Text(stringResource(R.string.clear_offline))
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    ForensimCard {
        Text(stringResource(R.string.temp_files), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.temp_files_size, fmt(tempBytes)), style = MaterialTheme.typography.bodySmall, color = sub)
        Spacer(Modifier.height(Spacing.sm))
        val clearedMsg = stringResource(R.string.temp_cleared)
        OutlinedButton(onClick = {
            scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { tempFiles(context).forEach { it.deleteRecursively() } }
                measureStorage(); done = clearedMsg to false
            }
        }, enabled = (tempBytes ?: 0L) > 0L, modifier = buttonMod) { Text(stringResource(R.string.clear_temp)) }
    }
    Spacer(Modifier.height(Spacing.sm))
    ForensimCard {
        Text(stringResource(R.string.video_data), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.video_data_body), style = MaterialTheme.typography.bodySmall, color = sub)
        Spacer(Modifier.height(Spacing.sm))
        val videoMsg = stringResource(R.string.video_data_cleared)
        OutlinedButton(onClick = {
            runCatching {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
                android.webkit.WebView(context).apply { clearCache(true); destroy() }
            }
            done = videoMsg to false
        }, modifier = buttonMod) { Text(stringResource(R.string.clear_video_data)) }
    }

    // ---------------------------------------------------------------- account security
    SectionHeader(stringResource(R.string.section_security))
    ForensimCard {
        Text(stringResource(R.string.sign_out_everywhere), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.sign_out_everywhere_body), style = MaterialTheme.typography.bodySmall, color = sub)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = { confirmEverywhere = true }, enabled = !busyEverywhere, modifier = buttonMod,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.sign_out_everywhere))
        }
    }

    // ---------------------------------------------------------------- diagnostics
    SectionHeader(stringResource(R.string.section_diagnostics))
    val online = rememberIsOnline()
    val notTested = stringResource(R.string.diag_not_tested)
    val diag = listOf(
        stringResource(R.string.diag_app_version) to "${com.forensim.app.BuildConfig.VERSION_NAME} (${com.forensim.app.BuildConfig.VERSION_CODE})",
        stringResource(R.string.diag_android) to "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
        stringResource(R.string.diag_device) to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        stringResource(R.string.diag_connection) to stringResource(if (online) R.string.diag_online else R.string.diag_offline),
        stringResource(R.string.diag_server) to (ping ?: notTested),
    )
    ForensimCard {
        diag.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = sub)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        val okFmt = R.string.diag_server_ok
        val failText = stringResource(R.string.diag_server_fail)
        OutlinedButton(onClick = {
            testing = true
            scope.launch {
                ping = runCatching { c.auth.pingServer() }.fold({ context.getString(okFmt, it.toInt()) }, { failText })
                testing = false
            }
        }, enabled = !testing, modifier = buttonMod) { Text(stringResource(if (testing) R.string.testing else R.string.test_connection)) }
        val copiedMsg = stringResource(R.string.diagnostics_copied)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = {
            val text = "Forensim diagnostics\n" + diag.joinToString("\n") { (l, v) -> "$l: $v" }
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Forensim diagnostics", text))
            done = copiedMsg to false
        }, modifier = buttonMod) { Text(stringResource(R.string.copy_diagnostics)) }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = {
            runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, modifier = buttonMod) { Text(stringResource(R.string.app_info)) }
    }

    // ---------------------------------------------------------------- reset
    SectionHeader(stringResource(R.string.reset_settings))
    ForensimCard {
        Text(stringResource(R.string.reset_settings_body), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text(stringResource(R.string.reset_settings))
        }
    }
    done?.let { (text, isError) ->
        Spacer(Modifier.height(Spacing.md))
        com.forensim.app.core.designsystem.Banner(text, if (isError) Tone.ATTENTION else Tone.SUCCESS)
    }
    Spacer(Modifier.height(Spacing.lg))

    val offlineCleared = stringResource(R.string.offline_cleared)
    val resetDone = stringResource(R.string.settings_reset_done)
    if (confirmClear) ConfirmDialog(stringResource(R.string.clear_offline), stringResource(R.string.clear_offline_confirm), onDismiss = { confirmClear = false }) {
        confirmClear = false
        scope.launch { c.cache.clearOffline(); measureStorage(); done = offlineCleared to false }
    }
    if (confirmReset) ConfirmDialog(stringResource(R.string.reset_settings), stringResource(R.string.reset_settings_body), onDismiss = { confirmReset = false }) {
        confirmReset = false
        scope.launch { c.settings.resetToDefaults(); done = resetDone to false }
    }
    if (confirmEverywhere) ConfirmDialog(stringResource(R.string.sign_out_everywhere), stringResource(R.string.sign_out_everywhere_body), onDismiss = { confirmEverywhere = false }) {
        confirmEverywhere = false
        busyEverywhere = true
        // App scope: this screen disappears as soon as the sign-out lands.
        c.appScope.launch {
            try { c.auth.signOutEverywhere() } catch (e: Exception) { done = (e.message ?: "Could not sign out of other devices.") to true }
            busyEverywhere = false
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { com.forensim.app.core.designsystem.TextButton(onClick = onConfirm) { Text(title) } },
        dismissButton = { com.forensim.app.core.designsystem.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun HelpSettings() {
    SectionHeader(stringResource(R.string.help_common))
    val faqs = listOf(
        R.string.help_q1 to R.string.help_a1, R.string.help_q2 to R.string.help_a2, R.string.help_q3 to R.string.help_a3,
        R.string.help_q4 to R.string.help_a4, R.string.help_q5 to R.string.help_a5, R.string.help_q6 to R.string.help_a6,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        faqs.forEach { (q, a) ->
            ForensimCard {
                Text(stringResource(q), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(a), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FontSettings() {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    SectionHeader(stringResource(R.string.choose_font))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        com.forensim.app.core.designsystem.AppFont.entries.forEach { font ->
            val family = remember(font) { com.forensim.app.core.designsystem.fontFamilyFor(font) }
            val desc = stringResource(when (font) {
                com.forensim.app.core.designsystem.AppFont.INTER -> R.string.font_inter_desc
                com.forensim.app.core.designsystem.AppFont.POPPINS -> R.string.font_poppins_desc
                com.forensim.app.core.designsystem.AppFont.MANROPE -> R.string.font_manrope_desc
                com.forensim.app.core.designsystem.AppFont.DM_SANS -> R.string.font_dmsans_desc
            })
            ChoiceCard(selected = settings.font == font, onClick = { scope.launch { c.settings.setFont(font) } }) {
                Text(font.label, style = MaterialTheme.typography.titleLarge.copy(fontFamily = family))
                Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontFamily = family), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(R.string.font_preview), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family))
            }
        }
    }
    Text(stringResource(R.string.font_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.md))
}

@Composable
private fun LanguageSettings() {
    val current = remember { AppLanguage.current() }
    SectionHeader(stringResource(R.string.app_language))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppLanguage.entries.forEach { lang ->
            ChoiceCard(selected = current == lang, onClick = { if (lang != current) AppLanguage.apply(lang) }) {
                Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                Text(if (lang == AppLanguage.FILIPINO) "Tagalog" else "English (US)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(stringResource(R.string.language_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.md))
}

/** Single-choice option card with a radio indicator; the whole card is the touch target. */
@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) cs.primaryContainer.copy(alpha = 0.35f) else cs.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f)) { content() }
            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun qualityLabel(q: GraphicsQuality) = when (q) {
    GraphicsQuality.LOW -> R.string.quality_low
    GraphicsQuality.MEDIUM -> R.string.quality_medium
    GraphicsQuality.HIGH -> R.string.quality_high
}

// ----------------------------------------------------------------------------- shared pieces

/** Confirms, then signs out. Shared by every role's profile screen. */
@Composable
fun SignOutDialog(onDismiss: () -> Unit) {
    val c = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_out_title)) },
        text = { Text(stringResource(R.string.log_out_body)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); scope.launch { c.auth.signOut() } },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.log_out)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Widens the element by [amount] on each side (into the parent's padding) without moving its content. */
private fun Modifier.bleedHorizontally(amount: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = amount.roundToPx()
        if (!constraints.hasBoundedWidth) {
            val p = measurable.measure(constraints)
            return@layout layout(p.width, p.height) { p.place(0, 0) }
        }
        val wide = constraints.maxWidth + extra * 2
        val p = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
        layout(constraints.maxWidth, p.height) { p.place(-extra, 0) }
    }
)

/**
 * A card that is one switch. The whole card is the touch and keyboard target, so the press, hover
 * and focus highlight is drawn by the card's own surface and follows its rounded shape exactly.
 */
@Composable
fun SettingSwitchCard(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Surface(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch },
    ) {
        Row(
            Modifier.heightIn(min = Spacing.touchTarget).padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Visual only: the card handles the toggle, so the switch is not a separate focus stop.
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** A switch row inside a card that also holds other controls. */
@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        // Rounded highlight that spills 8dp into the card padding on each side (never past the card edge),
        // with 8dp inner padding so the text stays aligned with the rest of the card.
        Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
            .bleedHorizontally(Spacing.sm)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ControlSettingsScreen(onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val controls = settings.controls
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gyroSupported = remember { GyroController(context) { _, _ -> }.isSupported }
    fun save(new: ControlSettings) = scope.launch { c.settings.updateControls(new) }

    ScreenScaffold(title = stringResource(R.string.title_controls), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
            ReadableColumn {
                SectionHeader("Touch controls")
                ForensimCard {
                    SettingSlider("Look sensitivity", controls.lookSensitivity) { save(controls.copy(lookSensitivity = it)) }
                    SettingSwitch("Invert vertical look", null, controls.invertY) { save(controls.copy(invertY = it)) }
                    SettingSwitch("Left-handed layout", "Move with your right thumb, look with your left.", controls.leftHanded) { save(controls.copy(leftHanded = it)) }
                }
                SectionHeader("Motion controls")
                ForensimCard {
                    SettingSwitch(
                        "Gyroscope look",
                        if (gyroSupported) "Turn your phone to look around the scene." else "This device uses touch controls.",
                        controls.gyroEnabled && gyroSupported, enabled = gyroSupported,
                    ) { save(controls.copy(gyroEnabled = it)) }
                    if (controls.gyroEnabled && gyroSupported) SettingSlider("Gyroscope gain", controls.gyroGain) { save(controls.copy(gyroGain = it)) }
                }
                SectionHeader(stringResource(R.string.video_performance))
                VideoCardBody(controls, ::save)
            }
        }
    }
}

/** Graphics quality and field of view, reused on the Controls page. */
@Composable
private fun VideoCardBody(controls: ControlSettings, save: (ControlSettings) -> Unit) {
    ForensimCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GraphicsQuality.entries.forEach { quality ->
                FilterChip(selected = controls.graphics == quality, onClick = { save(controls.copy(graphics = quality)) },
                    label = { Text(stringResource(qualityLabel(quality))) }, modifier = Modifier.heightIn(min = Spacing.touchTarget))
            }
        }
        SettingSlider(stringResource(R.string.field_of_view), controls.fieldOfView, range = 70f..110f, format = { "${it.toInt()}°" }) {
            save(controls.copy(fieldOfView = it))
        }
    }
}

@Composable
internal fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 0.3f..2.5f,
    format: (Float) -> String = { "%.1fx".format(it) },
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Row { Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Text(format(local), style = MaterialTheme.typography.labelLarge) }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range,
            modifier = Modifier.semantics { contentDescription = label })
    }
}
