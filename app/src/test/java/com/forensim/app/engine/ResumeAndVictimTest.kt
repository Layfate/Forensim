package com.forensim.app.engine

import com.forensim.app.core.model.ManifestItem
import com.forensim.app.engine.interaction.Tool
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.scene.CaseFactory
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.CaseType
import com.forensim.app.engine.scene.ObjectKind
import com.forensim.app.engine.scene.SceneGenerator
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex
import com.forensim.app.engine.math.distanceXZ
import com.forensim.app.engine.scene.PlayerStart
import com.forensim.app.engine.scene.ScenarioTemplate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/** Victims in the scene, and resuming an unfinished case from its saved event log. */
class ResumeAndVictimTest {
    private val victims = VictimSex.entries.flatMap { s -> VictimPose.entries.map { p -> Victim(s, p) } }

    @Test
    fun everyLocationAndCaseTypeFitsAVictimAtEverySize() {
        for (location in CaseLocations.all) for (type in CaseType.entries) for (count in listOf(CaseFactory.MIN_ITEMS, CaseFactory.MAX_ITEMS)) {
            val victim = victims[(type.ordinal + count) % victims.size]
            val draft = CaseFactory.draft(type, location, count, Random(type.ordinal * 31 + count), victim)
            val layout = SceneGenerator.generate(location.template, draft.manifest, draft.seed, victim)
            val body = layout.victim
            assertNotNull("${location.id}/$type", body)
            body!!
            // Never on top of evidence or furniture, never at the door, and solid.
            assertTrue(layout.evidence.none { it.bounds.overlapsXZ(body.bounds) })
            assertTrue(layout.objects.filter { it.kind == ObjectKind.FURNITURE }.none { it.bounds.overlapsXZ(body.bounds) })
            assertTrue(layout.objects.any { it.kind == ObjectKind.VICTIM && it.solid })
            val room = location.template.room
            assertTrue(body.bounds.min.x > 0f && body.bounds.min.z > 0f && body.bounds.max.x < room.width && body.bounds.max.z < room.depth)
        }
    }

    @Test
    fun aVictimNeverMovesTheEvidenceAndAlwaysLandsInTheSamePlace() {
        val location = CaseLocations.APARTMENT
        val victim = Victim(VictimSex.FEMALE, VictimPose.FACE_UP)
        val draft = CaseFactory.draft(CaseType.HOMICIDE, location, 5, Random(5), victim)
        val without = SceneGenerator.generate(location.template, draft.manifest, draft.seed)
        val with1 = SceneGenerator.generate(location.template, draft.manifest, draft.seed, victim)
        val with2 = SceneGenerator.generate(location.template, draft.manifest, draft.seed, victim)
        assertNull(without.victim)
        assertEquals(without.evidence.map { it.bounds }, with1.evidence.map { it.bounds })
        assertEquals(with1.victim, with2.victim)
    }

    @Test
    fun victimFromStoredValues() {
        assertEquals(Victim(VictimSex.MALE, VictimPose.FACE_UP), Victim.from("male", "face_up"))
        assertNull(Victim.from(null, null))
        assertNull(Victim.from("other", "face_up"))
        assertEquals("Female, face down", Victim.from("female", "face_down")!!.label)
    }

    // ------------------------------------------------------------------ resume

    // Same open room and seed as GameplayTest, where walking straight to each item works.
    private val template = Json { ignoreUnknownKeys = true }
        .decodeFromString(ScenarioTemplate.serializer(), javaClass.getResource("/templates/tutorial_residence.json")!!.readText())
    private val manifest = listOf(ManifestItem("ev_1", "latent_print"), ManifestItem("ev_2", "cartridge_case"))
    private val clock = { Instant.parse("2026-11-26T09:00:00Z") }

    private fun newGame() = SimulationGame(SceneGenerator.generate(template, manifest, 1001), SimMode.PRACTICE, "Test Student", clock = clock)

    private fun aimAt(game: SimulationGame, target: Vec3) {
        val d = target - game.player.eye
        game.input.addLook(atan2(d.x, -d.z) - game.player.yaw, atan2(d.y, sqrt(d.x * d.x + d.z * d.z)) - game.player.pitch)
        game.step(0.016f)
    }

    private fun walkNear(game: SimulationGame, target: Vec3) {
        repeat(400) {
            val dx = target.x - game.player.x
            val dz = target.z - game.player.z
            if (sqrt(dx * dx + dz * dz) < 1.1f) return
            aimAt(game, Vec3(target.x, game.player.eyeHeight, target.z))
            game.input.moveY = 1f
            game.step(0.05f)
            game.input.moveY = 0f
        }
    }

    /** Signs the log, gloves up, then fully processes the first [items] items. */
    private fun work(game: SimulationGame, items: Int) {
        walkNear(game, game.layout.sceneLog.center)
        aimAt(game, game.layout.sceneLog.center)
        game.post(GameAction.Interact); game.step(0.016f)
        game.post(GameAction.ToggleGloves); game.step(0.016f)
        for (ev in game.layout.evidence.take(items)) {
            walkNear(game, ev.bounds.center)
            aimAt(game, ev.bounds.center)
            for (tool in listOf(Tool.MARKER, Tool.CAMERA, Tool.SCALE, ev.type.recommendedCollect)) {
                game.post(GameAction.SelectTool(tool)); game.post(GameAction.Interact); game.step(0.016f)
            }
            game.post(GameAction.Package(ev.id, ev.type.recommendedPackaging.first())); game.step(0.016f)
        }
    }

    @Test
    fun resumingRestoresTheWorkAndKeepsTheLogInOrder() {
        val first = newGame()
        work(first, 1)
        val saved = first.eventLog()
        val progress = first.progressPercent()
        assertEquals(6 * 100 / 11, progress) // log + 5 steps of 2 x 5 + 1

        val resumed = newGame()
        resumed.restore(saved)
        resumed.step(0.016f)
        val hud = resumed.hud.value
        assertFalse(hud.allPackaged) // one item still to do: no "Submit report" button yet
        assertTrue(hud.logSigned)
        assertTrue(hud.glovesOn)
        assertEquals(1, hud.packaged)
        assertEquals(progress, resumed.progressPercent())
        assertEquals(first.custody().map { it.evidenceId to it.itemNumber }, resumed.custody().map { it.evidenceId to it.itemNumber })

        // Finish the last item after resuming: markers carry on from 3, and the log stays 1..n.
        val last = resumed.layout.evidence.last()
        walkNear(resumed, last.bounds.center)
        aimAt(resumed, last.bounds.center)
        for (tool in listOf(Tool.MARKER, Tool.CAMERA, Tool.SCALE, last.type.recommendedCollect)) {
            resumed.post(GameAction.SelectTool(tool)); resumed.post(GameAction.Interact); resumed.step(0.016f)
        }
        resumed.post(GameAction.Package(last.id, last.type.recommendedPackaging.first())); resumed.step(0.016f)
        assertTrue(resumed.hud.value.allPackaged) // every item packaged: "Submit report" appears beside the bag
        assertEquals(2, resumed.custody().first { it.evidenceId == last.id }.itemNumber)
        assertEquals(99, resumed.progressPercent())
        resumed.post(GameAction.SubmitReport); resumed.step(0.016f)
        assertEquals(100, resumed.progressPercent())
        assertEquals((1..resumed.eventLog().size).toList(), resumed.eventLog().map { it.seq })
        assertEquals(emptyList<String>(), LocalReview.issues(resumed))
    }

    @Test
    fun theReportIsNotRestoredSoItIsWrittenAgain() {
        val first = newGame()
        work(first, 1)
        first.post(GameAction.Conclude("homicide", listOf("ev_1")))
        first.post(GameAction.SubmitReport); first.step(0.016f)
        val resumed = newGame()
        resumed.restore(first.eventLog())
        resumed.step(0.016f)
        assertFalse(resumed.hud.value.reportSubmitted)
        assertTrue(resumed.eventLog().none { it.action == "conclude" || it.action == "submit_report" })
        assertEquals(1, resumed.hud.value.packaged)
    }

    @Test
    fun progressCountsHandlingStepsAndTheSceneLog() {
        val game = newGame()
        assertEquals(0, game.progressPercent())
        work(game, 0)
        // 2 items x 5 steps + the scene log = 11 steps; the log alone is 1 of them.
        assertEquals(100 / 11, game.progressPercent())
    }

    @Test
    fun lookingAtTheBodyShowsItButCannotMoveIt() {
        val location = CaseLocations.OFFICE
        val victim = Victim(VictimSex.MALE, VictimPose.FACE_DOWN)
        val draft = CaseFactory.draft(CaseType.HOMICIDE, location, 3, Random(3), victim)
        val layout = SceneGenerator.generate(location.template, draft.manifest, draft.seed, victim)
        val body = layout.victim!!
        // Stand 1 m from the middle of the body, somewhere clear, looking at it.
        val solids = layout.objects.filter { it.solid }.map { it.bounds }
        val c = body.bounds.center
        val stand = (0 until 16).map { i -> Math.toRadians(i * 22.5) }
            .map { a -> Vec3(c.x + (kotlin.math.cos(a) * 1.2).toFloat(), 0f, c.z + (kotlin.math.sin(a) * 1.2).toFloat()) }
            .first { p -> solids.none { distanceXZ(p.x, p.z, it) < SceneGenerator.PLAYER_RADIUS + 0.05f } }
        val game = SimulationGame(layout.copy(start = PlayerStart(stand.x, stand.z)), SimMode.PRACTICE, "Test Student", clock = clock)
        aimAt(game, Vec3(c.x, 0.15f, c.z))
        assertEquals("Victim", game.hud.value.focus?.label)
        assertEquals("Examine", game.hud.value.interactLabel)
        val before = game.eventLog().size
        game.post(GameAction.Interact); game.step(0.016f)
        assertTrue(game.hud.value.coach!!.text.contains("medical examiner"))
        assertEquals(before, game.eventLog().size) // examining the body is not a scored action
    }
}
