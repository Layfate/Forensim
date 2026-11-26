package com.forensim.app.feature.professor

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import com.forensim.app.core.designsystem.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.forensim.app.core.designsystem.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forensim.app.core.data.QuizDraftQuestion
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.IconTile
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_PDF_BYTES = 25L * 1024 * 1024

/** Dropdown of the professor's subject classes: which class new material (PDF, quiz, case) goes to. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ClassPicker(d: TeachingData, selected: String?, onSelect: (String) -> Unit) {
    SectionHeader("Class")
    if (d.classes.isEmpty()) {
        Banner("Create a subject class first (Classes tab). Material always belongs to one of your classes.", Tone.ATTENTION)
        return
    }
    fun label(k: com.forensim.app.core.model.CourseClass) = d.moduleOf(k)?.let { "${it.position}. ${it.title}" } ?: k.name
    var open by remember { mutableStateOf(false) }
    val current = selected?.let { d.classById(it) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = current?.let(::label) ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Class") },
            placeholder = { Text("Choose a class") },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            d.sortedClasses.forEach { k ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label(k), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(k.id); open = false },
                    trailingIcon = if (k.id == selected) ({ Icon(Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                    contentPadding = androidx.compose.material3.ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------- PDF upload

@Composable
fun UploadPdfScreen(vm: ProfessorViewModel, classId: String?, onBack: () -> Unit) {
    ScreenScaffold(title = "Upload PDF module", onBack = onBack) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            var selected by rememberSaveable { mutableStateOf(classId ?: d.sortedClasses.firstOrNull()?.id) }
            var title by rememberSaveable { mutableStateOf("") }
            var minutes by rememberSaveable { mutableStateOf("10") }
            var pdfUri by rememberSaveable { mutableStateOf<String?>(null) }
            var pdfName by rememberSaveable { mutableStateOf("") }
            var pdfSize by rememberSaveable { mutableStateOf(0L) }
            var localError by remember { mutableStateOf<String?>(null) }

            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        pdfName = c.getString(0) ?: "document.pdf"
                        pdfSize = if (c.isNull(1)) 0L else c.getLong(1)
                    }
                }
                pdfUri = uri.toString()
                localError = if (pdfSize > MAX_PDF_BYTES) "This PDF is larger than 25 MB. Compress it or split it into smaller files." else null
                if (title.isBlank()) title = pdfName.substringBeforeLast('.').replace('_', ' ').trim()
            }

            val target = selected?.let { d.classById(it) }
            val ready = target != null && title.trim().length >= 3 && pdfUri != null && pdfSize <= MAX_PDF_BYTES && !vm.publishing

            Column(Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                ReadableColumn {
                    PageIntro("Upload a PDF", Modifier.padding(top = Spacing.md), description = "It is unlocked for the class you choose as soon as the upload finishes.")
                    ClassPicker(d, selected) { selected = it }
                    SectionHeader("Lesson")
                    OutlinedTextField(title, { title = it }, label = { Text("Lesson title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.xs))
                    OutlinedTextField(minutes, { v -> minutes = v.filter(Char::isDigit).take(3) }, label = { Text("Reading time (minutes)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.md))
                    ForensimCard(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            IconTile(Icons.Outlined.PictureAsPdf, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(if (pdfUri == null) "Choose a PDF" else pdfName, style = MaterialTheme.typography.titleSmall)
                                Text(if (pdfUri == null) "Up to 25 MB" else "%.1f MB · tap to change".format(pdfSize / 1_048_576.0),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    (localError ?: vm.publishError)?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
                    Spacer(Modifier.height(Spacing.lg))
                    PrimaryButton(
                        text = if (vm.publishing) "Uploading" else "Publish PDF",
                        loading = vm.publishing,
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = {
                            val uri = Uri.parse(pdfUri ?: return@PrimaryButton)
                            val t = target ?: return@PrimaryButton
                            scope.launch {
                                val bytes = runCatching {
                                    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                                }.getOrNull()
                                when {
                                    bytes == null -> localError = "Couldn't read that file. Choose it again."
                                    bytes.size > MAX_PDF_BYTES -> localError = "This PDF is larger than 25 MB."
                                    !(bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF") -> localError = "That file isn't a PDF."
                                    else -> {
                                        localError = null
                                        vm.uploadPdf(t, title, minutes.toIntOrNull() ?: 10, bytes, onDone = onBack)
                                    }
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------- quiz builder

/**
 * The quiz being written. Lives in a view model tied to the Create quiz screen, so nothing typed
 * is lost while the professor is on the separate "Generate with AI" page and comes back.
 */
class QuizDraftViewModel : androidx.lifecycle.ViewModel() {
    var title by mutableStateOf("")
    var graded by mutableStateOf(false)
    var minutes by mutableStateOf("")
    var attempts by mutableStateOf("2")
    internal val questions = mutableStateListOf(DraftQuestion())
    /** How many AI questions were just added (shows the "check them" note). */
    var aiAdded by mutableStateOf<Int?>(null)

    fun addGenerated(draft: com.forensim.app.core.data.GeneratedQuiz) {
        if (title.isBlank() && draft.title.isNotBlank()) title = draft.title
        val fresh = draft.questions.map(DraftQuestion::from)
        // Replace the untouched starter question; otherwise add after what's already written.
        if (questions.all { it.blank }) questions.clear()
        questions.addAll(fresh)
        aiAdded = fresh.size
    }
}

internal class DraftQuestion {
    var prompt by mutableStateOf("")
    val options = mutableStateListOf("", "")
    var correct by mutableIntStateOf(-1)
    var explanation by mutableStateOf("")

    val complete: Boolean get() = prompt.isNotBlank() && options.size >= 2 && options.all { it.isNotBlank() } && correct in options.indices
    val blank: Boolean get() = prompt.isBlank() && options.all { it.isBlank() } && explanation.isBlank()
    fun toDraft() = QuizDraftQuestion(prompt, options.toList(), correct, explanation)

    companion object {
        fun from(g: com.forensim.app.core.data.GeneratedQuestion) = DraftQuestion().apply {
            prompt = g.prompt
            options.clear(); options.addAll(g.options.take(6))
            correct = g.correct.takeIf { it in options.indices } ?: -1
            explanation = g.explanation
        }
    }
}

@Composable
fun CreateQuizScreen(vm: ProfessorViewModel, classId: String?, onBack: () -> Unit, openAiGenerator: (String?) -> Unit) {
    val draft = androidx.lifecycle.viewmodel.compose.viewModel { QuizDraftViewModel() }
    // Questions drafted on the AI page arrive here once, then land in the editor below.
    androidx.compose.runtime.LaunchedEffect(vm.aiDraft) {
        vm.aiDraft?.let { draft.addGenerated(it); vm.aiDraft = null }
    }
    // Hoisted so the header's "Generate with AI" button knows which class (subject) to draft for.
    var selected by rememberSaveable { mutableStateOf(classId) }
    ScreenScaffold(title = "Create quiz", onBack = onBack, actions = {
        com.forensim.app.core.designsystem.Button(
            onClick = { openAiGenerator(selected) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            modifier = Modifier.padding(end = Spacing.sm).height(38.dp),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Generate with AI", style = MaterialTheme.typography.labelLarge)
        }
    }) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            if (selected == null) selected = d.sortedClasses.firstOrNull()?.id
            var title by draft::title
            var graded by draft::graded
            var minutes by draft::minutes
            var attempts by draft::attempts
            val questions = draft.questions
            val aiAdded = draft.aiAdded

            val target = selected?.let { d.classById(it) }
            val incomplete = questions.indexOfFirst { !it.complete }
            val ready = target != null && title.trim().length >= 3 && incomplete < 0 && !vm.publishing

            Column(Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                ReadableColumn {
                    PageIntro("New quiz", Modifier.padding(top = Spacing.md), description = "Multiple choice, for one of your classes only. Answers are kept on the server and graded there.")
                    ClassPicker(d, selected) { selected = it }
                    aiAdded?.let { n ->
                        var reportingDraft by remember { mutableStateOf(false) }
                        var reportNotice by remember { mutableStateOf<String?>(null) }
                        Spacer(Modifier.height(Spacing.sm))
                        Banner("Added $n AI-drafted question${if (n == 1) "" else "s"}. Check each question, answer and explanation before publishing.", Tone.ATTENTION)
                        // Google Play AI-Generated Content policy: offensive or wrong AI output can be reported in the app.
                        TextButton(onClick = { reportingDraft = true }) { Text("Report a problem with the AI draft") }
                        reportNotice?.let { Banner(it, Tone.NEUTRAL) }
                        if (reportingDraft) {
                            com.forensim.app.feature.report.ReportDialog(
                                title = "Report the AI draft",
                                explanation = "Tell us what's wrong with the AI-generated questions. The current questions are sent to the administrators for review.",
                                reasons = com.forensim.app.feature.report.AI_REASONS,
                                onDismiss = { reportingDraft = false },
                                onSubmit = { reason, details ->
                                    reportingDraft = false
                                    val excerpt = questions.mapIndexed { i, q ->
                                        "${i + 1}. ${q.prompt}\n" + q.options.mapIndexed { j, o -> "  ${if (j == q.correct) "*" else "-"} $o" }.joinToString("\n")
                                    }.joinToString("\n")
                                    vm.reportAiDraft(reason, details, excerpt) { reportNotice = it }
                                },
                            )
                        }
                    }
                    SectionHeader("Settings")
                    OutlinedTextField(title, { title = it }, label = { Text("Quiz title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FilterChip(selected = !graded, onClick = { graded = false }, label = { Text("Practice") })
                        FilterChip(selected = graded, onClick = { graded = true }, label = { Text("Graded") })
                    }
                    Text(if (graded) "Counts toward grades. Questions stay hidden until a student starts an attempt."
                        else "Instant feedback after each answer. Not graded.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (graded) {
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            OutlinedTextField(minutes, { v -> minutes = v.filter(Char::isDigit).take(3) }, label = { Text("Time limit (min)") },
                                placeholder = { Text("None") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(attempts, { v -> attempts = v.filter(Char::isDigit).take(2) }, label = { Text("Attempts") },
                                placeholder = { Text("Unlimited") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                    }

                    questions.forEachIndexed { qi, q ->
                        SectionHeader("Question ${qi + 1}", trailing = if (questions.size > 1) ({
                            IconButton(onClick = { questions.removeAt(qi) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove question ${qi + 1}")
                            }
                        }) else null)
                        QuestionEditor(q)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedButton(onClick = { questions.add(DraftQuestion()) }, enabled = questions.size < 100,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Add question")
                    }

                    Spacer(Modifier.height(Spacing.md))
                    when {
                        vm.publishError != null -> Banner(vm.publishError!!, Tone.ERROR)
                        incomplete >= 0 && title.isNotBlank() ->
                            Banner("Question ${incomplete + 1}: fill in the question and every choice, and mark the correct answer.", Tone.NEUTRAL)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = if (vm.publishing) "Publishing" else "Publish quiz (${questions.size} question${if (questions.size == 1) "" else "s"})",
                        loading = vm.publishing,
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = {
                            vm.createQuiz(target ?: return@PrimaryButton, title, graded,
                                if (graded) minutes.toIntOrNull()?.takeIf { it > 0 } else null,
                                if (graded) attempts.toIntOrNull()?.takeIf { it > 0 } else null,
                                questions.map { it.toDraft() }, onDone = onBack)
                        },
                    )
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun QuestionEditor(q: DraftQuestion) {
    ForensimCard {
        OutlinedTextField(q.prompt, { q.prompt = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(Spacing.sm))
        Text("Choices — select the correct one", style = MaterialTheme.typography.labelLarge)
        q.options.forEachIndexed { oi, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = q.correct == oi, onClick = { q.correct = oi },
                    modifier = Modifier.semantics { contentDescription = "Mark choice ${oi + 1} correct" })
                OutlinedTextField(text, { q.options[oi] = it }, label = { Text("Choice ${oi + 1}") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                if (q.options.size > 2) {
                    IconButton(onClick = {
                        q.options.removeAt(oi)
                        q.correct = when {
                            q.correct == oi -> -1
                            q.correct > oi -> q.correct - 1
                            else -> q.correct
                        }
                    }) { Icon(Icons.Outlined.Close, contentDescription = "Remove choice ${oi + 1}") }
                }
            }
        }
        if (q.options.size < 6) {
            TextButton(onClick = { q.options.add("") }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.xs))
                Text("Add choice")
            }
        }
        OutlinedTextField(q.explanation, { q.explanation = it }, label = { Text("Explanation shown after answering (optional)") },
            modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

// ----------------------------------------------------------------------------- AI quiz generator

/**
 * "Generate quiz with AI" page: drafts questions from a topic or from a PDF. PDF text is extracted
 * on the phone and only the text is sent. The draft goes back to Create quiz for review.
 */
@Composable
fun AiQuizScreen(vm: ProfessorViewModel, classId: String?, onBack: () -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    ScreenScaffold(title = "Generate with AI", onBack = onBack) { inner ->
        LoadContent(vm.state, vm::load, Modifier.padding(inner)) { d, _ ->
            val subject = classId?.let { d.classById(it) }?.let { d.moduleOf(it)?.title }
            Column(Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md)) {
                ReadableColumn {
                    PageIntro("Generate quiz with AI", Modifier.padding(top = Spacing.md), description = "Draft questions from a topic or a PDF. They are added to your quiz, " +
                        "where you check each question, answer and explanation before publishing.")
                    AiQuizGenerator(vm, subjectHint = subject) { generated ->
                        // Hand the questions to Create quiz. Only go back if this page is still open;
                        // if the professor already left while it was generating, don't pop a second screen.
                        vm.aiDraft = generated
                        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onBack()
                    }
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
        }
    }
}

@Composable
private fun AiQuizGenerator(vm: ProfessorViewModel, subjectHint: String?, onDraft: (com.forensim.app.core.data.GeneratedQuiz) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fromPdf by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(10) }
    var difficulty by rememberSaveable { mutableStateOf("medium") }
    var pdfUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pdfName by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pdfName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "document.pdf"
        pdfUri = uri.toString(); localError = null
    }
    val busy = reading || vm.generating
    val ready = !busy && if (fromPdf) pdfUri != null else topic.trim().length >= 3

    SectionHeader("Source")
    ForensimCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(selected = !fromPdf, onClick = { fromPdf = false; localError = null }, label = { Text("From a topic") })
            FilterChip(selected = fromPdf, onClick = { fromPdf = true; localError = null }, label = { Text("From a PDF") })
        }
        Spacer(Modifier.height(Spacing.sm))
        if (fromPdf) {
            ForensimCard(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    IconTile(Icons.Outlined.PictureAsPdf, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(if (pdfUri == null) "Choose a PDF" else pdfName, style = MaterialTheme.typography.titleSmall)
                        Text(if (pdfUri == null) "Text PDFs work best. The first 60 pages are used." else "Tap to change",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(topic, { topic = it.take(600) }, label = { Text("Focus (optional)") },
                placeholder = { Text("e.g. only chapter 2, chain of custody") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(topic, { topic = it.take(600) }, label = { Text("Topic or instructions") },
                placeholder = { Text(subjectHint?.let { "e.g. key concepts of $it" } ?: "e.g. fingerprint ridge patterns") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("Questions", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(5, 10, 15, 20).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) }
        }
        Text("Difficulty", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf("easy" to "Easy", "medium" to "Medium", "hard" to "Hard").forEach { (key, label) ->
                FilterChip(selected = difficulty == key, onClick = { difficulty = key }, label = { Text(label) })
            }
        }
        (localError ?: vm.generateError)?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = when {
                reading -> "Reading PDF"
                vm.generating -> "Generating, up to a minute"
                else -> "Generate $count questions"
            },
            loading = busy,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                localError = null; vm.generateError = null
                if (!fromPdf) {
                    vm.generateQuiz(topic, null, count, difficulty, onDraft)
                } else {
                    val uri = Uri.parse(pdfUri ?: return@PrimaryButton)
                    scope.launch {
                        reading = true
                        val text = try {
                            extractPdfText(context, uri).text
                        } catch (e: PdfTextException) {
                            localError = e.message; null
                        } catch (e: Exception) {
                            localError = "Couldn't read that PDF."; null
                        } finally {
                            reading = false
                        }
                        if (text != null) vm.generateQuiz(topic.ifBlank { null }, text, count, difficulty, onDraft)
                    }
                }
            },
        )
    }
}
