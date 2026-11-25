package com.forensim.app.feature.lms

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.foundation.layout.heightIn
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.WorkspaceList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.OfflineBanner
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.designsystem.outOf
import com.forensim.app.core.model.LearningModule
import com.forensim.app.core.model.Lesson
import com.forensim.app.core.model.Quiz
import com.forensim.app.core.model.QuizAttempt
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class Catalog(
    val modules: List<LearningModule>,
    val lessons: List<Lesson>,
    val completed: Set<String>,
    val quizzes: List<Quiz>,
    val attempts: List<QuizAttempt>,
    val classes: List<com.forensim.app.core.model.CourseClass> = emptyList(),
    /** Joined subject classes with unlocked lesson counts (public.my_subject_classes). */
    val subjects: List<com.forensim.app.core.model.SubjectClass> = emptyList(),
)

class CatalogViewModel(private val c: AppContainer) : ViewModel() {
    var state by mutableStateOf<Load<Catalog>>(Load.Loading); private set
    var marking by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    /** True when [message] reports a failure (shown as a warning, not a success). */
    var messageIsError by mutableStateOf(false); private set
    /** Lessons marked complete in this session; kept ticked even if a reload is slow or stale. */
    private var markedHere = emptySet<String>()

    init {
        load()
        // A lesson marked complete on another screen (e.g. the lesson page) ticks here at once.
        viewModelScope.launch {
            c.learning.completedLessons.collect { done ->
                if (done.isEmpty()) return@collect
                markedHere = markedHere + done
                (state as? Load.Ready)?.let { if (!it.data.completed.containsAll(done)) setCompleted(it.data.completed + done) }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            if (state !is Load.Ready) state = peek()?.let { Load.Ready(it) } ?: Load.Loading
            state = try {
                coroutineScope {
                    val m = async { c.learning.modules() }
                    val l = async { c.learning.lessons() }
                    val p = async { c.learning.progress() }
                    val q = async { runCatching { c.learning.quizzes() }.getOrNull() }
                    val a = async { runCatching { c.learning.myAttempts() }.getOrNull() }
                    val cl = async { runCatching { c.learning.myClasses() }.getOrNull() }
                    val sc = async { runCatching { c.learning.mySubjectClasses() }.getOrNull() }
                    val modules = m.await()
                    val online = !modules.fromCache
                    Load.Ready(
                        Catalog(
                            modules.value.sortedBy { it.position },
                            l.await().value,
                            p.await().value.map { it.lessonId }.toSet() + markedHere,
                            if (online) q.await().orEmpty() else emptyList(),
                            if (online) a.await().orEmpty() else emptyList(),
                            if (online) cl.await().orEmpty() else emptyList(),
                            sc.await()?.value.orEmpty(),
                        ),
                        modules.fromCache,
                    )
                }
            } catch (e: ApiException) {
                (state as? Load.Ready) ?: Load.Error(e.message)
            }
        }
    }

    private suspend fun peek(): Catalog? {
        val modules = c.learning.peekModules() ?: return null
        return Catalog(
            modules.sortedBy { it.position },
            c.learning.peekLessons().orEmpty(),
            c.learning.peekProgress().orEmpty().map { it.lessonId }.toSet(),
            c.learning.peekQuizzes().orEmpty(),
            c.learning.peekAttempts().orEmpty(),
            c.learning.peekClasses().orEmpty(),
            c.learning.peekSubjectClasses().orEmpty(),
        )
    }

    var joinBusy by mutableStateOf(false); private set
    var joinError by mutableStateOf<String?>(null); private set

    fun join(code: String, onJoined: () -> Unit = {}) {
        joinError = null
        if (code.isBlank()) { joinError = "Enter the code from your professor."; return }
        joinBusy = true
        viewModelScope.launch {
            try {
                val status = c.learning.joinClass(code.trim())
                messageIsError = false
                message = if (status == "pending") "Request sent. You can open the subject once your professor approves you."
                          else "You joined the class."
                load(); onJoined()
            } catch (e: ApiException) {
                joinError = e.message
            } finally {
                joinBusy = false
            }
        }
    }

    /**
     * Ticks the lesson at once (the button turns to "Completed" on tap), then saves it. If saving
     * fails the tick is taken back and the reason is shown. No full reload is needed afterwards.
     */
    fun markComplete(lessonId: String) {
        val ready = state as? Load.Ready ?: return
        if (marking || lessonId in ready.data.completed) return
        marking = true
        message = null
        messageIsError = false
        markedHere = markedHere + lessonId
        setCompleted(ready.data.completed + lessonId)
        viewModelScope.launch {
            try {
                c.learning.markLessonComplete(lessonId)
                message = "Lesson marked complete."
            } catch (e: ApiException) {
                markedHere = markedHere - lessonId
                (state as? Load.Ready)?.let { setCompleted(it.data.completed - lessonId) }
                message = if (e.isNetwork) "You're offline, so this lesson couldn't be marked complete. Try again when you're connected." else e.message
                messageIsError = true
            } finally {
                marking = false
            }
        }
    }

    private fun setCompleted(completed: Set<String>) {
        val ready = state as? Load.Ready ?: return
        state = ready.copy(data = ready.data.copy(completed = completed))
    }
}

@Composable
fun ModulesScreen(openModule: (String) -> Unit) {
    val vm = appViewModel(key = "catalog") { CatalogViewModel(it) }
    ScreenScaffold(title = stringResource(R.string.title_classes)) { padding ->
        LoadContent(vm.state, vm::load, Modifier.padding(padding)) { cat, fromCache ->
            WorkspaceList(Modifier.padding(padding)) {
                item("offline") { OfflineBanner(fromCache) }
                if (!fromCache) item("join") { JoinSubjectClassCard(vm, compact = cat.subjects.isNotEmpty()) }
                vm.message?.let { m -> item("message") { Spacer(Modifier.height(Spacing.sm)); Banner(m, Tone.SUCCESS) } }
                if (cat.subjects.isEmpty()) {
                    if (!fromCache) item("empty") {
                        Spacer(Modifier.height(Spacing.sm))
                        Banner("You have no subjects yet. Ask your professor for a class code and enter it above.", Tone.NEUTRAL)
                    }
                } else {
                    item("subjects-header") { SectionHeader("My subjects (${cat.subjects.size})") }
                    items(cat.subjects, key = { it.classId }) { s ->
                        val visible = cat.lessons.filter { it.moduleId == s.moduleId }
                        val done = visible.count { it.id in cat.completed }
                        com.forensim.app.feature.classes.SubjectClassCard(
                            slug = s.slug,
                            subjectNumber = s.position,
                            title = s.title,
                            subtitle = listOfNotNull(s.professorName, s.name.substringAfter(" · ", "").ifBlank { null }, s.term).joinToString(" · "),
                            stats = if (s.pending) emptyList()
                                    else listOf("${s.unlockedLessons}/${s.totalLessons} lessons unlocked", "${s.quizCount} quiz${if (s.quizCount == 1) "" else "zes"}"),
                            status = if (s.pending) "Waiting for approval" to Tone.ATTENTION else null,
                            progress = if (s.pending) null else (if (visible.isEmpty()) 0f else done / visible.size.toFloat()) to "$done/${visible.size} done",
                            onClick = { if (s.pending) vm.message = "Your professor has not approved your request yet." else openModule(s.moduleId) },
                            // The professor's class color and cover photo.
                            tint = com.forensim.app.feature.classes.classTint(com.forensim.app.feature.classes.ClassColor.from(s.color)),
                            coverPath = s.coverPath,
                            code = s.joinCode, shareCode = false,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinSubjectClassCard(vm: CatalogViewModel, compact: Boolean) {
    var code by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(!compact) }
    // No card padding: the header is a full-width tap strip, so its pressed highlight runs edge to
    // edge and is clipped by the card's own rounded corners instead of floating inside them.
    ForensimCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .then(if (compact) Modifier.clickable(
                    onClickLabel = if (expanded) "Hide" else "Show",
                    role = androidx.compose.ui.semantics.Role.Button,
                ) { expanded = !expanded } else Modifier)
                .padding(Spacing.md),
        ) {
            com.forensim.app.core.designsystem.IconTile(Icons.Outlined.School, size = 40.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Join a class", style = MaterialTheme.typography.titleMedium)
                Text("Enter the code your professor gave you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (compact) Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (expanded) Column(Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md)) {
            com.forensim.app.core.designsystem.OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(16) },
                label = { Text("Class code") }, singleLine = true,
                isError = vm.joinError != null,
                supportingText = vm.joinError?.let { { Text(it) } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton("Join class", onClick = { vm.join(code) { code = "" } }, enabled = code.isNotBlank(), loading = vm.joinBusy,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ModuleDetailScreen(
    moduleId: String,
    onBack: () -> Unit,
    openLesson: (String) -> Unit,
    openQuiz: (String) -> Unit,
    openTutor: (String) -> Unit,
    openChat: (classId: String, title: String) -> Unit = { _, _ -> },
) {
    val vm = appViewModel(key = "catalog") { CatalogViewModel(it) }
    val module = (vm.state as? Load.Ready)?.data?.modules?.firstOrNull { it.id == moduleId }
    var showQuizzes by androidx.compose.runtime.saveable.rememberSaveable(moduleId) { mutableStateOf(false) }
    ScreenScaffold(title = module?.let { "Module ${it.position}" } ?: "Module", onBack = onBack) { padding ->
        LoadContent(vm.state, vm::load, Modifier.padding(padding)) { cat, fromCache ->
            val m = cat.modules.firstOrNull { it.id == moduleId }
            if (m == null) {
                Banner("This module is not available.", Tone.ATTENTION, modifier = Modifier.padding(padding).padding(Spacing.md))
                return@LoadContent
            }
            WorkspaceList(Modifier.padding(padding)) {
                item {
                    ReadableColumn {
                        OfflineBanner(fromCache)
                        FeaturePanel(m.title, m.summary, com.forensim.app.core.designsystem.moduleIcon(m.slug),
                            category = "Module ${m.position}")

                        val quizzes = cat.quizzes.filter { it.moduleId == m.id }
                        Spacer(Modifier.height(Spacing.md))
                        // Chats needs an approved place in the subject's class.
                        val myClass = cat.subjects.firstOrNull { it.moduleId == m.id && it.status == "active" }
                        // Load the chat in the background so it opens instantly.
                        val chatRepo = com.forensim.app.ui.LocalAppContainer.current.chat
                        androidx.compose.runtime.LaunchedEffect(myClass?.classId) { myClass?.classId?.let { chatRepo.prefetch(it) } }
                        com.forensim.app.core.designsystem.ClassSectionTabs(
                            section = if (showQuizzes) com.forensim.app.core.designsystem.ClassSection.QUIZZES else com.forensim.app.core.designsystem.ClassSection.LESSONS,
                            lessons = cat.lessons.count { it.moduleId == m.id }, quizzes = quizzes.size,
                            onSelect = { showQuizzes = it == com.forensim.app.core.designsystem.ClassSection.QUIZZES },
                            onOpenChat = myClass?.let { k -> { openChat(k.classId, m.title) } },
                        )
                        Spacer(Modifier.height(Spacing.md))
                        if (!showQuizzes) {
                        val sub = cat.subjects.firstOrNull { it.moduleId == m.id }
                        val visibleCount = cat.lessons.count { it.moduleId == m.id }
                        if (visibleCount == 0) {
                            Banner("Your professor has not unlocked any lessons yet. They will appear here once unlocked.", Tone.NEUTRAL, Icons.Outlined.Lock)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            cat.lessons.filter { it.moduleId == m.id }.sortedBy { it.position }.forEach { l ->
                                val done = l.id in cat.completed
                                ForensimCard(onClick = { openLesson(l.id) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Circle, contentDescription = if (done) "Completed" else "Not completed",
                                            tint = if (done) com.forensim.app.core.designsystem.ForensimTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(Spacing.md))
                                        Column(Modifier.weight(1f)) {
                                            Text(l.title, style = MaterialTheme.typography.titleSmall)
                                            Text("${l.readingMinutes} min read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        val locked = (sub?.totalLessons ?: 0) - visibleCount
                        if (visibleCount > 0 && locked > 0) {
                            Spacer(Modifier.height(Spacing.sm))
                            ForensimCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(Spacing.md))
                                    Text("$locked more lesson${if (locked == 1) " is" else "s are"} locked by your professor.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        } else {
                        if (quizzes.isEmpty()) {
                            // Plain centred text, no card, in the empty space under the tabs.
                            Box(Modifier.fillMaxWidth().padding(vertical = 96.dp), contentAlignment = Alignment.Center) {
                                Text("No quiz yet.", style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        } else {
                            // Two groups: graded quizzes first (they count), then practice.
                            val (gradedQuizzes, practiceQuizzes) = quizzes.partition { it.isGraded }
                            listOf("Graded quizzes" to gradedQuizzes, "Practice quizzes" to practiceQuizzes).filter { it.second.isNotEmpty() }.forEach { (heading, group) ->
                            SectionHeader("$heading (${group.size})")
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                group.forEach { q ->
                                    val mine = cat.attempts.filter { it.quizId == q.id }
                                    val best = mine.filter { it.score != null }.maxByOrNull { it.score!! }
                                    ForensimCard(onClick = { openQuiz(q.id) }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(q.title, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    if (q.isGraded) buildString {
                                                        q.timeLimitSeconds?.let { append("${it / 60} min · ") }
                                                        append(q.maxAttempts?.let { "${mine.size}/$it attempts used" } ?: "${mine.size} attempts")
                                                        best?.let { append(" · best ${it.score!!.outOf(it.maxScore)}") }
                                                    } else "Instant feedback, not graded",
                                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            StatusChip(if (q.isGraded) "Graded" else "Practice", if (q.isGraded) Tone.ATTENTION else Tone.SUCCESS)
                                        }
                                    }
                                }
                            }
                            }
                        }

                        }

                        Spacer(Modifier.height(Spacing.md))
                        OutlinedButton(onClick = { openTutor(m.title) }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) {
                            Icon(Icons.Outlined.QuestionAnswer, contentDescription = null)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Ask the tutor about this module")
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}

@Composable
fun LessonScreen(lessonId: String, onBack: () -> Unit, openTutor: (String) -> Unit) {
    val vm = appViewModel(key = "catalog") { CatalogViewModel(it) }
    ScreenScaffold(title = "Lesson", onBack = onBack) { padding ->
        LoadContent(vm.state, vm::load, Modifier.padding(padding)) { cat, fromCache ->
            val lesson = cat.lessons.firstOrNull { it.id == lessonId }
            if (lesson == null) {
                Banner("This lesson is not available.", Tone.ATTENTION, modifier = Modifier.padding(padding).padding(Spacing.md))
                return@LoadContent
            }
            val module = cat.modules.firstOrNull { it.id == lesson.moduleId }
            val done = lesson.id in cat.completed
            if (lesson.pdfPath != null) {
                PdfLesson(lesson, module, done, fromCache, vm, Modifier.padding(padding), openTutor)
                return@LoadContent
            }
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                    ReadableColumn {
                        OfflineBanner(fromCache)
                        PageIntro(
                            title = lesson.title,
                            category = module?.let { "Module ${it.position} · ${it.title}" },
                            description = "${lesson.readingMinutes} min read · Version ${lesson.version}",
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (lesson.pendingReview) {
                            Spacer(Modifier.height(Spacing.md))
                            Banner("Draft content pending instructor review. Confirm procedures with your professor.", Tone.ATTENTION, Icons.Outlined.RateReview)
                        }
                        lesson.sections.forEach { s ->
                            Spacer(Modifier.height(Spacing.lg))
                            Text(s.heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(Spacing.sm))
                            Text(s.body, style = MaterialTheme.typography.bodyLarge)
                            s.image?.let { LessonImage(it, s.caption, s.credit) }
                            s.video?.let { LessonVideo(it, s.videoTitle) }
                        }
                        if (lesson.keyTerms.isNotEmpty()) {
                            SectionHeader("Key terms")
                            ForensimCard {
                                lesson.keyTerms.forEachIndexed { i, t ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(t.term, style = MaterialTheme.typography.titleSmall)
                                    Text(t.definition, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (lesson.citations.isNotEmpty()) {
                            SectionHeader("References")
                            lesson.citations.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = Spacing.sm))
                            }
                        }
                        vm.message?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, if (vm.messageIsError) Tone.ATTENTION else Tone.SUCCESS) }
                        Spacer(Modifier.height(Spacing.lg))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedButton(onClick = { openTutor(module?.title ?: "") }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touchTarget)) {
                                Icon(Icons.Outlined.QuestionAnswer, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Ask tutor")
                            }
                            PrimaryButton(
                                text = if (done) "Completed" else "Mark complete",
                                onClick = { vm.markComplete(lesson.id) },
                                enabled = !done && !fromCache,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(Spacing.xl))
                    }
                }
            }
        }
    }
}

/** A professor-uploaded PDF lesson: pages render inline, with the usual tutor / complete actions. */
@Composable
private fun PdfLesson(
    lesson: com.forensim.app.core.model.Lesson,
    module: com.forensim.app.core.model.LearningModule?,
    done: Boolean,
    fromCache: Boolean,
    vm: CatalogViewModel,
    modifier: Modifier,
    openTutor: (String) -> Unit,
) {
    val container = com.forensim.app.ui.LocalAppContainer.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var attempt by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val file by androidx.compose.runtime.produceState<Result<java.io.File>?>(null, lesson.pdfPath, attempt) {
        value = runCatching { container.learning.lessonPdf(lesson.pdfPath!!, context.cacheDir) }
    }
    Box(modifier.fillMaxSize()) {
        when (val f = file) {
            null -> com.forensim.app.core.designsystem.LoadingState(label = "Opening PDF")
            else -> f.fold(
                onSuccess = { pdf ->
                    PdfPages(
                        pdf,
                        header = {
                            item("intro") {
                                OfflineBanner(fromCache)
                                PageIntro(
                                    title = lesson.title,
                                    category = module?.let { "Module ${it.position} · ${it.title}" },
                                    description = "PDF lesson · ${lesson.readingMinutes} min read",
                                )
                            }
                        },
                        footer = {
                            item("actions") {
                                vm.message?.let { Banner(it, if (vm.messageIsError) Tone.ATTENTION else Tone.SUCCESS); Spacer(Modifier.height(Spacing.sm)) }
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.xl)) {
                                    OutlinedButton(onClick = { openTutor(module?.title ?: "") }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touchTarget)) {
                                        Icon(Icons.Outlined.QuestionAnswer, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(Spacing.xs))
                                        Text("Ask tutor")
                                    }
                                    PrimaryButton(
                                        text = if (done) "Completed" else "Mark complete",
                                        onClick = { vm.markComplete(lesson.id) },
                                        enabled = !done && !fromCache,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        },
                    )
                },
                onFailure = { e ->
                    com.forensim.app.core.designsystem.ErrorState(
                        (e as? com.forensim.app.core.network.ApiException)?.message ?: "Couldn't open this PDF.",
                        onRetry = { attempt++ },
                    )
                },
            )
        }
    }
}
