package com.forensim.app.feature.simulation

import androidx.compose.ui.res.stringResource
import com.forensim.app.R
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.foundation.layout.heightIn
import com.forensim.app.core.designsystem.FeaturePanel
import com.forensim.app.core.designsystem.PageIntro
import com.forensim.app.core.designsystem.WorkspaceList
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.forensim.app.core.designsystem.IconButton
import androidx.compose.material3.MaterialTheme
import com.forensim.app.core.designsystem.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.designsystem.Banner
import com.forensim.app.core.designsystem.ErrorState
import com.forensim.app.core.designsystem.ForensimCard
import com.forensim.app.core.designsystem.ForensimTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.forensim.app.core.designsystem.Load
import com.forensim.app.core.designsystem.LoadContent
import com.forensim.app.core.designsystem.LoadingState
import com.forensim.app.core.designsystem.OfflineBanner
import com.forensim.app.core.designsystem.PrimaryButton
import com.forensim.app.core.designsystem.ReadableColumn
import com.forensim.app.core.designsystem.ScreenScaffold
import com.forensim.app.core.designsystem.SectionHeader
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.core.designsystem.StatusChip
import com.forensim.app.core.designsystem.Tone
import com.forensim.app.core.designsystem.pct
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.engine.SimMode
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.ui.appViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the Simulate tab shows. [cases] is only loaded for professors. */
data class SimHome(
    val attempts: List<SimulationAttempt>,
    /** Training parts (1-3) the student has finished. */
    val training: Set<Int>,
    val cases: List<com.forensim.app.core.model.CaseSession>? = null,
    /** Why [cases] could not be loaded (professors only), shown instead of hiding the section. */
    val casesError: String? = null,
    /** Students: every professor case they've opened. Null if it couldn't be loaded (history is shown instead). */
    val myCases: List<com.forensim.app.core.model.MyCase>? = null,
    /** Practice work finished offline on this phone and not sent yet. */
    val pending: com.forensim.app.core.data.PendingSync = com.forensim.app.core.data.PendingSync(),
)

class ScenarioListViewModel(private val c: AppContainer, private val professor: Boolean) : ViewModel() {
    var state by mutableStateOf<Load<SimHome>>(Load.Loading); private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            // Show what we last knew right away, so finished training parts never flash back in.
            if (state !is Load.Ready) {
                val known = if (professor) emptySet() else c.simulation.peekTraining()
                // Professors play cases too: "Played" lists the ones they opened, to resume them.
                val mine = c.simulation.peekMyCases()?.let { withLocalProgress(it) }
                state = known?.let {
                    Load.Ready(SimHome(c.simulation.peekAttempts().orEmpty(), it, myCases = mine, pending = c.practiceSync.pending.value), fromCache = true)
                } ?: Load.Loading
            } else if (!professor) {
                // Coming back from a finished part: hide it now, before the server answers.
                val ready = state as Load.Ready
                c.simulation.peekTraining()?.let { saved -> state = ready.copy(data = ready.data.copy(training = ready.data.training + saved)) }
            }
            // Send anything finished offline first, so the lists below include it.
            if (!professor && !c.practiceSync.pending.value.isEmpty) runCatching { c.practiceSync.flush() }
            state = coroutineScope {
                val attempts = async { runCatching { c.simulation.myAttempts() } }
                val training = async { if (professor) kotlin.Result.success(emptySet<Int>()) else runCatching { c.simulation.trainingParts() } }
                val cases = async { if (professor) runCatching { c.staff.caseSessions() } else null }
                val mine = async { runCatching { withLocalProgress(c.simulation.myCases()) } }
                val tr = training.await()
                val at = attempts.await()
                val ks = cases.await()
                val mc = mine.await()
                val previous = (state as? Load.Ready)?.data
                val trainingKnown = tr.getOrNull() ?: previous?.training ?: c.simulation.peekTraining()
                if (trainingKnown == null) {
                    return@coroutineScope Load.Error((tr.exceptionOrNull() as? ApiException)?.message ?: "Couldn't load your training progress.")
                }
                val offline = listOfNotNull(tr.exceptionOrNull(), at.exceptionOrNull(), ks?.exceptionOrNull())
                    .any { (it as? ApiException)?.isNetwork == true }
                Load.Ready(SimHome(
                    attempts = at.getOrNull() ?: previous?.attempts ?: c.simulation.peekAttempts().orEmpty(),
                    training = trainingKnown,
                    cases = ks?.getOrNull() ?: previous?.cases,
                    casesError = ks?.exceptionOrNull()?.let { e -> (e as? ApiException)?.message ?: "Couldn't load your cases." },
                    myCases = mc.getOrNull() ?: previous?.myCases ?: c.simulation.peekMyCases()?.let { withLocalProgress(it) },
                    pending = c.practiceSync.pending.value,
                ), offline)
            }
        }
    }

    /** Unfinished cases: this phone's saved progress may be ahead of the server's (e.g. saved offline). */
    private suspend fun withLocalProgress(list: List<com.forensim.app.core.model.MyCase>) = list.map { k ->
        if (k.finished) k
        else c.simulation.localCheckpoint(k.attemptId)?.progress?.takeIf { it > k.progress }?.let { k.copy(progress = it) } ?: k
    }
}

/**
 * Simulate tab: required training (Parts 1-3, each disappearing once finished), joining a
 * professor's case by code or QR, and history. Professors also see the cases they generated.
 * There are no built-in cases: students only simulate cases their professor made.
 * [openScenario] takes a SimSource key ("training:N" or "case:CODE").
 */
@Composable
fun ScenarioListScreen(
    openScenario: (String) -> Unit,
    openControls: () -> Unit,
    professor: Boolean = false,
    openCaseBuilder: (() -> Unit)? = null,
    openCaseDetail: ((String) -> Unit)? = null,
) {
    val vm = appViewModel(key = "sim-home-$professor") { ScenarioListViewModel(it, professor) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { vm.load(); onPauseOrDispose { } }
    ScreenScaffold(title = stringResource(R.string.title_simulate), actions = {
        IconButton(onClick = openControls) { Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.control_settings)) }
    }) { padding ->
        LoadContent(vm.state, vm::load, Modifier.padding(padding)) { home, fromCache ->
            val remaining = com.forensim.app.engine.TrainingPart.entries.filter { it.number !in home.training }
            val trainingDone = remaining.isEmpty()
            // The first heading sits right under the top bar (the list already pads it); later
            // headings keep the usual gap above them.
            val showTraining = !professor && !trainingDone
            val showJoin = professor || trainingDone
            val firstSection = when {
                professor -> "join"
                showTraining -> "training"
                else -> "join"
            }
            fun gapAbove(section: String) = if (section == firstSection && !fromCache) 0.dp else Spacing.lg
            WorkspaceList(Modifier.padding(padding)) {
                if (fromCache) item("offline") { OfflineBanner(true) }
                if (!professor && home.pending.visibleCount > 0) item("sync") {
                    val n = home.pending.visibleCount
                    Spacer(Modifier.height(if (fromCache) Spacing.sm else 0.dp))
                    Banner("$n practice result${if (n == 1) "" else "s"} finished offline ${if (n == 1) "is" else "are"} saved on this phone " +
                        "and will be sent automatically when you're back online.", Tone.NEUTRAL)
                }
                // Professors: "Join a case" first, then the cases they generated below it.
                if (professor) item("join") {
                    SectionHeader("Join a case", topPadding = gapAbove("join"))
                    JoinCaseCard(onOpen = { code -> openScenario(SimSource.case(code)) })
                }
                // Professors always get "Your cases"; a failed load shows its reason instead of hiding it.
                if (professor && openCaseBuilder != null) item("my-cases") {
                    ProfessorCasesPager(
                        home = home, topPadding = gapAbove("my-cases"),
                        openCaseBuilder = openCaseBuilder, openCaseDetail = openCaseDetail,
                        openPlayed = { code -> openScenario(SimSource.case(code)) },
                    )
                }

                // Training is for students only. Each part disappears as soon as it is finished,
                // and the whole section goes once Part 3 is done.
                if (showTraining) item("training") {
                    SectionHeader("Training", topPadding = gapAbove("training"), trailing = {
                        Text("${3 - remaining.size} of 3 done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                    Text("Learn to investigate properly. Finish all three parts, in order, to unlock Join a case.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm))
                    remaining.forEach { part ->
                        val unlocked = part.number == 1 || (part.number - 1) in home.training
                        TrainingCard(part, unlocked) { openScenario(SimSource.training(part.number)) }
                    }
                }

                // Students only get "Join a case" once all three training parts are done.
                if (showJoin && !professor) item("join") {
                    SectionHeader("Join a case", topPadding = gapAbove("join"))
                    JoinCaseCard(onOpen = { code -> openScenario(SimSource.case(code)) })
                }

                // Students: every case they've opened stays here. Unfinished ones resume where they
                // left off; finished ones show 100%.
                val mine = home.myCases
                if (!professor && !mine.isNullOrEmpty()) item("my-cases-student") {
                    SectionHeader("My cases", trailing = {
                        Text("${mine.count { it.finished }} of ${mine.size} done", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    })
                    mine.forEach { k ->
                        val waiting = home.pending.submissions.any { it.attemptId == k.attemptId }
                        MyCaseCard(k, waiting, onOpen = { openScenario(SimSource.case(k.code)) })
                    }
                }

                // History only lists professor cases (shown when "My cases" isn't available).
                val scored = home.attempts.filter { it.status == "scored" && it.caseSessionId != null }
                if (!professor && mine == null && scored.isNotEmpty()) item("history") {
                    SectionHeader(stringResource(R.string.history))
                    com.forensim.app.core.designsystem.ListGroup {
                        scored.take(10).forEachIndexed { i, a ->
                            if (i > 0) com.forensim.app.core.designsystem.ListDivider(inset = Spacing.md)
                            com.forensim.app.core.designsystem.ListRow(
                                title = "Professor case",
                                subtitle = if (a.mode == "assessment" && !a.feedbackReleased) stringResource(R.string.feedback_pending) else a.submittedAt?.let { fmt(it) },
                                trailing = { com.forensim.app.core.designsystem.RowValue(a.score?.pct(a.maxScore) ?: "-", MaterialTheme.colorScheme.onSurface) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Professor's cases as two swipeable pages, like the quiz of the day on the student home:
 * page 1 "Your cases" (the ones they generated), page 2 "Cases you played" (opened with a code,
 * so an unfinished one can be resumed). The heading follows the page; the dots switch pages too.
 */
@Composable
private fun ProfessorCasesPager(
    home: SimHome,
    topPadding: androidx.compose.ui.unit.Dp,
    openCaseBuilder: () -> Unit,
    openCaseDetail: ((String) -> Unit)?,
    openPlayed: (String) -> Unit,
) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val played = home.myCases.orEmpty()
    val cs = MaterialTheme.colorScheme
    SectionHeader(
        if (pager.currentPage == 0) "Your cases" else "Cases you played",
        topPadding = topPadding,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(2) { i ->
                    val active = pager.currentPage == i
                    val width by androidx.compose.animation.core.animateDpAsState(if (active) 20.dp else 8.dp, label = "dot")
                    Box(Modifier.height(8.dp).width(width).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (active) cs.primary else cs.outlineVariant)
                        .semantics { contentDescription = if (i == 0) "Your cases" else "Cases you played" }
                        .clickable { scope.launch { pager.animateScrollToPage(i) } })
                }
            }
        },
    )
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager, pageSpacing = Spacing.md, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top,
    ) { page ->
        Column(Modifier.fillMaxWidth()) {
            if (page == 0) {
                PrimaryButton("Generate a case", onClick = openCaseBuilder, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                home.casesError?.let { Banner("Couldn't load your cases: $it", Tone.ERROR); Spacer(Modifier.height(Spacing.sm)) }
                val cases = home.cases.orEmpty()
                if (cases.isEmpty() && home.casesError == null) {
                    Text("Generate a theft, burglary, assault, homicide, shooting or drug case. Students open it with a code or QR.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                cases.forEach { k ->
                    BigCard(
                        icon = Icons.Outlined.QrCode2,
                        eyebrow = "Code ${k.code}" + if (k.graded) " · Graded" else "",
                        title = k.title,
                        subtitle = listOf(com.forensim.app.engine.scene.CaseType.from(k.caseType)?.label ?: k.caseType,
                            com.forensim.app.engine.scene.CaseLocations.byId(k.location)?.label ?: k.location,
                            "${k.manifest.size} items").joinToString(" · "),
                        status = if (k.active) "Open" to Tone.SUCCESS else "Closed" to Tone.NEUTRAL,
                        onClick = openCaseDetail?.let { open -> { open(k.id) } },
                    )
                }
                SwipeHint("Swipe for cases you played" + if (played.isNotEmpty()) " (${played.size})" else "") {
                    scope.launch { pager.animateScrollToPage(1) }
                }
            } else {
                if (played.isEmpty()) {
                    Text("Cases you open with a code appear here, so you can resume one where you left off.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm))
                } else {
                    val unfinished = played.count { !it.finished }
                    if (unfinished > 0) {
                        Text("$unfinished unfinished · tap one to resume where you left off.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Spacing.sm))
                    }
                    played.forEach { k ->
                        val waiting = home.pending.submissions.any { it.attemptId == k.attemptId }
                        MyCaseCard(k, waiting, professor = true, onOpen = { openPlayed(k.code) })
                    }
                }
            }
        }
    }
}

/** Small "swipe" pill under a pager page, like "Swipe for today's quiz" on the student home; tapping it turns the page. */
@Composable
private fun SwipeHint(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = Spacing.xs), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onClick)
                .background(cs.surfaceContainerHigh).padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = cs.primary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Large tappable card used for training parts, cases and professor cases. */
@Composable
private fun BigCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    eyebrow: String? = null,
    iconContainer: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    iconContent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
    status: Pair<String, Tone>? = null,
    badge: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
) {
    ForensimCard(onClick = onClick, modifier = Modifier.padding(bottom = Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            badge?.invoke() ?: com.forensim.app.core.designsystem.IconTile(icon, size = 52.dp, container = iconContainer, content = iconContent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                eyebrow?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                status?.let { (label, tone) -> Spacer(Modifier.height(4.dp)); StatusChip(label, tone) }
            }
            if (onClick != null) com.forensim.app.core.designsystem.Chevron()
        }
    }
}

/**
 * A case the student has opened: its progress bar, and what tapping does. Unfinished and still
 * open: resume. Finished practice case: run it again. Finished graded case or closed case: nothing.
 */
@Composable
private fun MyCaseCard(k: com.forensim.app.core.model.MyCase, waitingToSync: Boolean, professor: Boolean = false, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val location = com.forensim.app.engine.scene.CaseLocations.byId(k.location)?.label ?: k.location
    val canOpen = k.active && !(k.finished && k.graded) && !waitingToSync
    val percent = if (k.finished) 100 else k.progress.coerceIn(0, 99)
    ForensimCard(onClick = if (canOpen) onOpen else null, modifier = Modifier.padding(bottom = Spacing.sm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(if (k.finished) ForensimTheme.colors.success.copy(alpha = 0.16f) else cs.primaryContainer),
                contentAlignment = Alignment.Center) {
                Text("$percent%", style = MaterialTheme.typography.labelLarge,
                    color = if (k.finished) ForensimTheme.colors.success else cs.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Code ${k.code}" + if (k.graded) " · Graded" else "", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(k.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(location, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        .semantics { contentDescription = "$percent percent complete" },
                    color = if (k.finished) ForensimTheme.colors.success else cs.primary,
                    trackColor = cs.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                val (label, tone) = when {
                    waitingToSync -> "Report saved offline · sends when online" to Tone.ATTENTION
                    k.finished && k.graded && !k.feedbackReleased -> "Completed · score pending" to Tone.SUCCESS
                    k.finished -> ("Completed · " + (k.score?.pct(k.maxScore) ?: "scored")) to Tone.SUCCESS
                    !k.active -> (if (professor) "Case closed" else "Closed by your professor") to Tone.NEUTRAL
                    k.progress == 0 -> "Not started · tap to begin" to Tone.PRIMARY
                    else -> "In progress · tap to resume" to Tone.PRIMARY
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatusChip(label, tone)
                    if (k.finished && !k.graded && k.active) {
                        Text("Tap to practise again", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (canOpen) com.forensim.app.core.designsystem.Chevron()
        }
    }
}

/** A training part still to do: numbered when it can be started, locked until the one before is done. */
@Composable
private fun TrainingCard(part: com.forensim.app.engine.TrainingPart, unlocked: Boolean, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BigCard(
        icon = Icons.Outlined.School,
        eyebrow = "Part ${part.number}",
        title = part.title,
        subtitle = if (unlocked) part.summary else "Finish Part ${part.number - 1} to unlock.",
        status = if (unlocked) "Start" to Tone.PRIMARY else "Locked" to Tone.NEUTRAL,
        badge = {
            Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(if (unlocked) cs.primary else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center) {
                if (unlocked) Text("${part.number}", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimary)
                else Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        },
        onClick = if (unlocked) onOpen else null,
    )
}

/** "Join a case": type the 6-character code or scan the professor's QR. */
@Composable
private fun JoinCaseCard(onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var code by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var message by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val parsed = CaseCode.parse(code)
    ForensimCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            com.forensim.app.core.designsystem.IconTile(Icons.Outlined.QrCodeScanner, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text("Open a case from your professor", style = MaterialTheme.typography.titleMedium)
                Text("Enter the code or scan the QR code they gave you.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            com.forensim.app.core.designsystem.OutlinedTextField(
                value = code, onValueChange = { code = CaseCode.clean(it); message = null },
                label = { Text("Case code") }, placeholder = { Text("e.g. K7M2QX") }, singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.18f, androidx.compose.ui.unit.TextUnitType.Em)),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { parsed?.let(onOpen) }),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.FilledTonalIconButton(
                onClick = {
                    scanCaseQr(context, onCode = { scanned ->
                        if (scanned == null) message = "That QR code isn't a Forensim case." else { code = scanned; onOpen(scanned) }
                    }, onError = { message = it })
                },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan QR code") }
        }
        message?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ATTENTION) }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton("Open case", onClick = { parsed?.let(onOpen) }, enabled = parsed != null, modifier = Modifier.fillMaxWidth())
    }
}

private fun fmt(iso: String): String = runCatching {
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
}.getOrDefault(iso)

@Composable
fun SimulationSessionScreen(scenarioId: String, user: UserAccount, onExit: () -> Unit, onOpen: ((String) -> Unit)? = null) {
    val vm = appViewModel(key = "sim_$scenarioId") { SimulationViewModel(it, SimSource.parse(scenarioId), user) }
    // Unfinished professor cases are saved whenever the student leaves, so they can resume later.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.saveProgress() }
    val leave = { vm.saveProgress(); onExit() }
    when (val s = vm.stage) {
        is SimStage.Playing -> GameScreen(s.game, onFinish = vm::endPlaying, onExit = leave,
            onTrainingFinish = if (vm.source is SimSource.Train) vm::finishTraining else null)
        else -> ScreenScaffold(title = vm.title, onBack = leave) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (s) {
                    SimStage.Loading -> LoadingState()
                    is SimStage.Failed -> ErrorState(s.message, vm::load)
                    is SimStage.Briefing -> Briefing(vm, s)
                    is SimStage.Report -> Report(vm, s)
                    is SimStage.Result -> Result(vm, s, onExit)
                    is SimStage.TrainingDone -> TrainingDone(vm, s, onExit, onOpen)
                    is SimStage.Playing -> Unit
                }
            }
        }
    }
}

@Composable
private fun Briefing(vm: SimulationViewModel, s: SimStage.Briefing) {
    val brief = s.brief
    val mode = SimMode.from(brief.kind)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            FeaturePanel(if (vm.source is SimSource.Train) brief.title else "Case briefing", brief.template.briefing,
                Icons.AutoMirrored.Outlined.Assignment, category = brief.category)
            SectionHeader(if (vm.source is SimSource.Train) "In this part" else "Objectives")
            brief.template.objectives.forEachIndexed { i, o -> Text("${i + 1}. $o", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp)) }
            SectionHeader("Controls")
            Text("Left thumb: drag anywhere to walk. Right thumb: drag to look. Aim the reticle at an object, pick a tool from the bar and press the action button. " +
                "Gyroscope look and left-handed layout are in the pause menu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.md))
            when {
                vm.source is SimSource.Case && mode == SimMode.ASSESSMENT -> Banner(
                    "Graded case: no hints. Process the scene properly, then in your report decide what happened and which evidence shows it.",
                    Tone.ATTENTION, Icons.Outlined.Lock)
                vm.source is SimSource.Case -> Banner(
                    "Process the scene properly, then in your report decide what happened and which evidence shows it.", Tone.PRIMARY)
                mode == SimMode.ASSESSMENT -> Banner("Graded: no hints, and the AI tutor is locked until you submit. Your actions are scored on the server against the instructor rubric.",
                    Tone.ATTENTION, Icons.Outlined.Lock)
                mode == SimMode.PRACTICE -> Banner("Practice: mistakes are explained as you make them. Results are not graded.", Tone.PRIMARY)
                else -> Banner("Step-by-step guidance appears at the top left. When this part's goal is met, a Finish button appears.", Tone.SUCCESS)
            }
            brief.resumeProgress?.let { p ->
                Spacer(Modifier.height(Spacing.sm))
                Banner("You've done $p% of this case. Your earlier work is restored when you go back in.", Tone.SUCCESS)
            }
            vm.error?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                when {
                    brief.resumeProgress != null -> "Resume case"
                    mode == SimMode.ASSESSMENT -> "Start graded case"
                    else -> "Enter the scene"
                },
                onClick = vm::start, loading = vm.busy, modifier = Modifier.fillMaxWidth())
            Text("The scene opens in landscape.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun Report(vm: SimulationViewModel, s: SimStage.Report) {
    val game = s.game
    val custody = game.custody()
    val hud by game.hud.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            PageIntro("Case report", description = "Investigator · ${vm.investigatorName}")
            Banner(
                buildString {
                    append(if (hud.logSigned) "Scene log signed. " else "Scene log NOT signed. ")
                    append("${custody.size} item(s) collected, ${custody.count { it.sealedAt != null }} sealed. ")
                    append("${game.eventLog().size} actions recorded.")
                },
                if (hud.logSigned) Tone.NEUTRAL else Tone.ATTENTION,
            )
            // The decision first (what happened, which evidence shows it), the custody record below it.
            if (vm.source is SimSource.Case) Conclusion(vm, custody)
            SectionHeader("Chain of custody")
            if (custody.isEmpty()) Text("No evidence collected.", style = MaterialTheme.typography.bodyMedium)
            custody.forEach { e ->
                ForensimCard(Modifier.padding(bottom = Spacing.sm)) {
                    Text((e.itemNumber?.let { "Item $it · " } ?: "") + e.description, style = MaterialTheme.typography.titleSmall)
                    Text("Recovered from ${e.location}", style = MaterialTheme.typography.bodySmall)
                    Text("${e.collectionMethod} by ${e.collectedBy}, ${e.collectedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
                        style = MaterialTheme.typography.bodySmall)
                    Text(e.packaging?.let { "Sealed in $it" } ?: "Not packaged", style = MaterialTheme.typography.bodySmall,
                        color = if (e.packaging != null) ForensimTheme.colors.success else MaterialTheme.colorScheme.error)
                }
            }
            if (vm.offline) {
                Banner("You're offline. When you submit, your report is saved on this phone and sent to be scored automatically " +
                    "when you're back online.", Tone.ATTENTION)
            }
            vm.error?.let { Spacer(Modifier.height(Spacing.sm)); Banner(it, Tone.ERROR) }
            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = vm::resume, modifier = Modifier.weight(1f).heightIn(min = Spacing.touchTarget)) {
                    Icon(Icons.Outlined.SportsEsports, contentDescription = null); Spacer(Modifier.padding(2.dp)); Text("Back to scene")
                }
                PrimaryButton("Submit report", onClick = vm::submit, loading = vm.busy, enabled = !vm.needsConclusion, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Professor cases: "What happened here?" (a dropdown) and "Which evidence shows it?" */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Conclusion(vm: SimulationViewModel, custody: List<com.forensim.app.engine.CustodyEntry>) {
    SectionHeader("Your conclusion")
    Text("What happened here? Base it on the evidence you recovered.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.sm))
    val types = com.forensim.app.engine.scene.CaseType.entries
    val chosen = types.firstOrNull { it.key == vm.conclusionType }
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        androidx.compose.material3.OutlinedTextField(
            value = chosen?.label ?: "",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("What happened") },
            placeholder = { Text("Choose one") },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            types.forEach { t ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(t.label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { vm.conclusionType = t.key; vm.error = null; open = false },
                    trailingIcon = if (t == chosen) ({ Icon(androidx.compose.material.icons.Icons.Outlined.Check, contentDescription = "Selected") }) else null,
                    contentPadding = androidx.compose.material3.ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
    if (custody.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.md))
        Text("Which evidence shows it? Tick only the items that point to your conclusion.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        ForensimCard {
            custody.forEach { e ->
                val checked = e.evidenceId in vm.conclusionEvidence
                Row(Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)
                    .clickable { vm.conclusionEvidence = if (checked) vm.conclusionEvidence - e.evidenceId else vm.conclusionEvidence + e.evidenceId },
                    verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.size(Spacing.sm))
                    Text((e.itemNumber?.let { "Item $it · " } ?: "") + e.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun TrainingDone(vm: SimulationViewModel, s: SimStage.TrainingDone, onExit: () -> Unit, onOpen: ((String) -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            com.forensim.app.core.designsystem.ResultHero(
                celebrate = s.recorded,
                eyebrow = "Training · Part ${s.part.number} of 3",
                headline = if (s.recorded) "Part ${s.part.number} complete" else "Part ${s.part.number} not saved",
                message = when {
                    !s.recorded -> vm.error ?: "This part couldn't be saved. Try again when you're online."
                    s.next != null -> "Well done. Part ${s.next.number}, ${s.next.title.lowercase()}, is now unlocked."
                    else -> "You've finished all three parts. You can now open cases from your professor."
                } + if (s.queued) " You're offline, so it's saved on this phone and will be sent automatically when you're back online." else "",
                score = null, maxScore = null,
            )
            Spacer(Modifier.height(Spacing.lg))
            if (s.recorded && s.next != null && onOpen != null) {
                PrimaryButton("Start Part ${s.next.number}", onClick = { onOpen(SimSource.training(s.next.number)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touchTarget)) { Text("Back to Simulate") }
            } else {
                PrimaryButton("Back to Simulate", onClick = onExit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Result(vm: SimulationViewModel, s: SimStage.Result, onExit: () -> Unit) {
    val r = s.server
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
        ReadableColumn {
            if (r != null) {
                val fraction = r.score?.let { sc -> r.maxScore?.takeIf { it > 0 }?.let { sc / it } } ?: 0.0
                val celebrate = r.passed ?: (fraction >= 0.75)
                val evidence = r.feedback?.evidence
                com.forensim.app.core.designsystem.ResultHero(
                    celebrate = celebrate,
                    eyebrow = if (celebrate) "Case solved" else "Case reviewed",
                    headline = if (celebrate) "Scene processed by the book" else "Back to the scene",
                    message = when (r.passed) {
                        true -> "Your handling meets the procedure standard. The case report is on file."
                        false -> "Your handling is below the procedure standard. Check the notes below and run the scene again."
                        null -> "Your case report has been scored."
                    },
                    score = r.score,
                    maxScore = r.maxScore,
                    stats = listOfNotNull(
                        evidence?.filter { !it.redHerring && EvidenceType.from(it.type)?.redHerring != true }
                            ?.let { list -> "Evidence handled" to "${list.count { it.issues.isEmpty() }} of ${list.size}" },
                        r.feedback?.conclusion?.let { "Conclusion" to if (it.typeCorrect) "Correct" else "Incorrect" },
                        r.passed?.let { "Standard" to if (it) "Met" else "Not yet met" },
                    ),
                )
                val fb = r.feedback
                if (fb == null) {
                    Spacer(Modifier.height(Spacing.md))
                    Banner("Detailed feedback will appear in your case history when your professor releases it.", Tone.NEUTRAL)
                } else {
                    fb.conclusion?.let { c ->
                        SectionHeader("Your conclusion")
                        ForensimCard {
                            val chosen = com.forensim.app.engine.scene.CaseType.from(c.chosenType)?.label ?: "None"
                            val actual = com.forensim.app.engine.scene.CaseType.from(c.correctType)?.label ?: c.correctType.orEmpty()
                            Row { Text("What happened", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${c.earned.toInt()}/${c.possible.toInt()}") }
                            Text(if (c.typeCorrect) "$chosen — correct." else "You said $chosen. The evidence shows $actual.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (c.typeCorrect) ForensimTheme.colors.success else MaterialTheme.colorScheme.error)
                            Text("Key evidence cited: ${c.supportingCorrect} of ${c.supportingPossible}" +
                                (if (c.supportingWrong > 0) " · ${c.supportingWrong} not relevant" else "") +
                                (if (c.redHerringsCited > 0) " · ${c.redHerringsCited} red herring(s)" else ""), style = MaterialTheme.typography.bodySmall)
                            c.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    if (fb.globalIssues.isNotEmpty()) {
                        SectionHeader("Scene management")
                        fb.globalIssues.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                    SectionHeader("Evidence")
                    fb.evidence.forEach { e ->
                        val name = EvidenceType.from(e.type)?.displayName ?: e.type
                        ForensimCard(Modifier.padding(bottom = Spacing.sm)) {
                            if (e.redHerring || EvidenceType.from(e.type)?.redHerring == true) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text("Red herring: not connected to the case. Processing it earned and cost nothing; citing it as proof would.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                return@ForensimCard
                            }
                            Row { Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${e.earned.toInt()}/${e.possible.toInt()}") }
                            if (e.issues.isEmpty()) Text("Handled correctly.", style = MaterialTheme.typography.bodySmall, color = ForensimTheme.colors.success)
                            e.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            } else {
                val clean = s.localIssues.isEmpty()
                val training = vm.source is SimSource.Train
                com.forensim.app.core.designsystem.ResultHero(
                    celebrate = clean,
                    eyebrow = if (training) "Training" else if (s.queued) "Saved offline" else "Offline run",
                    headline = when {
                        s.queued -> "Report saved on this phone"
                        clean -> "Clean scene work"
                        training -> "Not quite yet"
                        else -> "Practice review"
                    },
                    message = when {
                        training -> "Fix the points below and run this part again to complete it."
                        s.queued -> "You're offline. Your report will be sent and scored automatically when you're back online; " +
                            "the score then appears under My cases."
                        else -> "Run this case online to record a score."
                    },
                    score = null, maxScore = null,
                )
            }
            if (s.localIssues.isNotEmpty() && r?.feedback == null) {
                SectionHeader("What to improve")
                s.localIssues.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            } else if (r == null) {
                Spacer(Modifier.height(Spacing.md)); Banner("No procedural issues found. Well done.", Tone.SUCCESS)
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.lg))
            PrimaryButton("Back to the lab", onClick = onExit, modifier = Modifier.fillMaxWidth())
        }
    }
}
