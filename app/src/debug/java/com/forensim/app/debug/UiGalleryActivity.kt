package com.forensim.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ViewInAr
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.HomeHeader
import com.forensim.app.core.designsystem.ListDivider
import com.forensim.app.core.designsystem.ListGroup
import com.forensim.app.core.designsystem.ListRow
import com.forensim.app.core.designsystem.RowValue
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.headerGreeting
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.EmptyState
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.LoadingState
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.ResultHero
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.ThemePreference
import com.forensim.app.core.designsystem.Tone

/**
 * adb shell am start -n com.forensim.app/.debug.UiGalleryActivity --es screen success --es theme light
 * screen: success | review | components | loading | empty | home | tab | appearance | notifications ; theme: dark | light
 */
class UiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "success"
        val theme = if (intent.getStringExtra("theme") == "light") ThemePreference.LIGHT else ThemePreference.DARK
        setContent {
            ForensimTheme(preference = theme) {
                if (screen == "home" || screen == "tab") { WorkspacePreview(home = screen == "home"); return@ForensimTheme }
                if (screen == "victims") {
                    // The four victim models (male/female x face up/down) as students see them.
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            for (sex in com.forensim.app.engine.scene.VictimSex.entries) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                for (pose in com.forensim.app.engine.scene.VictimPose.entries) {
                                    com.forensim.app.feature.professor.VictimPreview(com.forensim.app.engine.scene.Victim(sex, pose),
                                        Modifier.weight(1f).fillMaxSize())
                                }
                            }
                        }
                    }
                    return@ForensimTheme
                }
                if (screen == "appearance" || screen == "notifications") {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.forensim.app.ui.LocalAppContainer provides (application as com.forensim.app.ForensimApplication).container,
                    ) {
                        com.forensim.app.feature.settings.SettingsSectionScreen(
                            if (screen == "appearance") com.forensim.app.feature.settings.SettingsSection.APPEARANCE
                            else com.forensim.app.feature.settings.SettingsSection.NOTIFICATIONS, onBack = {})
                    }
                    return@ForensimTheme
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                        when (screen) {
                            "success" -> {
                                ResultHero(true, "Case closed", "Outstanding work, Detective",
                                    "Your answers held up to scrutiny. Your score is recorded for Fingerprint Classification.",
                                    18.0, 20.0, stats = listOf("Correct answers" to "18 of 20", "Status" to "On time"))
                                Spacer(Modifier.height(Spacing.lg))
                                PrimaryButton("Back to module", onClick = {}, modifier = Modifier.fillMaxWidth())
                            }
                            "review" -> {
                                ResultHero(false, "Quiz submitted", "Keep investigating",
                                    "Every case teaches something. Go through the review below, then revisit the lesson.",
                                    9.0, 20.0, stats = listOf("Correct answers" to "9 of 20", "Status" to "On time"))
                                Spacer(Modifier.height(Spacing.lg))
                                PrimaryButton("Back to module", onClick = {}, modifier = Modifier.fillMaxWidth())
                            }
                            "loading" -> LoadingState(Modifier.height(600.dp))
                            "empty" -> EmptyState(Icons.Outlined.Inbox, "No cases yet", "Your professor hasn't unlocked a case for this class.",
                                Modifier.height(600.dp), "Refresh", {})
                            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                val quizzes = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(intent.getStringExtra("tab") == "quizzes") }
                                val section = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(
                                    if (quizzes.value) com.forensim.app.core.designsystem.ClassSection.QUIZZES else com.forensim.app.core.designsystem.ClassSection.LESSONS) }
                                com.forensim.app.core.designsystem.ClassSectionTabs(section.value, lessons = 8, quizzes = 3, onSelect = { section.value = it },
                                    onOpenChat = {}, students = 24, pendingRequests = 2)
                                FeaturePanel("Latent Print Development", "Personal Identification · 8 min read", Icons.Outlined.Fingerprint,
                                    category = "Continue learning", actionLabel = "Resume lesson", onClick = {}, compact = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    StatusChip("Due", Tone.PRIMARY); StatusChip("In progress", Tone.ATTENTION); StatusChip("Done", Tone.SUCCESS)
                                }
                                ProgressRow("Lessons", 0.62f, "5 of 8")
                                Banner("Detailed review will be available when your professor releases it.", Tone.NEUTRAL)
                                Banner("Submitted after the time limit.", Tone.ATTENTION)
                                PrimaryButton("Start attempt", onClick = {}, modifier = Modifier.fillMaxWidth())
                                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Previous") }
                                Text("Success colour", color = ForensimTheme.colors.success)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Home tab (HomeHeader) or a plain tab (title bar), inside the real bottom navigation. */
@androidx.compose.runtime.Composable
private fun WorkspacePreview(home: Boolean) {
    val destinations = listOf(
        com.forensim.app.core.designsystem.WorkspaceDestination("Home", Icons.Outlined.Home, selected = home, onClick = {}),
        com.forensim.app.core.designsystem.WorkspaceDestination("Classes", Icons.Outlined.School, selected = !home, onClick = {}),
        com.forensim.app.core.designsystem.WorkspaceDestination("Lab", Icons.Outlined.ViewInAr, selected = false, prominent = true, onClick = {}),
        com.forensim.app.core.designsystem.WorkspaceDestination("Tutor", Icons.Outlined.AutoAwesome, selected = false, onClick = {}),
        com.forensim.app.core.designsystem.WorkspaceDestination("Profile", Icons.Outlined.AccountCircle, selected = false, onClick = {}),
    )
    com.forensim.app.core.designsystem.WorkspaceScaffold(destinations) {
        if (home) {
            ScreenScaffold("Forensim", header = {
                HomeHeader(headerGreeting("Robin Cortez"), "Welcome back", "Robin Cortez", onProfile = {})
            }) { padding ->
                WorkspaceList(Modifier.padding(padding)) {
                    item { ListGroup {
                        ListRow("Lessons", icon = Icons.AutoMirrored.Outlined.MenuBook, trailing = { RowValue("5 / 14") })
                        ListDivider()
                        ListRow("Quiz average", icon = Icons.Outlined.Quiz, trailing = { RowValue("86%") })
                        ListDivider()
                        ListRow("Cases solved", icon = Icons.Outlined.Fingerprint, trailing = { RowValue("2") })
                    } }
                    item {
                        FeaturePanel("Latent Print Development", "Personal Identification · 8 min read", Icons.Outlined.Fingerprint,
                            category = "Continue learning", actionLabel = "Resume lesson", onClick = {}, compact = true)
                    }
                    item { SectionHeader("New briefs", trailing = { com.forensim.app.core.designsystem.TextButton(onClick = {}) { Text("See all") } }) }
                    item { ForensimCard {
                        Text("Nothing here", style = MaterialTheme.typography.titleMedium)
                        Text("Lessons your professor publishes will show up here.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    item { PrimaryButton("Start attempt", onClick = {}, modifier = Modifier.fillMaxWidth()) }
                }
            }
        } else {
            ScreenScaffold("Manage classes") { padding ->
                EmptyState(Icons.Outlined.Inbox, "Find all your classes here", "Join a class with the code from your professor.",
                    Modifier.padding(padding), "Join a class", {})
            }
        }
    }
}
