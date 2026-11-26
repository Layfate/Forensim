package com.forensim.app.feature.professor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.WorkspaceList
import com.forensim.app.core.designsystem.moduleIcon
import com.forensim.app.core.model.CourseClass
import com.forensim.app.core.model.Subject
import com.forensim.app.feature.classes.ClassCodeRow
import com.forensim.app.feature.classes.LockBadge
import com.forensim.app.feature.classes.SubjectClassCard
import com.forensim.app.feature.classes.shareCode

private const val MAX_SUBJECT_CLASSES = 7

// ----------------------------------------------------------------------------- Classes tab

@Composable
internal fun ProfessorClasses(vm: ProfessorViewModel, openClass: (String) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var created by remember { mutableStateOf<CourseClass?>(null) }
    androidx.compose.runtime.LaunchedEffect(vm.message) {
        if (vm.message != null) { kotlinx.coroutines.delay(3000); vm.message = null }
    }
    val migrateContext = LocalContext.current
    val loaded = vm.state is com.forensim.app.core.designsystem.Load.Ready
    androidx.compose.runtime.LaunchedEffect(loaded) { if (loaded) vm.migrateLocalClassColors(migrateContext) }
    ScreenScaffold(title = "Classes") { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            val remaining = d.subjects.filter { it.classId == null }
            WorkspaceList(Modifier.padding(inner)) {
                item("intro") {
                    // Compact header card: title + count with a small Create pill, short hint below.
                    val canCreate = remaining.isNotEmpty() && d.classes.size < MAX_SUBJECT_CLASSES
                    ForensimCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Subject classes", style = MaterialTheme.typography.titleMedium)
                                Text("${d.classes.size} of $MAX_SUBJECT_CLASSES subjects opened", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            com.forensim.app.core.designsystem.Button(
                                onClick = { vm.createClassError = null; creating = true },
                                enabled = canCreate,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 16.dp),
                                modifier = Modifier.heightIn(min = 40.dp),
                            ) {
                                if (canCreate) {
                                    Icon(androidx.compose.material.icons.Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(if (remaining.isEmpty() && d.subjects.isNotEmpty()) "All opened" else "Create",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("One class per subject. Students join with the class code and only see the lessons you unlock.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                vm.message?.let { m -> item("feedback") { Banner(m, Tone.PRIMARY) } }
                if (d.classes.isEmpty()) {
                    item("none") { Banner("No subject classes yet. Create one to get a class code for your students.", Tone.NEUTRAL) }
                } else {
                    item("mine-header") { SectionHeader("My subject classes") }
                    items(d.sortedClasses, key = { it.id }) { k ->
                        ProfessorClassCard(k, d) { openClass(k.id) }
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }
                val sims = d.simAttempts.filter { it.mode == "assessment" && it.status == "scored" }
                if (sims.isNotEmpty()) {
                    item("sim-header") { SectionHeader("Simulation assessments") }
                    items(sims, key = { "sim-" + it.id }) { a -> SimFeedbackCard(a, d, vm) }
                }
            }
            if (creating) {
                CreateClassDialog(vm, remaining, onDismiss = { creating = false }) { k -> creating = false; created = k }
            }
        }
    }
    created?.let { k -> ClassCreatedDialog(k, onOpen = { created = null; openClass(k.id) }, onDismiss = { created = null }) }
}

/** Big card for one of the professor's subject classes. */
@Composable
internal fun ProfessorClassCard(k: CourseClass, d: TeachingData, onClick: () -> Unit) {
    val m = d.moduleOf(k)
    val pending = d.pendingOf(k).size
    SubjectClassCard(
        slug = m?.slug ?: "",
        subjectNumber = m?.position ?: 0,
        title = m?.title ?: k.name,
        tint = com.forensim.app.feature.classes.classTint(com.forensim.app.feature.classes.ClassColor.from(k.color)),
        coverPath = k.coverPath,
        // Section (e.g. "CRIM 3A") replaces the "SUBJECT n" line, so no separate subtitle row below.
        label = listOfNotNull(k.name.substringAfter(" · ", "").ifBlank { null }, k.term).joinToString(" · ").ifBlank { null },
        subtitle = null,
        stats = emptyList(),
        code = k.joinCode,
        status = when {
            pending > 0 -> "$pending request${if (pending == 1) "" else "s"}" to Tone.ATTENTION
            k.requireApproval -> "Approval" to Tone.NEUTRAL
            else -> "Open" to Tone.SUCCESS
        },
        onClick = onClick,
    )
}

@Composable
private fun CreateClassDialog(vm: ProfessorViewModel, subjects: List<Subject>, onDismiss: () -> Unit, onCreated: (CourseClass) -> Unit) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableStateOf("") }
    val chosen = subjects.firstOrNull { it.moduleId == selected }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!vm.creatingClass) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                // Top bar
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = !vm.creatingClass) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
                    Text("New subject class", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                    // Class details first
                    Text("Class details", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.sm))
                    Text("Optional. Shown to your students under the subject name.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(section, { section = it.take(40) }, label = { Text("Section") },
                            placeholder = { Text("CRIM 3A") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(term, { term = it.take(40) }, label = { Text("Term") },
                            placeholder = { Text("2026-27 Sem 1") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    // Subject picker
                    Row(Modifier.padding(top = Spacing.lg, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text("Choose a subject", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${subjects.size} available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        subjects.forEach { s -> SubjectOption(s, selected == s.moduleId) { selected = s.moduleId } }
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
                // Bottom action
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(Spacing.md)) {
                    vm.createClassError?.let { Banner(it, Tone.ERROR); Spacer(Modifier.height(Spacing.sm)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Lessons start locked. You unlock them from the class page.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    PrimaryButton(
                        text = when {
                            vm.creatingClass -> "Creating…"
                            chosen == null -> "Choose a subject"
                            else -> "Create ${chosen.title}"
                        },
                        onClick = { chosen?.let { vm.createClass(it, section, term, onCreated) } },
                        enabled = chosen != null && !vm.creatingClass,
                        loading = vm.creatingClass,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectOption(s: Subject, selected: Boolean, onSelect: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        color = if (selected) cs.primaryContainer.copy(alpha = 0.45f) else cs.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            IconTile(moduleIcon(s.slug), size = 48.dp,
                container = if (selected) cs.primary else cs.primaryContainer,
                content = if (selected) cs.onPrimary else cs.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text("SUBJECT ${s.position}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${s.lessonCount} lessons", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) cs.primary else cs.outline,
            )
        }
    }
}

@Composable
private fun ClassCreatedDialog(k: CourseClass, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Class created") },
        text = {
            Column {
                Text(k.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text("Give this code to your students. They enter it in Classes → Join a class.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.md))
                ClassCodeRow(k.joinCode, large = true)
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Open class") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ----------------------------------------------------------------------------- class page

@Composable
internal fun ProfessorClassDetail(
    vm: ProfessorViewModel,
    classId: String,
    onBack: () -> Unit,
    openLesson: (String) -> Unit,
    openQuizBuilder: (String) -> Unit,
    openQuiz: (quizId: String) -> Unit,
    openUpload: (String) -> Unit,
    openChat: (classId: String, title: String) -> Unit = { _, _ -> },
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    var confirmRemoveCover by remember { mutableStateOf(false) }
    val thisClass = (vm.state as? com.forensim.app.core.designsystem.Load.Ready)?.data?.classById(classId)
    val classColor = com.forensim.app.feature.classes.ClassColor.from(thisClass?.color)
    val coverContext = LocalContext.current
    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null && thisClass != null) vm.uploadClassCover(coverContext, thisClass, uri) }
    fun pickCover() = coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
    if (pickColor && thisClass != null) {
        com.forensim.app.feature.classes.ClassColorDialog(
            current = classColor,
            onPick = { vm.setClassColor(thisClass, it); pickColor = false },
            onDismiss = { pickColor = false },
        )
    }
    if (confirmRemoveCover && thisClass != null) {
        AlertDialog(
            onDismissRequest = { confirmRemoveCover = false },
            title = { Text("Remove cover photo?") },
            text = { Text("The class card goes back to its color for you and your students.") },
            confirmButton = { TextButton(onClick = { confirmRemoveCover = false; vm.removeClassCover(thisClass) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemoveCover = false }) { Text("Cancel") } },
        )
    }
    var removing by remember { mutableStateOf<String?>(null) }
    var deletingQuiz by remember { mutableStateOf<com.forensim.app.core.model.Quiz?>(null) }
    var showRemoved by rememberSaveable(classId) { mutableStateOf(false) }
    // Load the chat in the background so it opens instantly.
    val chatRepo = com.forensim.app.ui.LocalAppContainer.current.chat
    androidx.compose.runtime.LaunchedEffect(classId) { chatRepo.prefetch(classId) }
    var headerExpanded by rememberSaveable(classId) { mutableStateOf(false) }
    var section by rememberSaveable(classId) { mutableStateOf(com.forensim.app.core.designsystem.ClassSection.LESSONS) }
    val showQuizzes = section == com.forensim.app.core.designsystem.ClassSection.QUIZZES
    val showStudents = section == com.forensim.app.core.designsystem.ClassSection.STUDENTS
    val context = LocalContext.current
    // Status messages ("... unlocked") clear themselves so they don't push the page down for long.
    androidx.compose.runtime.LaunchedEffect(vm.message) {
        if (vm.message != null) { kotlinx.coroutines.delay(3000); vm.message = null }
    }
    val title = (vm.state as? com.forensim.app.core.designsystem.Load.Ready)?.data?.let { d ->
        d.classById(classId)?.let { k -> d.moduleOf(k)?.let { "Subject ${it.position}" } }
    } ?: "Class"
    val classLoaded = (vm.state as? com.forensim.app.core.designsystem.Load.Ready)?.data?.classById(classId) != null
    ScreenScaffold(title = title, onBack = onBack, actions = {
        // Overflow menu: destructive actions stay out of the way of everyday class work.
        if (classLoaded) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                com.forensim.app.core.designsystem.IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Compact rows: the default 48dp items made the menu look oversized.
                    val itemModifier = Modifier.height(40.dp)
                    val itemPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Class color", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; pickColor = true },
                        modifier = itemModifier, contentPadding = itemPadding,
                    )
                    val hasCover = thisClass?.coverPath != null
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (hasCover) "Change cover photo" else "Add cover photo", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; pickCover() },
                        enabled = vm.coverBusy == null,
                        modifier = itemModifier, contentPadding = itemPadding,
                    )
                    if (hasCover) androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Remove cover photo", style = MaterialTheme.typography.labelLarge) },
                        leadingIcon = { Icon(Icons.Outlined.HideImage, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; confirmRemoveCover = true },
                        enabled = vm.coverBusy == null,
                        modifier = itemModifier, contentPadding = itemPadding,
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Delete class", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; confirmDelete = true },
                        modifier = itemModifier, contentPadding = itemPadding,
                    )
                }
            }
        }
    }) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            val k = d.classById(classId)
            if (k == null) {
                Banner("This class was deleted.", Tone.ATTENTION, modifier = Modifier.padding(inner).padding(Spacing.md))
                return@LoadContent
            }
            val m = d.moduleOf(k)
            val lessons = d.lessonsOf(k)
            val unlocked = lessons.count { d.isUnlocked(k, it) }
            val roster = d.activeRoster(k)
            val pending = d.pendingOf(k)
            val classQuizzes = d.classQuizzes(k)
            val shared = d.sharedQuizzes(k)
            WorkspaceList(Modifier.padding(inner)) {
                item("header") {
                    ClassHeaderCard(
                        k = k, slug = m?.slug ?: "", title = m?.title ?: k.name,
                        tint = com.forensim.app.feature.classes.classTint(classColor),
                        coverUploading = vm.coverBusy == k.id,
                        subtitle = listOfNotNull(k.name.substringAfter(" · ", "").ifBlank { null }, k.term).joinToString(" · ").ifBlank { null },
                        expanded = headerExpanded, onToggle = { headerExpanded = !headerExpanded },
                        onAnyoneCanJoin = { vm.setAnyoneCanJoin(k, it) },
                        onShare = { shareCode(context, k.joinCode, m?.title) },
                    )
                }
                vm.message?.let { msg -> item("feedback") { Spacer(Modifier.height(Spacing.sm)); Banner(msg, Tone.PRIMARY) } }
                item("stats") {
                    Spacer(Modifier.height(Spacing.sm))
                    DashboardStats("Students" to roster.size.toString(), "Unlocked" to "$unlocked/${lessons.size}",
                        "Quizzes" to (classQuizzes.size + shared.count { d.isQuizUnlocked(k, it) }).toString())
                }

                item("tabs") {
                    Spacer(Modifier.height(Spacing.md))
                    com.forensim.app.core.designsystem.ClassSectionTabs(
                        section = section, lessons = lessons.size, quizzes = classQuizzes.size + shared.size,
                        onSelect = { section = it },
                        onOpenChat = { openChat(k.id, m?.title ?: k.name) },
                        students = roster.size, pendingRequests = pending.size,
                    )
                }

                // ---------------------------------------------------------------- lessons
                if (section == com.forensim.app.core.designsystem.ClassSection.LESSONS) item("lessons-header") {
                    SectionHeader("Lessons", trailing = {
                        if (lessons.isNotEmpty()) {
                            val allOpen = unlocked == lessons.size
                            val working = vm.bulkUnlocking == k.id
                            TextButton(onClick = { vm.setAllUnlocked(k, !allOpen) }, enabled = !working) {
                                if (working) {
                                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(Spacing.xs))
                                }
                                Text(if (allOpen) "Lock all" else "Unlock all")
                            }
                        }
                    })
                    Text("Students only see unlocked lessons. Tap a lesson to preview it.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.sm))
                }
                if (section == com.forensim.app.core.designsystem.ClassSection.LESSONS) items(lessons, key = { "lesson-" + it.id }) { l ->
                    val open = d.isUnlocked(k, l)
                    ForensimCard(modifier = Modifier.padding(bottom = Spacing.xs),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LockBadge(open)
                            Column(Modifier.weight(1f).clickable { openLesson(l.id) }.padding(vertical = 4.dp)) {
                                Text(l.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${l.position}. ${if (l.isPdf) "PDF · " else ""}${l.readingMinutes} min read · ${if (open) "Visible to students" else "Locked"}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = open, onCheckedChange = { vm.setLessonUnlocked(k, l, it) },
                                modifier = Modifier.semantics { contentDescription = "Unlock ${l.title} for students" })
                        }
                    }
                }
                if (section == com.forensim.app.core.designsystem.ClassSection.LESSONS) item("upload") {
                    OutlinedButton(onClick = { openUpload(k.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Upload a PDF lesson")
                    }
                }

                // ---------------------------------------------------------------- quizzes
                if (showQuizzes) item("quizzes-header") {
                    SectionHeader("Quizzes", trailing = {
                        TextButton(onClick = { openQuizBuilder(k.id) }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New quiz")
                        }
                    })
                    if (classQuizzes.isEmpty()) {
                        Text("No quizzes in this class yet. Create one, or unlock a built-in practice quiz below.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm))
                    }
                }
                if (showQuizzes) items(classQuizzes, key = { "quiz-" + it.id }) { q ->
                    val submissions = d.quizAttempts.filter { it.quizId == q.id && it.status == "submitted" }
                    ForensimCard(modifier = Modifier.padding(bottom = Spacing.xs), onClick = { openQuiz(q.id) },
                        onLongClick = { deletingQuiz = q }, onLongClickLabel = "Delete quiz") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Quiz, size = 36.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(q.title, style = MaterialTheme.typography.bodyLarge)
                                Text("${if (q.isGraded) "Graded" else "Practice"} · ${submissions.map { it.studentId }.distinct().size} submitted · avg ${avgPct(submissions.map { it.score to it.maxScore })}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = q.published, onCheckedChange = { vm.setQuizPublished(q, it) },
                                modifier = Modifier.semantics { contentDescription = "Quiz open to students" })
                        }
                    }
                }
                if (showQuizzes && shared.isNotEmpty()) {
                    item("shared-header") {
                        Text("Built-in quizzes for this subject", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs))
                    }
                    items(shared, key = { "shared-" + it.id }) { q ->
                        val open = d.isQuizUnlocked(k, q)
                        ForensimCard(modifier = Modifier.padding(bottom = Spacing.xs),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                            onLongClick = { deletingQuiz = q }, onLongClickLabel = "Remove quiz from this class") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LockBadge(open)
                                Column(Modifier.weight(1f)) {
                                    Text(q.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(if (q.isGraded) "Graded" else "Practice", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = open, onCheckedChange = { vm.setQuizUnlocked(k, q, it) },
                                    modifier = Modifier.semantics { contentDescription = "Unlock ${q.title} for students" })
                            }
                        }
                    }
                }
                if (showQuizzes && (classQuizzes.isNotEmpty() || shared.isNotEmpty())) item("quiz-hint") {
                    Text("Press and hold a quiz to delete it.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.xs))
                }
                val removedQuizzes = d.removedQuizzes(k)
                if (showQuizzes && removedQuizzes.isNotEmpty()) {
                    item("removed-header") {
                        TextButton(onClick = { showRemoved = !showRemoved }) {
                            Text(if (showRemoved) "Hide removed built-in quizzes" else "Removed built-in quizzes (${removedQuizzes.size})")
                            Icon(if (showRemoved) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                    }
                    if (showRemoved) items(removedQuizzes, key = { "removed-" + it.id }) { q ->
                        ForensimCard(modifier = Modifier.padding(bottom = Spacing.xs),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(q.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (q.isGraded) "Graded" else "Practice", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { vm.setQuizHidden(k, q, false) }) { Text("Put back") }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------- students
                if (showStudents) {
                if (pending.isNotEmpty()) {
                    item("requests") {
                        SectionHeader("Requests to join (${pending.size})", trailing = {
                            if (pending.size > 1) TextButton(onClick = { vm.reviewJoin(k, null, true) }) { Text("Approve all") }
                        })
                        ForensimCard {
                            pending.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name(e.studentId), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Wants to join", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { vm.reviewJoin(k, e.studentId, false) },
                                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Decline") }
                                    com.forensim.app.core.designsystem.FilledTonalButton(onClick = { vm.reviewJoin(k, e.studentId, true) }) { Text("Approve") }
                                }
                            }
                        }
                    }
                }
                    item("students-header") { SectionHeader("Students (${roster.size})") }
                    if (roster.isEmpty()) item("no-students") {
                        Text("No students yet. Share the class code above.", style = MaterialTheme.typography.bodyMedium)
                    }
                    item("roster") {
                        if (roster.isNotEmpty()) ForensimCard {
                            val lessonIds = lessons.map { it.id }.toSet()
                            val quizIds = (classQuizzes + shared).map { it.id }.toSet()
                            roster.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val done = d.progress.count { it.studentId == e.studentId && it.lessonId in lessonIds }
                                val graded = d.quizAttempts.filter { it.studentId == e.studentId && it.status == "submitted" && it.quizId in quizIds }
                                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(d.name(e.studentId), style = MaterialTheme.typography.bodyLarge)
                                        Text("$done/$unlocked lessons done · Quiz avg ${avgPct(graded.map { it.score to it.maxScore }).replace("-", "\u2014")}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    com.forensim.app.core.designsystem.IconButton(onClick = { removing = e.studentId }) {
                                        Icon(Icons.Outlined.PersonRemove, contentDescription = "Remove ${d.name(e.studentId)} from the class",
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                item("bottom-space") { Spacer(Modifier.height(Spacing.xl)) }
            }
            removing?.let { sid ->
                AlertDialog(
                    onDismissRequest = { removing = null },
                    title = { Text("Remove ${d.name(sid)}?") },
                    text = { Text("They lose access to this class's lessons, quizzes and chat. Their past results are kept. They can ask to join again with the class code.") },
                    confirmButton = {
                        TextButton(onClick = { vm.removeStudent(k, sid); removing = null },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
                    },
                    dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
                )
            }
            deletingQuiz?.let { q ->
                val builtIn = q.classId == null
                AlertDialog(
                    onDismissRequest = { deletingQuiz = null },
                    title = { Text(if (builtIn) "Remove \"${q.title}\"?" else "Delete \"${q.title}\"?") },
                    text = {
                        Text(if (builtIn) "This built-in quiz is removed from this class only and locked for its students. Other classes keep it, and you can put it back from \"Removed built-in quizzes\"."
                             else "The quiz, its questions and all student results for it are deleted. This can't be undone.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            deletingQuiz = null
                            if (builtIn) vm.setQuizHidden(k, q, true) else vm.deleteQuiz(q)
                        }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(if (builtIn) "Remove" else "Delete")
                        }
                    },
                    dismissButton = { TextButton(onClick = { deletingQuiz = null }) { Text("Cancel") } },
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete ${k.name}?") },
                    text = { Text("Students are removed from the class and its quizzes and results are deleted. The subject's lessons stay in ForenSim.") },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete = false; vm.deleteClass(k, onDone = onBack) },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

/**
 * Class header on the class page. Collapsed (default): one compact row with the subject, section
 * and code. Expanded: the big code with copy/share, the "Anyone can join" switch and a share button.
 */
@Composable
private fun ClassHeaderCard(
    k: CourseClass,
    slug: String,
    title: String,
    subtitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAnyoneCanJoin: (Boolean) -> Unit,
    onShare: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    coverUploading: Boolean = false,
) {
    ForensimCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      Column(Modifier.fillMaxWidth().background(tint)) {
        // The cover photo (or the class tint) is the frame behind the subject and the code.
        Box(contentAlignment = Alignment.Center) {
          com.forensim.app.feature.classes.ClassCardBackground(k.coverPath, tint) { colors ->
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (expanded) "Hide class settings" else "Show class settings", onClick = onToggle)
                    .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = if (expanded) Spacing.sm else Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(moduleIcon(slug), size = 48.dp, container = colors.inner, content = colors.content)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(subtitle, if (expanded) null else "Code ${k.joinCode}").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = colors.variant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(if (k.requireApproval) "Approval" else "Open", if (k.requireApproval) Tone.NEUTRAL else Tone.SUCCESS)
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.variant)
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Box(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
                    ClassCodeRow(k.joinCode, large = true, container = colors.inner, contentColor = colors.content, labelColor = colors.variant)
                }
            }
          }
          if (coverUploading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        // Settings stay on the plain tint so the switch and button read normally.
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.md)) {
                com.forensim.app.feature.settings.SettingSwitch(
                    "Anyone can join",
                    if (k.requireApproval) "Off: students who enter the code wait for your approval"
                    else "On: the code adds students straight away",
                    checked = !k.requireApproval,
                    onChange = onAnyoneCanJoin,
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Share code with students")
                }
            }
        }
      }
    }
}
