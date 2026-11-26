package com.forensim.app.feature.student

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Fingerprint
import com.forensim.app.core.designsystem.IconButton
import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.WorkspaceList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined._3dRotation
import com.forensim.app.core.designsystem.Avatar
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.moduleIcon
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
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.designsystem.outOf
import com.forensim.app.core.model.CourseClass
import com.forensim.app.core.model.LearningModule
import com.forensim.app.core.model.Lesson
import com.forensim.app.core.model.Quiz
import com.forensim.app.core.model.QuizAttempt
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class StudentDashboard(
    val modules: List<LearningModule>,
    val lessons: List<Lesson>,
    val completed: Set<String>,
    val quizzes: List<Quiz>,
    val attempts: List<QuizAttempt>,
    val simAttempts: List<SimulationAttempt>,
    val classes: List<CourseClass>,
    val notice: String?,
    val fromCache: Boolean,
    val subjects: List<com.forensim.app.core.model.SubjectClass> = emptyList(),
) {
    fun moduleProgress(moduleId: String): Pair<Int, Int> {
        val ls = lessons.filter { it.moduleId == moduleId }
        return ls.count { it.id in completed } to ls.size
    }

    val nextLesson: Lesson?
        get() = modules.sortedBy { it.position }.firstNotNullOfOrNull { m ->
            lessons.filter { it.moduleId == m.id }.sortedBy { it.position }.firstOrNull { it.id !in completed }
        }

    val pendingGraded: List<Quiz>
        get() = quizzes.filter { q -> q.isGraded && attempts.none { it.quizId == q.id && it.status == "submitted" } }

    val practiceQuizzes: List<Quiz> get() = quizzes.filter { !it.isGraded }

    /**
     * Key terms from the student's unlocked lessons (or the built-in set before any are unlocked).
     * With only one or two unlocked terms the built-in set is added, so the term and quiz still
     * change every day instead of repeating the same one.
     */
    private val dailyTerms: List<Pair<String, String>>
        get() {
            val unlocked = lessons.sortedBy { it.id }.flatMap { l -> l.keyTerms.map { it.term to it.definition } }
            return (if (unlocked.size < 3) unlocked + FALLBACK_TERMS else unlocked).distinctBy { it.first.trim().lowercase() }
        }

    /** A key term from the student's unlocked lessons, changing once a day. */
    fun termOfTheDay(today: java.time.LocalDate): Pair<String, String> {
        val terms = dailyTerms
        return terms[Math.floorMod(today.toEpochDay(), terms.size.toLong()).toInt()]
    }

    /**
     * Today's one-shot quiz: a definition and four terms to pick from. It asks about a different
     * term than the term of the day, so swiping over doesn't give the answer away.
     */
    fun dailyQuiz(today: java.time.LocalDate): DailyQuiz = DailyQuiz.build(today.toEpochDay(), dailyTerms, FALLBACK_TERMS.map { it.first })
}

data class DailyQuiz(val day: Long, val definition: String, val answer: String, val options: List<String>) {
    companion object {
        /**
         * The quiz for epoch [day]: the definition of one of [terms], its term, and three other
         * terms (from [terms] and [extra]). The same day always gives the same quiz.
         *
         * Where the answer sits (A-D) is not left to a shuffle: java/kotlin Random seeded with
         * consecutive day numbers gives nearly the same first shuffle every day, which kept the
         * answer on the same letter. Instead every block of four days uses each letter once, in a
         * random order, so the letter changes from day to day and can't be guessed.
         */
        fun build(day: Long, terms: List<Pair<String, String>>, extra: List<String>): DailyQuiz {
            val (answer, definition) = terms[Math.floorMod(day + 1, terms.size.toLong()).toInt()]
            val distractors = (terms.map { it.first } + extra).distinctBy { it.trim().lowercase() }
                .filterNot { it.equals(answer, ignoreCase = true) }
                .shuffled(kotlin.random.Random(mix(day))).take(3)
            val slot = answerSlot(day, distractors.size + 1)
            return DailyQuiz(day, definition, answer, distractors.toMutableList().apply { add(slot, answer) })
        }

        /** Answer position for [day] among [count] options: each position once per block of [count] days. */
        internal fun answerSlot(day: Long, count: Int): Int {
            val block = Math.floorDiv(day, count.toLong())
            val order = (0 until count).shuffled(kotlin.random.Random(mix(block xor 0x5DEECE66DL)))
            return order[Math.floorMod(day, count.toLong()).toInt()]
        }

        /** SplitMix64 finalizer: nearby inputs (day numbers) give unrelated seeds. */
        private fun mix(x: Long): Long {
            var z = x + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}

/** Shown before any lesson is unlocked. */
private val FALLBACK_TERMS = listOf(
    "Locard's exchange principle" to "Every contact leaves a trace: a person takes something from a scene and leaves something behind.",
    "Chain of custody" to "The documented, unbroken record of everyone who handled an item of evidence, from collection to court.",
    "Latent print" to "An invisible fingerprint left by sweat and oils that must be developed with powder or chemicals to be seen.",
    "Corpus delicti" to "The body of the crime: proof that a crime actually took place.",
    "Rifling" to "Spiral grooves cut inside a gun barrel that spin the bullet and leave marks on it.",
    "Questioned document" to "Any document whose authorship, genuineness or history is disputed.",
    "Presumptive test" to "A quick screening test that suggests, but does not prove, what a substance is.",
)

class StudentDashboardViewModel(private val c: AppContainer) : ViewModel() {
    var state by mutableStateOf<Load<StudentDashboard>>(Load.Loading); private set
    var joinBusy by mutableStateOf(false); private set
    var joinError by mutableStateOf<String?>(null); private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Paint the last saved dashboard immediately, then refresh it from the server.
            if (state !is Load.Ready) state = peek()?.let { Load.Ready(it) } ?: Load.Loading
            state = try {
                coroutineScope {
                    val modules = async { c.learning.modules() }
                    val lessons = async { c.learning.lessons() }
                    val progress = async { c.learning.progress() }
                    // Started together with the core lists instead of one after another.
                    val quizzes = async { runCatching { c.learning.quizzes() }.getOrNull() }
                    val attempts = async { runCatching { c.learning.myAttempts() }.getOrNull() }
                    val simAttempts = async { runCatching { c.simulation.myAttempts() }.getOrNull() }
                    val classes = async { runCatching { c.learning.myClasses() }.getOrNull() }
                    val subjects = async { runCatching { c.learning.mySubjectClasses() }.getOrNull() }
                    val notice = async { c.learning.systemNotice() }
                    val m = modules.await()
                    val online = !m.fromCache
                    Load.Ready(
                        StudentDashboard(
                            modules = m.value,
                            lessons = lessons.await().value,
                            completed = progress.await().value.map { it.lessonId }.toSet(),
                            quizzes = if (online) quizzes.await().orEmpty() else emptyList(),
                            attempts = if (online) attempts.await().orEmpty() else emptyList(),
                            simAttempts = if (online) simAttempts.await().orEmpty() else emptyList(),
                            classes = if (online) classes.await().orEmpty() else emptyList(),
                            notice = if (online) notice.await() else null,
                            fromCache = m.fromCache,
                            subjects = subjects.await()?.value.orEmpty(),
                        ),
                        fromCache = m.fromCache,
                    )
                }
            } catch (e: ApiException) {
                // Keep what is already on screen rather than replacing it with an error page.
                (state as? Load.Ready) ?: Load.Error(e.message)
            }
        }
    }

    private suspend fun peek(): StudentDashboard? {
        val modules = c.learning.peekModules() ?: return null
        return StudentDashboard(
            modules = modules,
            lessons = c.learning.peekLessons().orEmpty(),
            completed = c.learning.peekProgress().orEmpty().map { it.lessonId }.toSet(),
            quizzes = c.learning.peekQuizzes().orEmpty(),
            attempts = c.learning.peekAttempts().orEmpty(),
            simAttempts = c.simulation.peekAttempts().orEmpty(),
            classes = c.learning.peekClasses().orEmpty(),
            notice = null,
            fromCache = false,
            subjects = c.learning.peekSubjectClasses().orEmpty(),
        )
    }

    fun join(code: String) {
        joinError = null
        if (code.isBlank()) { joinError = "Enter the code from your professor."; return }
        joinBusy = true
        viewModelScope.launch {
            try {
                c.learning.joinClass(code.trim())
                load()
            } catch (e: ApiException) {
                joinError = e.message
            } finally {
                joinBusy = false
            }
        }
    }
}

@Composable
fun HomeScreen(
    user: UserAccount,
    openModule: (String) -> Unit,
    openLesson: (String) -> Unit,
    openQuiz: (String) -> Unit,
    openSimulate: () -> Unit,
    openProfile: () -> Unit,
    openTutor: () -> Unit = {},
    openAr: () -> Unit = {},
    openClasses: () -> Unit = {},
) {
    val vm = appViewModel { StudentDashboardViewModel(it) }
    ScreenScaffold(title = "ForenSim", header = {
        val photo by com.forensim.app.ui.LocalAppContainer.current.profile.avatar.collectAsStateWithLifecycle()
        com.forensim.app.core.designsystem.HomeHeader(
            greeting = com.forensim.app.core.designsystem.headerGreeting(user.fullName),
            title = stringResource(R.string.welcome_back),
            profileName = user.fullName.ifBlank { user.email },
            onProfile = openProfile,
            photo = photo,
        )
    }, floatingActionButton = {
        // AI assistant as a compact floating button, bottom-right just above the navigation bar.
        androidx.compose.material3.FloatingActionButton(
            onClick = openTutor,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = stringResource(R.string.ai_assistant), modifier = Modifier.size(26.dp))
        }
    }) { padding ->
        LoadContent(vm.state, onRetry = vm::load, modifier = Modifier.padding(padding)) { d, fromCache ->
            val totalLessons = d.lessons.size
            val doneLessons = d.lessons.count { it.id in d.completed }
            val graded = d.attempts.filter { it.status == "submitted" && it.score != null }
            val avg = if (graded.isEmpty()) "—" else graded.sumOf { it.score!! }.pct(graded.sumOf { it.maxScore ?: 0.0 })
            val scoredCases = d.simAttempts.filter { it.status == "scored" }
            WorkspaceList(Modifier.padding(padding)) {
                item("welcome") {
                    // The greeting lives in the HomeHeader above; the list opens on the progress card.
                    ProgressStrip(
                        lessons = "$doneLessons/$totalLessons",
                        lessonFraction = if (totalLessons == 0) 0f else doneLessons / totalLessons.toFloat(),
                        average = avg,
                        cases = scoredCases.size.toString(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OfflineBanner(fromCache)
                    // Admin announcement (system_settings.system_notice); hidden when blank.
                    d.notice?.let { Banner(it, Tone.NEUTRAL, Icons.Outlined.Campaign) }
                }
                if (d.subjects.isEmpty() && !fromCache) item("join") { JoinClassCard(vm) }
                d.nextLesson?.let { lesson ->
                    item("continue") {
                        val module = d.modules.firstOrNull { it.id == lesson.moduleId }
                        FeaturePanel(
                            title = lesson.title,
                            description = listOfNotNull(module?.title, stringResource(R.string.min_read, lesson.readingMinutes)).joinToString(" · "),
                            icon = module?.let { moduleIcon(it.slug) } ?: Icons.Outlined.Fingerprint,
                            category = stringResource(R.string.continue_learning),
                            actionLabel = stringResource(R.string.resume_lesson),
                            onClick = { openLesson(lesson.id) },
                            compact = true,
                        )
                    }
                }
                item("quick") {
                    SectionHeader(stringResource(R.string.home_quick_actions))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val cs = MaterialTheme.colorScheme
                        val fx = com.forensim.app.core.designsystem.ForensimTheme.colors
                        QuickAction(Icons.Outlined._3dRotation, stringResource(R.string.home_lab), stringResource(R.string.home_lab_sub), openSimulate, Modifier.weight(1f),
                            cs.primaryContainer, cs.onPrimaryContainer)
                        QuickAction(Icons.Outlined.ViewInAr, stringResource(R.string.home_ar), stringResource(R.string.home_ar_sub), openAr, Modifier.weight(1f),
                            cs.tertiaryContainer, cs.onTertiaryContainer)
                        QuickAction(Icons.Outlined.AutoAwesome, stringResource(R.string.home_tutor), stringResource(R.string.home_tutor_sub), openTutor, Modifier.weight(1f),
                            fx.successContainer, fx.onSuccessContainer)
                    }
                }
                val waiting = d.subjects.filter { it.pending }
                if (waiting.isNotEmpty()) item("waiting") {
                    Spacer(Modifier.height(Spacing.sm))
                    Banner("Waiting for your professor to approve: " + waiting.joinToString(", ") { it.title }, Tone.ATTENTION, Icons.Outlined.HourglassTop)
                }
                val joined = d.subjects.filterNot { it.pending }
                if (joined.isNotEmpty()) item("subjects") {
                    SectionHeader(stringResource(R.string.my_subjects), trailing = {
                        com.forensim.app.core.designsystem.TextButton(onClick = openClasses) { Text(stringResource(R.string.see_all)) }
                    })
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(joined, key = { "subject:" + it.classId }) { s ->
                            val (done, total) = d.moduleProgress(s.moduleId)
                            SubjectMiniCard(s, done, total) { openModule(s.moduleId) }
                        }
                    }
                }
                if (d.pendingGraded.isNotEmpty()) {
                    item("graded-header") { SectionHeader(stringResource(R.string.graded_work)) }
                    items(d.pendingGraded, key = { "graded:${it.id}" }) { q ->
                        val used = d.attempts.count { it.quizId == q.id }
                        val open = d.attempts.any { it.quizId == q.id && it.status == "in_progress" }
                        ForensimCard(onClick = { openQuiz(q.id) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Outlined.AssignmentTurnedIn, size = 40.dp,
                                    container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                                Column(Modifier.weight(1f)) {
                                    Text(q.title, style = MaterialTheme.typography.titleMedium)
                                    val minutes = q.timeLimitSeconds?.let { stringResource(R.string.min_short, it / 60) + " · " }.orEmpty()
                                    val attempts = q.maxAttempts?.let { stringResource(R.string.attempts_left, (it - used).coerceAtLeast(0), it) }
                                        ?: stringResource(R.string.unlimited_attempts)
                                    Text(minutes + attempts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(if (open) stringResource(R.string.in_progress) else stringResource(R.string.due), if (open) Tone.ATTENTION else Tone.PRIMARY)
                            }
                        }
                    }
                }
                item("term") {
                    // Re-picked when the date changes (midnight, or reopening the app the next day).
                    val today by rememberToday()
                    TermAndQuizOfTheDay(today, remember(d, today) { d.termOfTheDay(today) }, remember(d, today) { d.dailyQuiz(today) })
                }
                if (d.practiceQuizzes.isNotEmpty()) {
                    item("practice-header") { SectionHeader(stringResource(R.string.practice_quizzes)) }
                    items(d.practiceQuizzes.take(3), key = { "practice:${it.id}" }) { q ->
                        val module = d.modules.firstOrNull { it.id == q.moduleId }
                        ForensimCard(onClick = { openQuiz(q.id) }, modifier = Modifier.padding(bottom = Spacing.xs)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(module?.let { moduleIcon(it.slug) } ?: Icons.Outlined.Fingerprint, size = 40.dp,
                                    container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                                Column(Modifier.weight(1f)) {
                                    Text(q.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(module?.title, stringResource(R.string.practice_sub)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (graded.isNotEmpty() || scoredCases.isNotEmpty()) item("results") {
                    SectionHeader(stringResource(R.string.recent_results))
                    com.forensim.app.core.designsystem.ListGroup {
                        val quizLabel = stringResource(R.string.quiz)
                        val attemptLabel = stringResource(R.string.quiz_attempt, 0).substringBefore("0")
                        val caseLabel = stringResource(R.string.case_3d, "%s")
                        val rows = graded.take(3).map { a ->
                            Triple(d.quizzes.firstOrNull { it.id == a.quizId }?.title ?: quizLabel, attemptLabel + a.attemptNumber, a.score!!.outOf(a.maxScore))
                        } + scoredCases.take(3).map { case ->
                            Triple(case.scenarioId.replace('_', ' ').replaceFirstChar { it.uppercase() }, caseLabel.replace("%s", case.mode), case.score?.pct(case.maxScore) ?: "—")
                        }
                        rows.forEachIndexed { i, (title, sub, value) ->
                            if (i > 0) com.forensim.app.core.designsystem.ListDivider(inset = Spacing.md)
                            com.forensim.app.core.designsystem.ListRow(title, subtitle = sub,
                                trailing = { com.forensim.app.core.designsystem.RowValue(value, MaterialTheme.colorScheme.onSurface) })
                        }
                    }
                }
                // Room to scroll the last card clear of the floating assistant button.
                item("fab-space") { Spacer(Modifier.height(64.dp)) }
            }
        }
    }
}


@Composable
private fun JoinClassCard(vm: StudentDashboardViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    Spacer(Modifier.height(Spacing.sm))
    ForensimCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)) {
        Text(stringResource(R.string.have_code), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' }.take(16) },
                placeholder = { Text(stringResource(R.string.join_code)) }, singleLine = true,
                isError = vm.joinError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            com.forensim.app.core.designsystem.Button(onClick = { vm.join(code) }, enabled = code.isNotBlank() && !vm.joinBusy,
                modifier = Modifier.height(56.dp)) {
                Text(if (vm.joinBusy) "…" else stringResource(R.string.join))
            }
        }
        vm.joinError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier,
                        container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    // Each card takes a soft wash of its accent colour; the icon sits on a surface tile so it pops.
    val cs = MaterialTheme.colorScheme
    val cardColor = androidx.compose.ui.graphics.lerp(cs.surfaceContainer, container, 0.6f)
    val edgeColor = androidx.compose.ui.graphics.lerp(cs.outlineVariant, content, 0.25f)
    ForensimCard(modifier = modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm + Spacing.xs),
        containerColor = cardColor, borderColor = edgeColor) {
        IconTile(icon, size = 42.dp, container = cs.surface, content = content)
        Spacer(Modifier.height(Spacing.sm + Spacing.xs))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.height(32.dp))
    }
}

/**
 * Swipeable pair: page 1 is the forensic term of the day, page 2 a one-shot quiz of the day
 * (a definition and four terms). The answer is saved per day, so it can only be answered once.
 */
@Composable
private fun TermAndQuizOfTheDay(today: java.time.LocalDate, term: Pair<String, String>, quiz: DailyQuiz) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Daily answers and the streak live on the account; load them (cached first, then server),
    // and again when a new day starts.
    val container = com.forensim.app.ui.LocalAppContainer.current
    androidx.compose.runtime.LaunchedEffect(today) { container.daily.refresh() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Both pages share the taller page's height so the card doesn't jump while swiping.
    var tallest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val pageModifier = Modifier.fillMaxWidth()
        .heightIn(min = with(density) { tallest.toDp() })
        .onSizeChanged { if (it.height > tallest) tallest = it.height }

    SectionHeader(
        stringResource(when (pager.currentPage) { 0 -> R.string.term_of_day; 1 -> R.string.quiz_of_day; else -> R.string.evidence_of_day }),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val active = pager.currentPage == i
                    val width by androidx.compose.animation.core.animateDpAsState(if (active) 20.dp else 8.dp, label = "dot")
                    Box(Modifier.height(8.dp).width(width).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        .clickable { scope.launch { pager.animateScrollToPage(i) } })
                }
            }
        },
    )
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager, pageSpacing = Spacing.sm, beyondViewportPageCount = 2,
        verticalAlignment = Alignment.Top,
    ) { page ->
        if (page == 0) TermOfTheDayCard(term, pageModifier) { scope.launch { pager.animateScrollToPage(1) } }
        else if (page == 1) QuizOfTheDayCard(quiz, pageModifier)
        else EvidenceOfTheDayCard(today, pageModifier)
    }
}

@Composable
private fun TermOfTheDayCard(term: Pair<String, String>, modifier: Modifier, onNext: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Box(
        modifier.clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(cs.tertiaryContainer, cs.surfaceContainer)))
            .border(1.dp, cs.outlineVariant, shape),
    ) {
        // Faint fingerprint watermark in the corner.
        Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = cs.onTertiaryContainer.copy(alpha = 0.07f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.sm, bottom = Spacing.sm).size(120.dp))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(Icons.Outlined.Lightbulb, size = 48.dp, container = cs.surface, content = cs.onTertiaryContainer)
            Spacer(Modifier.height(Spacing.md))
            Text(term.first, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = cs.onSurface)
            Spacer(Modifier.height(Spacing.sm))
            Box(Modifier.width(32.dp).height(3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(cs.primary))
            Spacer(Modifier.height(Spacing.sm + Spacing.xs))
            Text(term.second, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onNext)
                    .background(cs.surface.copy(alpha = 0.7f)).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.swipe_for_quiz), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private const val DAILY_QUIZ_PREFS = "daily_quiz"

@Composable
private fun QuizOfTheDayCard(quiz: DailyQuiz, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val fx = com.forensim.app.core.designsystem.ForensimTheme.colors
    // Today's answer and the streak come from the student's account (DailyRepository).
    val daily = com.forensim.app.ui.LocalAppContainer.current.daily
    val entries by daily.entries.collectAsStateWithLifecycle()
    val today = java.time.LocalDate.ofEpochDay(quiz.day)
    val todayEntry = entries[today.toString()]
    val choice = todayEntry?.quizChoice
    val answered = choice != null
    val streak = com.forensim.app.core.data.DailyRepository.streak(entries, today)
    val best = com.forensim.app.core.data.DailyRepository.best(entries)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shape = MaterialTheme.shapes.large
    Column(
        modifier.clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(cs.primaryContainer, cs.surfaceContainer)))
            .border(1.dp, cs.outlineVariant, shape)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StreakBadge(streak, best, answeredToday = answered, correctToday = todayEntry?.quizCorrect ?: (choice == quiz.answer))
        Spacer(Modifier.height(Spacing.md))
        Text(stringResource(R.string.quiz_of_day_question), style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text("“" + quiz.definition + "”", style = MaterialTheme.typography.bodyMedium,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(cs.surface.copy(alpha = 0.7f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xs))
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            quiz.options.forEachIndexed { i, option ->
                val isAnswer = option == quiz.answer
                val isChoice = option == choice
                val (bg, edge) = when {
                    answered && isAnswer -> fx.successContainer to fx.success
                    answered && isChoice -> cs.errorContainer to cs.error
                    else -> cs.surface to cs.outlineVariant
                }
                val optionShape = MaterialTheme.shapes.medium
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
                        .alpha(if (answered && !isAnswer && !isChoice) 0.5f else 1f)
                        .clip(optionShape).background(bg).border(1.dp, edge, optionShape)
                        .clickable(enabled = !answered) {
                            scope.launch { daily.answerQuiz(today, option, option == quiz.answer) }
                        }
                        .padding(horizontal = Spacing.sm + Spacing.xs, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm + Spacing.xs),
                ) {
                    Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (answered && (isAnswer || isChoice)) edge else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center) {
                        when {
                            answered && isAnswer -> Icon(Icons.Outlined.Check, null, tint = cs.surface, modifier = Modifier.size(16.dp))
                            answered && isChoice -> Icon(Icons.Outlined.Close, null, tint = cs.surface, modifier = Modifier.size(16.dp))
                            else -> Text(('A' + i).toString(), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                    Text(option, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm + Spacing.xs))
        val correct = choice == quiz.answer
        Text(
            when {
                !answered -> stringResource(R.string.quiz_of_day_one_try)
                correct -> stringResource(R.string.quiz_of_day_correct)
                else -> stringResource(R.string.quiz_of_day_wrong, quiz.answer)
            },
            style = if (answered) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = when { !answered -> cs.onSurfaceVariant; correct -> fx.success; else -> cs.error },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SubjectMiniCard(s: com.forensim.app.core.model.SubjectClass, done: Int, total: Int, onClick: () -> Unit) {
    // Framed like the professor's class card: their cover photo, or else their class color.
    val tint = com.forensim.app.feature.classes.classTint(com.forensim.app.feature.classes.ClassColor.from(s.color))
    ForensimCard(modifier = Modifier.width(240.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
      com.forensim.app.feature.classes.ClassCardBackground(s.coverPath, tint) { colors ->
       androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconTile(moduleIcon(s.slug), size = 44.dp, container = colors.inner, content = colors.content)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.subject_n, s.position), style = MaterialTheme.typography.labelMedium, color = colors.variant)
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = colors.content, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(40.dp))
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (total == 0) {
            Text(stringResource(R.string.no_lessons_unlocked), style = MaterialTheme.typography.bodySmall, color = colors.variant)
        } else {
            ProgressRow(stringResource(R.string.lessons), done / total.toFloat(), stringResource(R.string.done_of, done, total),
                detailColor = colors.variant, barColor = colors.content, trackColor = colors.track)
        }
       }
      }
    }
}

/** Three headline numbers on one card: lessons (with a ring), quiz average, cases scored. */
@Composable
private fun ProgressStrip(lessons: String, lessonFraction: Float, average: String, cases: String) {
    val cs = MaterialTheme.colorScheme
    ForensimCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1.2f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    com.forensim.app.core.designsystem.ScoreRing(lessonFraction, cs.primary, 40.dp, stroke = 5.dp)
                    Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                StripValue(lessons, stringResource(R.string.lessons))
            }
            androidx.compose.material3.VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = cs.outlineVariant)
            StripValue(average, stringResource(R.string.quiz_average), Modifier.weight(1f).padding(start = Spacing.md))
            androidx.compose.material3.VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 4.dp), color = cs.outlineVariant)
            StripValue(cases, stringResource(R.string.cases), Modifier.weight(1f).padding(start = Spacing.md))
        }
    }
}

@Composable
private fun StripValue(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = com.forensim.app.core.designsystem.TabularNumbers), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
