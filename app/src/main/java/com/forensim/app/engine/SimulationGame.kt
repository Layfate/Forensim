package com.forensim.app.engine

import com.forensim.app.core.model.SimEventDto
import com.forensim.app.engine.interaction.Tool
import com.forensim.app.engine.interaction.ToolKind
import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.physics.CollisionWorld
import com.forensim.app.engine.physics.PlayerController
import com.forensim.app.engine.physics.applyDeadZone
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.ObjectKind
import com.forensim.app.engine.scene.PlacedEvidence
import com.forensim.app.engine.scene.SceneGenerator
import com.forensim.app.engine.scene.SceneLayout
import com.forensim.app.engine.scene.collectTip
import com.forensim.app.engine.scene.packagingTip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.acos

enum class SimMode(val key: String) {
    TUTORIAL("tutorial"), PRACTICE("practice"), ASSESSMENT("assessment");

    val coaching: Boolean get() = this != ASSESSMENT

    companion object {
        fun from(key: String) = entries.first { it.key == key }
    }
}

/** Biological evidence degrades in plastic: it must be dried and packaged in paper. */
private val BIOLOGICAL = setOf(EvidenceType.BLOODSTAIN, EvidenceType.HAIR_FIBER, EvidenceType.BLUNT_WEAPON)

/**
 * The three required training parts, played in order in the training scene. Each part teaches
 * one stage of scene processing and has its own completion rule (see SimulationGame.trainingDone).
 */
enum class TrainingPart(val number: Int, val title: String, val summary: String) {
    BASICS(1, "Your first scene", "Step by step: sign in, gloves on, document, collect and package two items. Every step explains why."),
    PACKAGING(2, "Collecting and packaging", "Guided: blood, hair and glass. Learn which tool collects each one and whether it goes in a paper bag, plastic bag or evidence box."),
    SOLO(3, "On your own", "Process the whole scene without step-by-step help, then submit your report. Mistakes are still explained.");

    /** Parts 1-2 show every step with arrows and refuse a wrong tool or container; Part 3 does not. */
    val guided: Boolean get() = this != SOLO

    companion object {
        fun of(number: Int?) = entries.firstOrNull { it.number == number }
    }
}

/** Evidence state machine (plan section 5.4). */
enum class EvidenceState { UNSEEN, DISCOVERED, DOCUMENTED, COLLECTED, PACKAGED, SUBMITTED }

class EvidenceRecord(val placed: PlacedEvidence) {
    var state = EvidenceState.UNSEEN
    var markerNumber: Int? = null
    var photographed = false
    var scalePhotographed = false
    var collectTool: Tool? = null
    var packageTool: Tool? = null
    var contaminated = false
    var collectedAt: Instant? = null
    var packagedAt: Instant? = null

    val documented get() = markerNumber != null && photographed && scalePhotographed
    val inScene get() = collectTool == null
}

data class CustodyEntry(
    val evidenceId: String,
    val itemNumber: Int?,
    val description: String,
    val location: String,
    val collectedBy: String,
    val collectedAt: Instant,
    val collectionMethod: String,
    val packaging: String?,
    val sealedAt: Instant?,
    val contaminated: Boolean,
)

private const val HUD_INTERVAL_S = 0.1f

enum class CoachTone { INFO, SUCCESS, WARNING }

data class CoachMessage(val id: Long, val text: String, val tone: CoachTone)

/**
 * Training guide: what the on-screen arrow points at for the current step. Only set in training
 * (and the classic tutorial); null once the last part's goal is met, so the guide disappears.
 */
sealed interface GuideTarget {
    /** A spot in the room to walk to and aim at. */
    data class World(val point: Vec3) : GuideTarget
    data object Interact : GuideTarget
    data object Gloves : GuideTarget
    data class ToolChip(val tool: Tool) : GuideTarget
    /** Open the inventory and seal [evidenceId] in [tool]. */
    data class Package(val evidenceId: String, val tool: Tool) : GuideTarget
    /** Open the menu and finish with the case report. */
    data object Report : GuideTarget
    data object FinishPart : GuideTarget
}

data class FocusInfo(val label: String, val detail: String, val evidenceId: String?, val isSceneLog: Boolean)

data class HudState(
    val focus: FocusInfo? = null,
    val interactLabel: String? = null,
    val selectedTool: Tool = Tool.MARKER,
    val glovesOn: Boolean = false,
    val logSigned: Boolean = false,
    val found: Int = 0,
    val packaged: Int = 0,
    /** Hidden (null) during assessments so the count is not a hint. */
    val total: Int? = null,
    val coach: CoachMessage? = null,
    val tutorialStep: String? = null,
    val guide: GuideTarget? = null,
    /** Training only: true once this part's goal is met (the screen then offers "Finish part"). */
    val trainingComplete: Boolean = false,
    val inventory: List<CustodyEntry> = emptyList(),
    val awaitingPackaging: List<String> = emptyList(),
    val reportSubmitted: Boolean = false,
    /** Every item is collected and packaged: the HUD offers "Submit report". */
    val allPackaged: Boolean = false,
    /** Compass heading (8-wise) plus the wall the exit door is on, for orientation. */
    val heading: String = "N",
    val doorWall: String = "S",
)

data class MarkerPlacement(val number: Int, val bounds: Aabb)

/** Immutable view for the renderer, rebuilt only when the scene changes. */
data class RenderSnapshot(val evidence: List<Pair<PlacedEvidence, Boolean>>, val markers: List<MarkerPlacement>, val focusedId: String?)

sealed interface GameAction {
    data object Interact : GameAction
    data class SelectTool(val tool: Tool) : GameAction
    data object ToggleGloves : GameAction
    data class Package(val evidenceId: String, val tool: Tool) : GameAction
    data object Recenter : GameAction
    data object SubmitReport : GameAction
    /** Case report conclusion: what happened and which collected items support it. */
    data class Conclude(val caseType: String, val evidenceIds: List<String>) : GameAction
}

/** Thread-safe input written by the UI thread, read by the game loop. */
class InputState {
    @Volatile var moveX = 0f
    @Volatile var moveY = 0f
    private val lookLock = Any()
    private var lookX = 0f
    private var lookY = 0f

    fun addLook(dxRadians: Float, dyRadians: Float) = synchronized(lookLock) { lookX += dxRadians; lookY += dyRadians }
    fun drainLook(): Pair<Float, Float> = synchronized(lookLock) { val r = lookX to lookY; lookX = 0f; lookY = 0f; r }
}

/**
 * The playable investigation (plan section 5): player movement with collision, center-reticle
 * targeting, tools, evidence states, chain of custody, practice coaching and an event log for
 * server-side scoring. Not thread-safe except via [input], [post] and the published flows;
 * [step] must be called from a single game-loop thread.
 */
class SimulationGame(
    val layout: SceneLayout,
    val mode: SimMode,
    private val investigatorName: String,
    /** Set when this run is one of the required training parts. */
    val trainingPart: TrainingPart? = null,
    /** Step-by-step hints and guide arrows outside the tutorial (the built-in practice case). */
    val guided: Boolean = false,
    private val clock: () -> Instant = { Instant.now() },
) {
    val input = InputState()
    private val actions = ConcurrentLinkedQueue<GameAction>()

    private val room = layout.template.room
    private val world = CollisionWorld(layout.objects.filter { it.solid }.map { it.bounds }, 0f, 0f, room.width, room.depth)
    val player = PlayerController(layout.start.x, layout.start.z, layout.start.yaw, world)

    private val records = layout.evidence.associate { it.id to EvidenceRecord(it) }
    private val occluders = layout.objects.filter { it.occludes && it.kind != ObjectKind.SCENE_LOG }.map { it.bounds }
    private val events = mutableListOf<SimEventDto>()
    private var seq = 0
    private var nextMarker = 1
    private var tool = Tool.MARKER
    private var gloves = false
    private var logSigned = false
    private var submitted = false
    private var focus: Target? = null
    private var coach: CoachMessage? = null
    private var coachSeq = 0L
    private var sceneVersion = 0
    private var publishedSceneVersion = -1
    private var publishedFocus: String? = "init"
    /** HUD is rebuilt on any game change, otherwise ~10x a second (for the compass heading). */
    private var hudDirty = true
    private var sinceHud = 0f

    private val _hud = MutableStateFlow(HudState(total = if (mode == SimMode.ASSESSMENT) null else records.size))
    val hud: StateFlow<HudState> = _hud.asStateFlow()

    @Volatile var renderSnapshot: RenderSnapshot = buildRenderSnapshot(); private set

    private sealed interface Target {
        data class Evidence(val record: EvidenceRecord) : Target
        data object SceneLog : Target
        data object Victim : Target
    }

    fun post(action: GameAction) {
        actions.add(action)
    }

    /** Advance one frame. */
    fun step(dt: Float) {
        val clampedDt = dt.coerceIn(0f, 0.1f)
        while (true) {
            val a = actions.poll() ?: break
            handle(a)
            hudDirty = true
        }
        sinceHud += clampedDt
        val (mx, my) = applyDeadZone(input.moveX, input.moveY)
        val (lx, ly) = input.drainLook()
        player.update(clampedDt, mx, my, lx, ly)
        updateFocus()
        publish()
    }

    // ------------------------------------------------------------------ targeting

    private fun updateFocus() {
        val eye = player.eye
        val dir = player.forward
        val blockT = occluders.mapNotNull { it.raycast(eye, dir) }.minOrNull() ?: Float.MAX_VALUE

        var best: Target? = null
        var bestT = Float.MAX_VALUE
        fun reachable(t: Float) = t < bestT && t <= SceneGenerator.INTERACT_RANGE && t <= blockT + 0.05f
        // 1) What the reticle is exactly on: the item's real shape, or the scene log.
        for (r in records.values) {
            if (!r.inScene) continue
            val t = r.placed.bounds.raycast(eye, dir) ?: continue
            if (reachable(t)) { best = Target.Evidence(r); bestT = t }
        }
        layout.sceneLog.raycast(eye, dir)?.let { t -> if (reachable(t)) { best = Target.SceneLog; bestT = t } }
        // 2) Only if nothing is exactly under it: the slightly enlarged box that makes tiny items tappable.
        if (best == null) for (r in records.values) {
            if (!r.inScene) continue
            val t = pickBox(r.placed.bounds).raycast(eye, dir) ?: continue
            if (reachable(t)) { best = Target.Evidence(r); bestT = t }
        }
        // The victim's body, when nothing smaller is in the way.
        if (best == null) layout.victim?.let { v ->
            v.bounds.raycast(eye, dir)?.let { t -> if (reachable(t)) { best = Target.Victim; bestT = t } }
        }
        // 3) Aim assist for small items on a phone screen: the item closest to the reticle within
        // ~4 degrees with line of sight (tight, so focus lands on what you are actually pointing at).
        if (best == null) {
            var bestAngle = Math.toRadians(4.0).toFloat()
            for (r in records.values) {
                if (!r.inScene) continue
                val to = r.placed.bounds.center - eye
                val dist = to.length()
                if (dist > SceneGenerator.INTERACT_RANGE) continue
                val angle = acos(to.normalized().dot(dir).coerceIn(-1f, 1f))
                if (angle < bestAngle) {
                    val los = occluders.mapNotNull { it.raycast(eye, to.normalized()) }.minOrNull() ?: Float.MAX_VALUE
                    if (los >= dist - 0.08f) { best = Target.Evidence(r); bestAngle = angle }
                }
            }
        }
        focus = best
        (best as? Target.Evidence)?.record?.let { r ->
            if (r.state == EvidenceState.UNSEEN) {
                r.state = EvidenceState.DISCOVERED
                record("discover", r.placed.id, null)
                sceneVersion++
                if (mode == SimMode.TUTORIAL) say("You found evidence: ${r.placed.type.displayName}. Document it before touching it.", CoachTone.INFO)
            }
        }
    }

    private fun pickBox(b: Aabb): Aabb {
        // Ensure tiny items have a usable pick volume.
        val s = b.size
        val padX = maxOf(0f, (0.16f - s.x) / 2) + 0.03f
        val padY = maxOf(0f, (0.08f - s.y) / 2) + 0.03f
        val padZ = maxOf(0f, (0.16f - s.z) / 2) + 0.03f
        return Aabb(Vec3(b.min.x - padX, b.min.y - padY, b.min.z - padZ), Vec3(b.max.x + padX, b.max.y + padY, b.max.z + padZ))
    }

    // ------------------------------------------------------------------ actions

    private fun handle(a: GameAction) {
        if (submitted && a !is GameAction.SelectTool) return
        when (a) {
            is GameAction.SelectTool -> tool = a.tool
            GameAction.ToggleGloves -> {
                gloves = !gloves
                record(if (gloves) "don_gloves" else "remove_gloves", null, null)
                if (mode.coaching) say(if (gloves) "Gloves on." else "Gloves removed.", CoachTone.INFO)
            }
            GameAction.Recenter -> player.recenterPitch()
            GameAction.Interact -> interact()
            is GameAction.Package -> packageItem(a.evidenceId, a.tool)
            is GameAction.Conclude -> conclude(a.caseType, a.evidenceIds)
            GameAction.SubmitReport -> {
                submitted = true
                record("submit_report", null, null)
                records.values.filter { it.state == EvidenceState.PACKAGED }.forEach { it.state = EvidenceState.SUBMITTED }
                sceneVersion++
            }
        }
    }

    private fun interact() {
        when (val target = focus) {
            null -> if (mode.coaching) say("Aim the centre reticle at an object within reach.", CoachTone.INFO)
            Target.SceneLog -> {
                if (logSigned) {
                    say("You have already signed the scene entry log.", CoachTone.INFO)
                } else {
                    logSigned = true
                    record("sign_log", null, null)
                    if (mode.coaching) say("Entry recorded in the scene log.", CoachTone.SUCCESS)
                }
            }
            is Target.Evidence -> useToolOn(target.record)
            Target.Victim -> say("Leave the body exactly as it lies for the medical examiner. Document and collect the evidence around it.", CoachTone.INFO)
        }
    }

    private fun useToolOn(r: EvidenceRecord) {
        val type = r.placed.type
        if (tool.kind != ToolKind.PACKAGE && mode.coaching && !logSigned) {
            say("Sign the scene entry log at the door before handling evidence.", CoachTone.WARNING)
        }
        when (tool) {
            Tool.MARKER -> {
                if (r.markerNumber != null) return say("This item already has marker ${r.markerNumber}.", CoachTone.INFO)
                r.markerNumber = nextMarker++
                record("marker", r.placed.id, null)
                if (mode.coaching) say("Marker ${r.markerNumber} placed beside the ${type.displayName.lowercase()}.", CoachTone.SUCCESS)
            }
            Tool.CAMERA -> {
                r.photographed = true
                record("photograph", r.placed.id, null)
                if (mode.coaching) {
                    if (r.markerNumber == null) say("Photographed. Tip: place a numbered marker first so the item is identified in every image.", CoachTone.WARNING)
                    else say("Photograph recorded in the photo log.", CoachTone.SUCCESS)
                }
            }
            Tool.SCALE -> {
                if (mode.coaching && !r.photographed) say("Take the photograph without a scale first, so the original condition is shown.", CoachTone.WARNING)
                r.scalePhotographed = true
                record("photograph_scale", r.placed.id, null)
                if (mode.coaching && r.photographed) say("Scaled close-up recorded.", CoachTone.SUCCESS)
            }
            Tool.HAND, Tool.PRINT_KIT, Tool.SWAB_KIT, Tool.FORCEPS, Tool.CASTING_KIT -> collect(r)
            Tool.PAPER_BAG, Tool.PLASTIC_BAG, Tool.EVIDENCE_BOX -> say("Collect the item first, then package it from your inventory.", CoachTone.INFO)
        }
        if (r.documented && r.state == EvidenceState.DISCOVERED) r.state = EvidenceState.DOCUMENTED
        sceneVersion++
    }

    private fun collect(r: EvidenceRecord) {
        val type = r.placed.type
        // Guided training parts don't let a mistake through: they explain it and wait for the fix.
        if (trainingPart?.guided == true) {
            val problem = when {
                r.state != EvidenceState.DOCUMENTED && r.state != EvidenceState.DISCOVERED -> return
                !gloves -> "Put on gloves before you touch any evidence."
                !r.documented -> "Not yet: document it first (marker, photograph, then photograph with scale). Once moved, its original position is lost."
                tool != type.recommendedCollect -> "Wrong tool for a ${type.displayName.lowercase()}. Use the ${type.recommendedCollect.label}: ${type.collectTip}"
                else -> null
            }
            if (problem != null) return say(problem, CoachTone.WARNING)
        }
        if (mode.coaching && !r.documented) {
            say("Document before collecting: marker, photograph, then photograph with scale.", CoachTone.WARNING)
        }
        r.collectTool = tool
        r.collectedAt = clock()
        r.contaminated = !gloves
        r.state = EvidenceState.COLLECTED
        record("collect", r.placed.id, tool.key)
        if (mode.coaching) {
            when {
                !gloves -> say("Collected without gloves: the ${type.displayName.lowercase()} may now be contaminated.", CoachTone.WARNING)
                tool != type.recommendedCollect -> say("That technique isn't suited to a ${type.displayName.lowercase()}. Recommended: ${type.recommendedCollect.actionLabel.lowercase()}.", CoachTone.WARNING)
                r.documented -> say("Collected. Now open your inventory and package it.", CoachTone.SUCCESS)
            }
        }
    }

    private fun packageItem(id: String, packaging: Tool) {
        val r = records[id] ?: return
        if (r.state != EvidenceState.COLLECTED) return
        val wrong = packaging !in r.placed.type.recommendedPackaging
        if (wrong && trainingPart?.guided == true) return say(wrongPackaging(r.placed.type, packaging) + " " + r.placed.type.packagingTip, CoachTone.WARNING)
        r.packageTool = packaging
        r.packagedAt = clock()
        r.state = EvidenceState.PACKAGED
        record("package", id, packaging.key)
        if (mode.coaching) {
            if (wrong) {
                say(wrongPackaging(r.placed.type, packaging), CoachTone.WARNING)
            } else if (trainingPart != null || mode == SimMode.TUTORIAL) {
                say("Packaged in a ${packaging.label.lowercase()}, sealed with evidence tape and signed across the seal. " +
                    "Label: case number, item ${r.markerNumber ?: "?"}, date, time and your name. Entered in the chain of custody.", CoachTone.SUCCESS)
            } else {
                say("Sealed, labelled and entered in the chain of custody.", CoachTone.SUCCESS)
            }
        }
        sceneVersion++
    }

    private fun wrongPackaging(type: EvidenceType, packaging: Tool): String {
        val use = type.recommendedPackaging.joinToString(" or ") { it.label.lowercase() }
        return "${type.displayName} should not go in a ${packaging.label.lowercase()}. " + when {
            packaging == Tool.PLASTIC_BAG && type in BIOLOGICAL -> "Plastic traps moisture and degrades biological material. Use $use."
            Tool.EVIDENCE_BOX in type.recommendedPackaging -> "Sharp or fragile items need a rigid container: $use."
            else -> "Use $use."
        }
    }

    private fun conclude(caseType: String, evidenceIds: List<String>) {
        val known = evidenceIds.filter { it in records }.distinct()
        record("conclude", null, null, mapOf(
            "case_type" to JsonPrimitive(caseType.take(40)),
            "evidence" to kotlinx.serialization.json.JsonArray(known.map { JsonPrimitive(it) }),
        ))
        hudDirty = true
    }

    // ------------------------------------------------------------------ recording

    private fun record(action: String, evidenceId: String?, toolKey: String?, extra: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()) {
        seq += 1
        val px = JsonPrimitive((player.x * 100).toInt() / 100f)
        val pz = JsonPrimitive((player.z * 100).toInt() / 100f)
        val event = SimEventDto(seq, action, evidenceId, toolKey, JsonObject(mapOf("x" to px, "z" to pz) + extra), clock().toString())
        synchronized(events) { events += event }
    }

    /**
     * Resume: rebuilds an unfinished run from its saved event log. Call once, before the game loop
     * starts. Only scene work is restored; the conclusion and report are done again at the end.
     * Restored events are renumbered 1..n so the log stays in order for the server.
     */
    fun restore(saved: List<SimEventDto>) {
        if (seq != 0 || saved.isEmpty()) return
        for (e in saved.sortedBy { it.seq }) {
            val r = e.evidenceId?.let { records[it] }
            val at = e.clientTs?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: clock()
            val tool = Tool.entries.firstOrNull { it.key == e.tool }
            val kept = when (e.action) {
                "don_gloves" -> { gloves = true; true }
                "remove_gloves" -> { gloves = false; true }
                "sign_log" -> { logSigned = true; true }
                "discover" -> r != null
                "marker" -> r != null && r.markerNumber == null && run { r.markerNumber = nextMarker++; true }
                "photograph" -> r != null && run { r.photographed = true; true }
                "photograph_scale" -> r != null && run { r.scalePhotographed = true; true }
                "collect" -> r != null && tool != null && r.collectTool == null && run {
                    r.collectTool = tool; r.collectedAt = at; r.contaminated = !gloves; r.state = EvidenceState.COLLECTED; true
                }
                "package" -> r != null && tool != null && r.state == EvidenceState.COLLECTED && run {
                    r.packageTool = tool; r.packagedAt = at; r.state = EvidenceState.PACKAGED; true
                }
                else -> false // conclude, submit_report, anything unknown
            }
            if (!kept) continue
            if (r != null) {
                if (r.state == EvidenceState.UNSEEN) r.state = EvidenceState.DISCOVERED
                if (r.documented && r.state == EvidenceState.DISCOVERED) r.state = EvidenceState.DOCUMENTED
            }
            seq += 1
            synchronized(events) { events += e.copy(seq = seq) }
        }
        if (seq > 0) say("Welcome back. Your earlier work on this scene has been restored.", CoachTone.INFO)
        sceneVersion++
        hudDirty = true
    }

    /** How far through the case this run is (0-99; 100 once the report is submitted). */
    fun progressPercent(): Int = CaseProgress.percent(eventLog(), records.keys)

    /** Event log for submit-simulation. Safe to call after the loop is paused. */
    fun eventLog(): List<SimEventDto> = synchronized(events) { events.toList() }

    fun custody(): List<CustodyEntry> = records.values.filter { it.collectTool != null }
        .sortedBy { it.markerNumber ?: 999 }
        .map { r ->
            CustodyEntry(
                evidenceId = r.placed.id,
                itemNumber = r.markerNumber,
                description = r.placed.type.displayName,
                location = r.placed.locationLabel,
                collectedBy = investigatorName,
                collectedAt = r.collectedAt!!,
                collectionMethod = r.collectTool!!.actionLabel,
                packaging = r.packageTool?.label,
                sealedAt = r.packagedAt,
                contaminated = r.contaminated,
            )
        }

    fun recordsSnapshot(): List<EvidenceRecord> = records.values.toList()

    private fun say(text: String, tone: CoachTone) {
        coach = CoachMessage(++coachSeq, text, tone)
    }

    /** Training goal for the current part; null outside training. */
    private fun trainingDone(): Boolean = when (trainingPart) {
        null -> false
        TrainingPart.BASICS, TrainingPart.PACKAGING -> logSigned && gloves && records.values.all { it.state == EvidenceState.PACKAGED }
        TrainingPart.SOLO -> submitted && records.values.all { it.state == EvidenceState.PACKAGED || it.state == EvidenceState.SUBMITTED }
    }

    private fun tutorialStep(): String? {
        when (val part = trainingPart) {
            // Part 3 is the test of Parts 1-2: the goal only, no step-by-step hints or arrows.
            TrainingPart.SOLO -> return if (trainingDone()) "Part 3 complete! Training is finished."
                else "On your own now: process every item the way you learned, package it, then submit your report from the menu."
            null -> if (mode != SimMode.TUTORIAL && !guided) return null
            else -> if (trainingDone()) return "Part ${part.number} complete! Every item is collected, sealed and labelled. Tap Finish part to continue."
        }
        // Training and the tutorial explain why; the guided practice case only says what.
        val teach = trainingPart != null || mode == SimMode.TUTORIAL
        if (!logSigned) return "Walk to the clipboard stand by the door and sign the scene entry log." +
            if (teach) " Everyone who enters a scene is logged, so it is known who could have touched what." else ""
        if (!gloves) return "Put on gloves using the glove button." +
            if (teach) " Gloves stop your own DNA and fingerprints getting onto the evidence." else ""
        val next = records.values.sortedBy { it.placed.id }.firstOrNull { it.state != EvidenceState.PACKAGED && it.state != EvidenceState.SUBMITTED }
            ?: return if (submitted) null else "All items packaged. Open the menu and submit your case report."
        val type = next.placed.type
        val name = type.displayName.lowercase()
        val collect = type.recommendedCollect
        val bag = type.recommendedPackaging.first()
        return when {
            next.state == EvidenceState.UNSEEN -> "Search the room. Look for a $name on the ${next.placed.locationLabel}."
            next.markerNumber == null -> "Select the Marker tool and place a numbered marker beside the $name." +
                if (teach) " Never move evidence before it is documented." else ""
            !next.photographed -> "Select the Camera and photograph the $name exactly as it lies."
            !next.scalePhotographed -> "Select the Scale tool and take a close-up with a scale" +
                if (teach) ", so its size can be measured later." else "."
            next.collectTool == null -> "Collect it: select the ${collect.label} tool and ${collect.actionLabel.lowercase()}." +
                if (teach) " ${type.collectTip}" else ""
            else -> "Open the inventory and package the $name in a${if (bag.label.first().lowercase() in "aeiou") "n" else ""} ${bag.label.lowercase()}." +
                if (teach) " ${type.packagingTip} Then it is sealed with evidence tape, signed across the seal and labelled." else ""
        }
    }

    /** Mirrors [tutorialStep]: walk to the item, pick the right tool, then press the action button. */
    private fun guide(): GuideTarget? {
        if (trainingPart == null && mode != SimMode.TUTORIAL && !guided) return null
        if (mode == SimMode.ASSESSMENT) return null // graded: never any help
        if (trainingPart == TrainingPart.SOLO) return null // Part 3: students work it out themselves
        if (submitted) return null
        if (trainingDone()) return if (trainingPart != null) GuideTarget.FinishPart else null
        if (!logSigned) return if (focus == Target.SceneLog) GuideTarget.Interact else GuideTarget.World(layout.sceneLog.center)
        if (!gloves) return GuideTarget.Gloves
        val next = records.values.sortedBy { it.placed.id }
            .firstOrNull { it.state != EvidenceState.PACKAGED && it.state != EvidenceState.SUBMITTED }
            ?: return if (trainingPart == null) GuideTarget.Report else null
        if (next.state == EvidenceState.UNSEEN) return GuideTarget.World(next.placed.bounds.center)
        if (next.state == EvidenceState.COLLECTED) return GuideTarget.Package(next.placed.id, next.placed.type.recommendedPackaging.first())
        val aimed = (focus as? Target.Evidence)?.record === next
        if (!aimed) return GuideTarget.World(next.placed.bounds.center)
        val needed = when {
            next.markerNumber == null -> Tool.MARKER
            !next.photographed -> Tool.CAMERA
            !next.scalePhotographed -> Tool.SCALE
            else -> next.placed.type.recommendedCollect
        }
        return if (tool != needed) GuideTarget.ToolChip(needed) else GuideTarget.Interact
    }

    private fun buildRenderSnapshot(): RenderSnapshot = RenderSnapshot(
        evidence = records.values.map { it.placed to it.inScene },
        markers = records.values.mapNotNull { r ->
            r.markerNumber?.let { n ->
                val b = r.placed.bounds
                MarkerPlacement(n, Aabb.onSurface(b.max.x + 0.09f, b.min.y, b.center.z, 0.07f, 0.1f, 0.05f))
            }
        },
        focusedId = when (val f = focus) {
            is Target.Evidence -> f.record.placed.id
            Target.SceneLog -> "scene_log"
            Target.Victim -> "victim"
            null -> null
        },
    )

    private fun publish() {
        val focusKey = when (val f = focus) {
            is Target.Evidence -> f.record.placed.id
            Target.SceneLog -> "scene_log"
            Target.Victim -> "victim"
            null -> null
        }
        if (sceneVersion != publishedSceneVersion || focusKey != publishedFocus) {
            renderSnapshot = buildRenderSnapshot()
            publishedSceneVersion = sceneVersion
            publishedFocus = focusKey
            hudDirty = true
        }
        // Building the HUD allocates lists; doing it every frame at 90-120 fps churns the GC on
        // the render thread and causes hitches. Rebuild on change, or at 10 Hz for the heading.
        if (!hudDirty && sinceHud < HUD_INTERVAL_S) return
        hudDirty = false
        sinceHud = 0f
        val f = focus
        val focusInfo = when (f) {
            is Target.Evidence -> FocusInfo(
                label = f.record.markerNumber?.let { "Item $it · ${f.record.placed.type.displayName}" } ?: f.record.placed.type.displayName,
                detail = stateLabel(f.record),
                evidenceId = f.record.placed.id,
                isSceneLog = false,
            )
            Target.SceneLog -> FocusInfo("Scene entry log", if (logSigned) "Signed" else "Not signed", null, true)
            Target.Victim -> FocusInfo("Victim", (layout.victim?.victim?.label ?: "") + ". Do not move the body.", null, false)
            null -> null
        }
        val interactLabel = when (f) {
            Target.SceneLog -> if (logSigned) null else "Sign scene log"
            Target.Victim -> "Examine"
            is Target.Evidence -> if (tool.kind == ToolKind.PACKAGE) null else tool.actionLabel
            null -> null
        }
        val next = HudState(
            focus = focusInfo,
            interactLabel = interactLabel,
            selectedTool = tool,
            glovesOn = gloves,
            logSigned = logSigned,
            found = records.values.count { it.state != EvidenceState.UNSEEN },
            packaged = records.values.count { it.state == EvidenceState.PACKAGED || it.state == EvidenceState.SUBMITTED },
            total = if (mode == SimMode.ASSESSMENT) null else records.size,
            coach = coach,
            tutorialStep = tutorialStep(),
            guide = guide(),
            trainingComplete = trainingDone(),
            inventory = custody(),
            awaitingPackaging = records.values.filter { it.state == EvidenceState.COLLECTED }.map { it.placed.id },
            reportSubmitted = submitted,
            allPackaged = records.isNotEmpty() && records.values.all { it.state == EvidenceState.PACKAGED || it.state == EvidenceState.SUBMITTED },
            heading = compass(player.yaw),
            doorWall = layout.template.door.wall.take(1).uppercase(),
        )
        if (next != _hud.value) _hud.value = next
    }

    private fun compass(yaw: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        var deg = Math.toDegrees(yaw.toDouble())
        if (deg < 0) deg += 360.0
        return names[((deg + 22.5) / 45.0).toInt() % 8]
    }

    private fun stateLabel(r: EvidenceRecord): String {
        val parts = mutableListOf<String>()
        if (r.markerNumber != null) parts += "marked"
        if (r.photographed) parts += "photographed"
        if (r.scalePhotographed) parts += "scaled"
        return if (parts.isEmpty()) "Not documented" else parts.joinToString(", ").replaceFirstChar { it.uppercase() }
    }
}

/**
 * Progress through a case, from its event log: for every item the five handling steps (marker,
 * photograph, scale photograph, collect, package), plus signing the scene log. Capped at 99 until
 * the report is submitted, which is 100.
 */
object CaseProgress {
    private val ITEM_STEPS = setOf("marker", "photograph", "photograph_scale", "collect", "package")

    fun percent(events: List<SimEventDto>, itemIds: Collection<String>): Int {
        if (events.any { it.action == "submit_report" }) return 100
        if (itemIds.isEmpty()) return 0
        val total = itemIds.size * ITEM_STEPS.size + 1
        val steps = events.filter { it.action in ITEM_STEPS && it.evidenceId in itemIds }.map { it.action to it.evidenceId }.toSet().size
        val done = steps + if (events.any { it.action == "sign_log" }) 1 else 0
        return (done * 100 / total).coerceIn(0, 99)
    }
}

/**
 * Qualitative local review for practice/offline runs. Deliberately has no point values: the
 * authoritative score comes from the server rubric.
 */
object LocalReview {
    fun issues(game: SimulationGame): List<String> {
        val out = mutableListOf<String>()
        val log = game.eventLog()
        val firstLog = log.firstOrNull { it.action == "sign_log" }?.seq
        val firstEvidence = log.firstOrNull { it.action in setOf("marker", "photograph", "photograph_scale", "collect") }?.seq
        if (firstLog == null) out += "The scene entry log was not signed."
        else if (firstEvidence != null && firstEvidence < firstLog) out += "Evidence was handled before signing the scene log."
        for (r in game.recordsSnapshot()) {
            // Red herrings carry no procedure marks either way (the server ignores them too).
            if (r.placed.type.redHerring) continue
            val name = r.markerNumber?.let { "Item $it (${r.placed.type.displayName})" } ?: r.placed.type.displayName
            when {
                r.state == EvidenceState.UNSEEN -> out += "$name was never found."
                r.collectTool == null -> out += "$name was not collected."
                else -> {
                    if (!r.documented) out += "$name was collected before it was fully documented."
                    if (r.contaminated) out += "$name was collected without gloves."
                    if (r.collectTool != r.placed.type.recommendedCollect) out += "$name was collected with an unsuitable technique."
                    if (r.packageTool == null) out += "$name was not packaged."
                    else if (r.packageTool !in r.placed.type.recommendedPackaging) out += "$name was packaged in an unsuitable container."
                }
            }
        }
        return out
    }
}
