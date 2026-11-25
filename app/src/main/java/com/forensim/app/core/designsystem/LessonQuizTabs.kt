package com.forensim.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Sections of a subject class page. Chat opens its own screen, so it isn't one of these. */
enum class ClassSection { STUDENTS, LESSONS, QUIZZES }

/**
 * Subject class navigation, two rows:
 *   Chats | Students      (Students only for the professor; students get Chats full width)
 *   Lessons | Quizzes
 * Chats opens the class chat; the others switch which list the page shows. A red dot on Students
 * means join requests are waiting.
 */
@Composable
fun ClassSectionTabs(
    section: ClassSection,
    lessons: Int,
    quizzes: Int,
    onSelect: (ClassSection) -> Unit,
    onOpenChat: (() -> Unit)?,
    students: Int? = null,
    pendingRequests: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (onOpenChat != null || students != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                onOpenChat?.let { open ->
                    TabButton("Chats", null, Icons.Outlined.Forum, selected = false, Modifier.weight(1f), arrow = true, onClick = open)
                }
                students?.let { n ->
                    TabButton("Students", n, Icons.Outlined.Group, selected = section == ClassSection.STUDENTS, Modifier.weight(1f),
                        alert = pendingRequests > 0, alertLabel = "$pendingRequests join request${if (pendingRequests == 1) "" else "s"} waiting") {
                        onSelect(ClassSection.STUDENTS)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TabButton("Lessons", lessons, Icons.AutoMirrored.Outlined.MenuBook, selected = section == ClassSection.LESSONS, Modifier.weight(1f)) {
                onSelect(ClassSection.LESSONS)
            }
            TabButton("Quizzes", quizzes, Icons.Outlined.Quiz, selected = section == ClassSection.QUIZZES, Modifier.weight(1f)) {
                onSelect(ClassSection.QUIZZES)
            }
        }
    }
}

/** Lessons | Quizzes only (kept for screens without chat or a roster). */
@Composable
fun LessonQuizTabs(showQuizzes: Boolean, lessons: Int, quizzes: Int, onSelect: (quizzes: Boolean) -> Unit, modifier: Modifier = Modifier) {
    ClassSectionTabs(if (showQuizzes) ClassSection.QUIZZES else ClassSection.LESSONS, lessons, quizzes,
        onSelect = { onSelect(it == ClassSection.QUIZZES) }, onOpenChat = null, modifier = modifier)
}

@Composable
private fun TabButton(
    label: String,
    count: Int?,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    arrow: Boolean = false,
    alert: Boolean = false,
    alertLabel: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(modifier) {
        Surface(
            selected = selected,
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(12.dp),
            color = if (selected) cs.primary else cs.surfaceContainer,
            contentColor = if (selected) cs.onPrimary else cs.onSurface,
            border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interaction, PressScale.CARD).semantics {
                role = if (arrow) Role.Button else Role.Tab
                alertLabel?.takeIf { alert }?.let { contentDescription = "$label, $it" }
            },
        ) {
            Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (count != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    Surface(shape = CircleShape, color = if (selected) cs.onPrimary else cs.surfaceContainerHighest,
                        contentColor = if (selected) cs.primary else cs.onSurfaceVariant) {
                        Text("$count", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TabularNumbers),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                if (arrow) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = cs.onSurfaceVariant)
                }
            }
        }
        if (alert) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp).size(10.dp).background(cs.error, CircleShape))
        }
    }
}
