package com.forensim.app.feature.simulation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forensim.app.AppContainer
import com.forensim.app.core.model.CaseInfo
import com.forensim.app.core.model.SimSubmitResult
import com.forensim.app.core.model.SimulationAttempt
import com.forensim.app.core.model.UserAccount
import com.forensim.app.core.network.ApiException
import com.forensim.app.engine.GameAction
import com.forensim.app.engine.LocalReview
import com.forensim.app.engine.SimMode
import com.forensim.app.engine.SimulationGame
import com.forensim.app.engine.TrainingPart
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.ScenarioTemplate
import com.forensim.app.engine.scene.SceneGenerationException
import com.forensim.app.engine.scene.SceneGenerator
import com.forensim.app.engine.scene.Training
import com.forensim.app.engine.scene.Victim
import com.forensim.app.core.model.CaseCheckpoint
import com.forensim.app.core.model.SimEventDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What a simulation session plays. Encoded in the navigation argument so one route serves all:
 * "training:<1-3>" or "case:<CODE>". There are no built-in cases any more: anything else is
 * [Unavailable] (an old link or a stale back stack entry).
 */
sealed interface SimSource {
    data class Unavailable(val id: String) : SimSource
    data class Train(val part: TrainingPart) : SimSource
    data class Case(val code: String) : SimSource

    val key: String
        get() = when (this) {
            is Unavailable -> id
            is Train -> "training:${part.number}"
            is Case -> "case:$code"
        }

    companion object {
        fun parse(arg: String): SimSource = when {
            arg.startsWith("training:") -> TrainingPart.of(arg.substringAfter(':').toIntOrNull())?.let { Train(it) } ?: Unavailable(arg)
            arg.startsWith("case:") -> Case(arg.substringAfter(':'))
            else -> Unavailable(arg)
        }
        fun training(part: Int) = "training:$part"
        fun case(code: String) = "case:$code"
    }
}

/**
 * What the briefing shows, whatever the source. [kind] is tutorial / practice / assessment.
 * [resumeProgress] is set when an unfinished case is being picked up again.
 */
data class SimBrief(val title: String, val kind: String, val template: ScenarioTemplate, val category: String,
                    val resumeProgress: Int? = null)

sealed interface SimStage {
    data object Loading : SimStage
    data class Failed(val message: String) : SimStage
    data class Briefing(val brief: SimBrief) : SimStage
    data class Playing(val game: SimulationGame) : SimStage
    data class Report(val game: SimulationGame) : SimStage
    /** [queued]: a practice report made offline, saved on this phone and sent when back online. */
    data class Result(val game: SimulationGame, val server: SimSubmitResult?, val localIssues: List<String>, val queued: Boolean = false) : SimStage
    /**
     * A training part was finished and recorded. [next] is the part that is now unlocked, if any.
     * [queued]: finished offline; it counts on this phone now and is sent when back online.
     */
    data class TrainingDone(val part: TrainingPart, val next: TrainingPart?, val recorded: Boolean, val queued: Boolean = false) : SimStage
}

private const val AUTOSAVE_MS = 15_000L

class SimulationViewModel(private val c: AppContainer, val source: SimSource, private val user: UserAccount) : ViewModel() {
    var stage by mutableStateOf<SimStage>(SimStage.Loading); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var title by mutableStateOf("Simulation"); private set
    /** Null when playing training. */
    var attempt: SimulationAttempt? = null; private set
    /** A practice case opened from this phone's copy because there was no connection. */
    var offline by mutableStateOf(false); private set
    /** Set for professor cases opened by code. */
    var case: CaseInfo? = null; private set
    val investigatorName: String get() = user.fullName

    /** Saved work on an unfinished case, replayed into the scene when it starts. */
    private var resumeEvents: List<SimEventDto> = emptyList()
    private var autosave: Job? = null
    private var lastSavedEvents = -1

    // Case report conclusion (professor cases only).
    var conclusionType by mutableStateOf<String?>(null)
    var conclusionEvidence by mutableStateOf<Set<String>>(emptySet())

    init { load() }

    fun load() {
        stage = SimStage.Loading
        viewModelScope.launch {
            stage = try {
                SimStage.Briefing(brief())
            } catch (e: ApiException) {
                SimStage.Failed(e.message)
            }
        }
    }

    private suspend fun brief(): SimBrief = when (val s = source) {
        is SimSource.Unavailable ->
            throw ApiException(404, "This case isn't available. Open a case from your professor with its code or QR.")
        is SimSource.Train -> {
            title = "Training · Part ${s.part.number}"
            SimBrief("Part ${s.part.number}: ${s.part.title}", "tutorial", Training.template(s.part), "Training · Part ${s.part.number} of 3")
        }
        is SimSource.Case -> {
            // Anything finished offline goes first, so the server is up to date before the case opens.
            if (!c.practiceSync.pending.value.isEmpty) runCatching { c.practiceSync.flush() }
            val started = try {
                c.simulation.startCase(s.code).also { c.simulation.cacheCaseStart(s.code, it) }
            } catch (e: ApiException) {
                if (!com.forensim.app.core.data.PracticeSync.retryable(e)) throw e
                // Offline: a practice case opened on this phone before can be played from its saved copy.
                when {
                    c.practiceSync.hasPendingReport(s.code) -> throw ApiException(0,
                        "Your report for this case is saved on this phone. It will be sent automatically when you're back online.")
                    else -> c.simulation.cachedCaseStart(s.code)?.also { offline = true } ?: throw ApiException(0,
                        "You're offline. A practice case can be played offline once it has been opened online on this phone; graded cases always need a connection.")
                }
            }
            val location = CaseLocations.byId(started.case.location)
                ?: throw ApiException(422, "This case uses a location your app doesn't have yet. Update ForenSim and try again.")
            if (location.version < started.case.locationVersion) {
                throw ApiException(422, "This case was made with a newer version of ForenSim. Update the app and try again.")
            }
            attempt = started.attempt
            case = started.case
            title = started.case.title
            val graded = started.attempt.mode == "assessment"
            // Unfinished case: use whichever saved copy got further (this phone's may be newer if it was offline).
            val server = started.attempt.checkpoint.orEmpty()
            val local = c.simulation.localCheckpoint(started.attempt.id)?.events.orEmpty()
            resumeEvents = if (local.size > server.size) local else server
            lastSavedEvents = resumeEvents.size
            val resume = resumeEvents.takeIf { it.isNotEmpty() }?.let {
                maxOf(started.attempt.progress, c.simulation.localCheckpoint(started.attempt.id)?.progress ?: 0)
            }
            SimBrief(started.case.title, started.attempt.mode, location.templateFor(started.case.locationVersion).copy(briefing = started.case.briefing),
                "Case ${started.case.code} · ${location.label}" + if (graded) " · Graded" else "", resume)
        }
    }

    fun start() {
        val b = stage as? SimStage.Briefing ?: return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val game = when (val s = source) {
                    is SimSource.Train -> {
                        val layout = SceneGenerator.generate(b.brief.template, Training.manifest(s.part), Training.seed(s.part))
                        SimulationGame(layout, SimMode.TUTORIAL, user.fullName, trainingPart = s.part)
                    }
                    is SimSource.Case -> {
                        val a = attempt ?: throw ApiException(409, "This case could not be opened. Go back and enter the code again.")
                        val victim = case?.let { Victim.from(it.victimSex, it.victimPose) }
                        val layout = SceneGenerator.generate(b.brief.template, a.manifest, a.seed, victim)
                        SimulationGame(layout, SimMode.from(a.mode), user.fullName).also { it.restore(resumeEvents) }
                    }
                    is SimSource.Unavailable -> return@launch
                }
                stage = SimStage.Playing(game)
                startAutosave()
            } catch (e: ApiException) {
                error = if (e.isNetwork) "Cases need an internet connection." else e.message
            } catch (e: SceneGenerationException) {
                error = "This scene could not be generated (${e.message}). Please report it to your professor."
            } finally {
                busy = false
            }
        }
    }

    fun endPlaying() {
        val p = stage as? SimStage.Playing ?: return
        stage = SimStage.Report(p.game)
        saveProgress()
    }

    /** Professor cases: save the work every few seconds while there is something new. */
    private fun startAutosave() {
        if (source !is SimSource.Case || attempt == null) return
        autosave?.cancel()
        autosave = viewModelScope.launch {
            while (isActive) {
                delay(AUTOSAVE_MS)
                saveProgress()
            }
        }
    }

    /**
     * Saves an unfinished professor case (on this phone at once, then on the server), so the
     * student can leave and resume it later. Runs in the app scope so leaving the screen doesn't
     * cancel it. Does nothing for training, offline runs or a submitted report.
     */
    fun saveProgress() {
        if (source !is SimSource.Case) return
        val a = attempt ?: return
        val game = when (val s = stage) {
            is SimStage.Playing -> s.game
            is SimStage.Report -> s.game
            else -> return
        }
        if (game.hud.value.reportSubmitted) return
        val events = game.eventLog()
        if (events.size == lastSavedEvents) return
        lastSavedEvents = events.size
        val checkpoint = CaseCheckpoint(game.progressPercent(), events)
        c.appScope.launch {
            c.simulation.saveLocalCheckpoint(a.id, checkpoint)
            try {
                c.simulation.uploadCheckpoint(a.id, checkpoint)
            } catch (e: ApiException) {
                // Offline: this phone's copy is newer; send it when the connection is back.
                if (com.forensim.app.core.data.PracticeSync.retryable(e)) c.practiceSync.queueCheckpoint(a.id)
            }
        }
    }

    override fun onCleared() {
        saveProgress()
        super.onCleared()
    }

    fun resume() {
        val r = stage as? SimStage.Report ?: return
        if (!r.game.hud.value.reportSubmitted) stage = SimStage.Playing(r.game)
    }

    /** "Finish part" in a training run: record it on the server and show what unlocks next. */
    fun finishTraining() {
        val part = (source as? SimSource.Train)?.part ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val sync = c.practiceSync
            var queued = false
            val recorded = try {
                if (!sync.pending.value.isEmpty) runCatching { sync.flush() }
                if (sync.pending.value.training.any { it < part.number }) {
                    // An earlier part is still waiting to be sent: this one has to follow it.
                    sync.queueTraining(part.number); queued = true
                } else {
                    c.simulation.recordTraining(part.number)
                }
                true
            } catch (e: ApiException) {
                if (com.forensim.app.core.data.PracticeSync.retryable(e)) {
                    // Offline: count it on this phone now and send it when the connection is back.
                    sync.queueTraining(part.number); queued = true
                    true
                } else {
                    error = e.message
                    false
                }
            } finally {
                busy = false
            }
            stage = SimStage.TrainingDone(part, TrainingPart.of(part.number + 1), recorded, queued)
        }
    }

    /** Professor cases need a conclusion before the report can be submitted. */
    val needsConclusion: Boolean get() = source is SimSource.Case && conclusionType == null

    fun submit() {
        val r = stage as? SimStage.Report ?: return
        val game = r.game
        if (needsConclusion) {
            error = "Choose what you think happened before submitting your report."
            return
        }
        if (!game.hud.value.reportSubmitted) {
            // The GL loop is stopped in this stage, so stepping on the main thread is safe.
            conclusionType?.let { game.post(GameAction.Conclude(it, conclusionEvidence.toList())) }
            game.post(GameAction.SubmitReport)
            game.step(0f)
        }
        if (source is SimSource.Train) {
            if (game.hud.value.trainingComplete) finishTraining()
            else stage = SimStage.Result(game, null, LocalReview.issues(game))
            return
        }
        val local = LocalReview.issues(game)
        val a = attempt
        if (a == null) {
            stage = SimStage.Result(game, null, local)
            return
        }
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val result = c.simulation.submit(a.id, game.eventLog())
                autosave?.cancel()
                c.simulation.clearCheckpoint(a.id)
                stage = SimStage.Result(game, result, if (game.mode == SimMode.ASSESSMENT) emptyList() else local)
            } catch (e: ApiException) {
                if (game.mode != SimMode.ASSESSMENT && com.forensim.app.core.data.PracticeSync.retryable(e)) {
                    // Practice report made offline: keep it on this phone; it is scored when it reaches the server.
                    val code = (source as SimSource.Case).code
                    c.practiceSync.queueSubmission(com.forensim.app.core.data.PendingSubmission(
                        a.id, code, case?.title ?: title, game.eventLog(), System.currentTimeMillis()))
                    autosave?.cancel()
                    stage = SimStage.Result(game, null, local, queued = true)
                } else {
                    error = "Could not submit: ${e.message} Your case is kept on this screen; try again" +
                        if (e.isNetwork) " when you're connected (graded cases need a connection)." else "."
                }
            } finally {
                busy = false
            }
        }
    }
}
