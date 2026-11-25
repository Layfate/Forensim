package com.forensim.app.feature.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CheckCircle
import com.forensim.app.core.designsystem.DashboardStats
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.ProgressRow
import com.forensim.app.core.designsystem.ResultHero
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ErrorState
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.LoadingState
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.designsystem.outOf
import com.forensim.app.core.model.PracticeFeedback
import com.forensim.app.core.model.Question
import com.forensim.app.core.model.Quiz
import com.forensim.app.core.model.QuizAttempt
import com.forensim.app.core.model.ReviewItem
import com.forensim.app.core.model.SubmitResult
import com.forensim.app.core.network.ApiException
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface QuizStage {
    data object Loading : QuizStage
    data class Failed(val message: String) : QuizStage
    data class Practice(val quiz: Quiz, val questions: List<Question>) : QuizStage
    data class Intro(val quiz: Quiz, val used: Int, val open: QuizAttempt?, val history: List<QuizAttempt>) : QuizStage
    data class Attempt(val quiz: Quiz, val attempt: QuizAttempt, val questions: List<Question>) : QuizStage
    data class Result(val quiz: Quiz, val result: SubmitResult, val review: List<ReviewItem>?, val questions: List<Question>) : QuizStage
}

class QuizViewModel(private val c: AppContainer, private val quizId: String) : ViewModel() {
    var stage by mutableStateOf<QuizStage>(QuizStage.Loading); private set
    val answers = mutableStateMapOf<String, String>()
    var index by mutableIntStateOf(0)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    // practice
    val feedback = mutableStateMapOf<String, PracticeFeedback>()

    init { load() }

    fun load() {
        stage = QuizStage.Loading
        viewModelScope.launch {
            stage = try {
                val quiz = c.learning.quiz(quizId)
                if (!quiz.isGraded) {
                    QuizStage.Practice(quiz, c.learning.questions(quizId))
                } else {
                    val mine = c.learning.myAttempts().filter { it.quizId == quizId }
                    QuizStage.Intro(quiz, mine.size, mine.firstOrNull { it.status == "in_progress" }, mine.filter { it.status == "submitted" })
                }
            } catch (e: ApiException) {
                QuizStage.Failed(e.message)
            }
        }
    }

    fun checkPractice(q: Question) {
        val choice = answers[q.id] ?: return
        busy = true
        viewModelScope.launch {
            try {
                feedback[q.id] = c.learning.checkPracticeAnswer(q.id, choice)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun startOrResume(quiz: Quiz) {
        busy = true
        viewModelScope.launch {
            try {
                val attempt = c.learning.createAttempt(quiz.id)
                val questions = c.learning.questions(quiz.id)
                answers.clear()
                c.learning.responses(attempt.id).forEach { answers[it.questionId] = it.optionId }
                index = 0
                stage = QuizStage.Attempt(quiz, attempt, questions)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun choose(attemptId: String, questionId: String, optionId: String) {
        val previous = answers[questionId]
        answers[questionId] = optionId
        viewModelScope.launch {
            try {
                c.learning.saveResponse(attemptId, questionId, optionId)
            } catch (e: ApiException) {
                if (previous == null) answers.remove(questionId) else answers[questionId] = previous
                error = "Answer not saved: ${e.message}"
            }
        }
    }

    fun submit() {
        val s = stage as? QuizStage.Attempt ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = c.learning.submitAttempt(s.attempt.id)
                val review = if (result.reviewAvailable) runCatching { c.learning.review(s.attempt.id) }.getOrNull() else null
                stage = QuizStage.Result(s.quiz, result, review, s.questions)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun QuizScreen(quizId: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "quiz_$quizId") { QuizViewModel(it, quizId) }
    var confirmExit by remember { mutableStateOf(false) }
    val inAttempt = vm.stage is QuizStage.Attempt
    BackHandler(inAttempt) { confirmExit = true }
    val title = when (val s = vm.stage) {
        is QuizStage.Practice -> s.quiz.title
        is QuizStage.Intro -> s.quiz.title
        is QuizStage.Attempt -> s.quiz.title
        is QuizStage.Result -> s.quiz.title
        else -> "Quiz"
    }
    ScreenScaffold(title = title, onBack = { if (inAttempt) confirmExit = true else onBack() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            vm.error?.let {
                Banner(it, Tone.ERROR, modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs))
                LaunchedEffect(it) { delay(5000); vm.error = null }
            }
            when (val s = vm.stage) {
                QuizStage.Loading -> LoadingState()
                is QuizStage.Failed -> ErrorState(s.message, vm::load)
                is QuizStage.Practice -> PracticeContent(vm, s, onBack)
                is QuizStage.Intro -> IntroContent(vm, s)
                is QuizStage.Attempt -> AttemptContent(vm, s)
                is QuizStage.Result -> ResultContent(s, onBack)
            }
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this attempt?") },
            text = { Text("Your answers are saved and the timer keeps running on the server. You can resume before time runs out.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Stay") } },
        )
    }
}

@Composable
private fun OptionList(question: Question, selected: String?, enabled: Boolean, correctId: String? = null, onSelect: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fx = ForensimTheme.colors
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
        question.options.sortedBy { it.position }.forEachIndexed { index, o ->
            val isSel = selected == o.id
            val isCorrect = correctId != null && o.id == correctId
            val isWrong = correctId != null && isSel && !isCorrect
            val accent = when {
                isCorrect -> fx.success
                isWrong -> cs.error
                isSel -> cs.primary
                else -> null
            }
            val container = when {
                isCorrect -> fx.successContainer
                isWrong -> cs.errorContainer
                isSel -> cs.primaryContainer
                else -> cs.surfaceContainer
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = container,
                border = BorderStroke(if (accent != null) 2.dp else 1.dp, accent ?: cs.outlineVariant),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    .selectable(selected = isSel, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(o.id) }),
            ) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Letter badge: filled when chosen, tinted green/red once the answer is checked.
                    Box(
                        Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(accent ?: cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isCorrect -> Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.surface, modifier = Modifier.size(20.dp))
                            isWrong -> Icon(Icons.Outlined.Close, contentDescription = null, tint = cs.surface, modifier = Modifier.size(20.dp))
                            else -> Text(('A' + index).toString(), style = MaterialTheme.typography.titleSmall,
                                color = if (isSel) cs.onPrimary else cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.body, style = MaterialTheme.typography.bodyLarge)
                        if (isCorrect || isWrong) Text(if (isCorrect) "Correct answer" else "Your answer",
                            style = MaterialTheme.typography.labelMedium, color = if (isCorrect) fx.onSuccessContainer else cs.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeContent(vm: QuizViewModel, s: QuizStage.Practice, onDone: () -> Unit) {
    if (s.questions.isEmpty()) { ErrorState("This quiz has no questions yet.", null); return }
    val i = vm.index.coerceIn(0, s.questions.lastIndex)
    val q = s.questions[i]
    val fb = vm.feedback[q.id]
    val finished = vm.feedback.size == s.questions.size
    var showSummary by rememberSaveable(s.quiz.id) { mutableStateOf(false) }
    if (finished && showSummary) {
        PracticeSummary(s, vm.feedback.values.count { it.correct },
            onRetry = { vm.feedback.clear(); vm.answers.clear(); vm.index = 0; showSummary = false }, onDone = onDone)
        return
    }
    val scroll = rememberScrollState()
    LaunchedEffect(q.id) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(Spacing.md)) {
        ReadableColumn {
            // Fills up to this question once it is answered, so choosing an answer slides the bar right.
            ProgressRow("Practice quiz", (i + if (vm.answers[q.id] != null) 1f else 0f) / s.questions.size, "${i + 1} of ${s.questions.size}")
            Spacer(Modifier.height(Spacing.md))
            PageIntro(q.prompt)
            OptionList(q, vm.answers[q.id], enabled = fb == null, correctId = fb?.correctOptionId) { vm.answers[q.id] = it }
            if (fb != null) {
                Spacer(Modifier.height(Spacing.md))
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Banner((if (fb.correct) "Correct. " else "Not quite. ") + fb.explanation, if (fb.correct) Tone.SUCCESS else Tone.ATTENTION)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { vm.index = i - 1 }, enabled = i > 0, modifier = Modifier.weight(1f).heightIn(min = Spacing.touchTarget)) { Text("Previous") }
                if (fb == null) {
                    PrimaryButton("Check answer", onClick = { vm.checkPractice(q) }, enabled = vm.answers[q.id] != null, loading = vm.busy, modifier = Modifier.weight(1f))
                } else {
                    val hasNext = i < s.questions.lastIndex
                    PrimaryButton(
                        if (hasNext) "Next" else if (finished) "See results" else "Next unanswered",
                        onClick = {
                            when {
                                hasNext -> vm.index = i + 1
                                finished -> showSummary = true
                                else -> vm.index = s.questions.indexOfFirst { it.id !in vm.feedback }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        }
    }
}

@Composable
private fun IntroContent(vm: QuizViewModel, s: QuizStage.Intro) {
    val left = s.quiz.maxAttempts?.let { it - s.used }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            FeaturePanel(s.quiz.title, "Review the time limit and remaining attempts before you begin.",
                Icons.Outlined.AssignmentTurnedIn, category = "Graded quiz")
            Spacer(Modifier.height(Spacing.md))
            DashboardStats("Time limit" to (s.quiz.timeLimitSeconds?.let { "${it / 60} min" } ?: "None"),
                "Attempts left" to (left?.coerceAtLeast(0)?.toString() ?: "Unlimited"))
            Spacer(Modifier.height(Spacing.md))
            Banner("The AI tutor and hints are locked while the attempt is open. Answers save automatically and are graded on the server when you submit.",
                Tone.ATTENTION, Icons.Outlined.Lock)
            Spacer(Modifier.height(Spacing.lg))
            when {
                s.open != null -> PrimaryButton("Resume attempt ${s.open.attemptNumber}", onClick = { vm.startOrResume(s.quiz) }, loading = vm.busy, modifier = Modifier.fillMaxWidth())
                left != null && left <= 0 -> Banner("You have used all attempts for this quiz.", Tone.NEUTRAL)
                else -> PrimaryButton("Start attempt", onClick = { vm.startOrResume(s.quiz) }, loading = vm.busy, modifier = Modifier.fillMaxWidth())
            }
            if (s.history.isNotEmpty()) {
                SectionHeader("Previous attempts")
                s.history.forEach { a ->
                    ForensimCard(Modifier.padding(bottom = Spacing.sm)) {
                        Row {
                            Text("Attempt ${a.attemptNumber}" + if (a.late) " (late)" else "", Modifier.weight(1f))
                            Text(a.score?.outOf(a.maxScore) ?: "-", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptContent(vm: QuizViewModel, s: QuizStage.Attempt) {
    var confirm by remember { mutableStateOf(false) }
    val deadline = remember(s.attempt.id) { s.attempt.deadlineAt?.let { runCatching { Instant.parse(it) }.getOrNull() ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() } }
    var remaining by remember { mutableLongStateOf(deadline?.let { it.epochSecond - Instant.now().epochSecond } ?: -1) }
    LaunchedEffect(deadline) {
        if (deadline == null) return@LaunchedEffect
        while (true) {
            remaining = deadline.epochSecond - Instant.now().epochSecond
            if (remaining <= 0) { vm.submit(); break }
            delay(1000)
        }
    }
    val q = s.questions.getOrNull(vm.index.coerceIn(0, (s.questions.size - 1).coerceAtLeast(0)))
    val scroll = rememberScrollState()
    LaunchedEffect(q?.id) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("${vm.answers.size} of ${s.questions.size} answered", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (remaining >= 0) {
                val urgent = remaining < 60
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (urgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 2.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("%d:%02d".format(remaining / 60, remaining % 60),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = com.forensim.app.core.designsystem.TabularNumbers),
                            modifier = Modifier.semantics { contentDescription = "${remaining / 60} minutes ${remaining % 60} seconds left" })
                    }
                }
            }
        }
        if (q == null) { ErrorState("No questions available.", null); return }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = Spacing.md)) {
            ReadableColumn {
                Spacer(Modifier.height(Spacing.sm))
                // Fills up to this question once it is answered, so choosing an answer slides the bar right.
                ProgressRow("Question", (vm.index + if (vm.answers[q.id] != null) 1f else 0f) / s.questions.size, "${vm.index + 1} of ${s.questions.size}")
                Spacer(Modifier.height(Spacing.md))
                PageIntro(q.prompt)
                OptionList(q, vm.answers[q.id], enabled = !vm.busy) { vm.choose(s.attempt.id, q.id, it) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { vm.index-- }, enabled = vm.index > 0, modifier = Modifier.weight(1f).heightIn(min = Spacing.touchTarget)) { Text("Previous") }
            if (vm.index < s.questions.lastIndex) {
                PrimaryButton("Next", onClick = { vm.index++ }, modifier = Modifier.weight(1f))
            } else {
                PrimaryButton("Submit", onClick = { confirm = true }, loading = vm.busy, modifier = Modifier.weight(1f))
            }
        }
    }
    if (confirm) {
        val unanswered = s.questions.size - vm.answers.size
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Submit for grading?") },
            text = { Text(if (unanswered > 0) "$unanswered question(s) are unanswered and will score zero. You can't change answers after submitting." else "You can't change answers after submitting.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.submit() }) { Text("Submit") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep working") } },
        )
    }
}

@Composable
private fun ResultContent(s: QuizStage.Result, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            val fraction = s.result.score?.let { sc -> s.result.maxScore?.takeIf { it > 0 }?.let { sc / it } } ?: 0.0
            val celebrate = fraction >= CELEBRATE_AT
            val correct = s.review?.count { it.chosenOptionId == it.correctOptionId }
            ResultHero(
                celebrate = celebrate,
                eyebrow = if (celebrate) "Case closed" else "Quiz submitted",
                headline = when {
                    fraction >= 0.9 -> "Outstanding work, Detective"
                    celebrate -> "Nice work, Detective"
                    else -> "Keep investigating"
                },
                message = if (celebrate) "Your answers held up to scrutiny. Your score is recorded for ${s.quiz.title}."
                    else "Every case teaches something. Go through the review below, then revisit the lesson.",
                score = s.result.score,
                maxScore = s.result.maxScore,
                stats = listOfNotNull(
                    correct?.let { "Correct answers" to "$it of ${s.review.size}" },
                    "Status" to if (s.result.late) "Submitted late" else "On time",
                ),
            )
            if (s.result.late) { Spacer(Modifier.height(Spacing.sm)); Banner("Submitted after the time limit; your professor can see this.", Tone.ATTENTION) }
            val review = s.review
            if (review == null) {
                Spacer(Modifier.height(Spacing.md))
                Banner("Detailed review will be available when your professor releases it.", Tone.NEUTRAL)
            } else {
                SectionHeader("Review")
                review.forEachIndexed { i, r ->
                    val q = s.questions.firstOrNull { it.id == r.questionId }
                    val right = r.chosenOptionId == r.correctOptionId
                    ForensimCard(Modifier.padding(bottom = Spacing.sm)) {
                        Text("${i + 1}. ${r.prompt}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Spacing.xs))
                        val chosen = q?.options?.firstOrNull { it.id == r.chosenOptionId }?.body ?: "No answer"
                        val correct = q?.options?.firstOrNull { it.id == r.correctOptionId }?.body ?: ""
                        Text((if (right) "Correct: " else "Your answer: ") + chosen, style = MaterialTheme.typography.bodyMedium,
                            color = if (right) ForensimTheme.colors.success else MaterialTheme.colorScheme.error)
                        if (!right) Text("Correct answer: $correct", style = MaterialTheme.typography.bodyMedium)
                        if (r.explanation.isNotBlank()) Text(r.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton("Back to module", onClick = onBack, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/** Results at or above this fraction get the success animation. Not a pass mark; quizzes have none. */
private const val CELEBRATE_AT = 0.75

/** Shown when a practice quiz is finished: same celebration as graded results, but marked ungraded. */
@Composable
private fun PracticeSummary(s: QuizStage.Practice, correct: Int, onRetry: () -> Unit, onDone: () -> Unit) {
    val total = s.questions.size
    val celebrate = total > 0 && correct.toDouble() / total >= CELEBRATE_AT
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            ResultHero(
                celebrate = celebrate,
                eyebrow = if (celebrate) "Practice complete" else "Practice finished",
                headline = if (celebrate) "Sharp eye, Detective" else "Good practice run",
                message = if (celebrate) "You're ready for the graded work on this topic. Practice results are not graded."
                    else "Try the set again or review the lesson first. Practice results are not graded.",
                score = correct.toDouble(),
                maxScore = total.toDouble(),
                stats = listOf("Correct answers" to "$correct of $total"),
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            OutlinedButton(onClick = onRetry, shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Practice again") }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
