package com.forensim.app.engine

import com.forensim.app.core.model.ManifestItem
import com.forensim.app.engine.interaction.Tool
import com.forensim.app.engine.math.Aabb
import com.forensim.app.engine.math.Vec3
import com.forensim.app.engine.physics.CollisionWorld
import com.forensim.app.engine.physics.PlayerController
import com.forensim.app.engine.physics.applyDeadZone
import com.forensim.app.engine.scene.ScenarioTemplate
import com.forensim.app.engine.scene.SceneGenerator
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

/** Plan section 13 "Gameplay tests": dead zones, camera, collisions, raycasts, evidence states. */
class GameplayTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val template = json.decodeFromString(ScenarioTemplate.serializer(), javaClass.getResource("/templates/tutorial_residence.json")!!.readText())
    private val manifest = listOf(ManifestItem("ev_1", "latent_print"), ManifestItem("ev_2", "cartridge_case"))

    private fun newGame(mode: SimMode = SimMode.PRACTICE) =
        SimulationGame(SceneGenerator.generate(template, manifest, 1001), mode, "Test Student") { Instant.parse("2026-11-22T10:00:00Z") }

    @Test
    fun deadZoneSuppressesDriftAndRescales() {
        assertEquals(0f to 0f, applyDeadZone(0.1f, 0.05f))
        val (x, y) = applyDeadZone(0f, 1f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(1f, y, 1e-4f)
        val (_, half) = applyDeadZone(0f, 0.575f, 0.15f)
        assertEquals(0.5f, half, 1e-3f)
    }

    @Test
    fun wallsBlockMovementAndPlayerSlides() {
        val world = CollisionWorld(listOf(Aabb(Vec3(0f, 0f, 0f), Vec3(10f, 3f, 0.2f))), -100f, -100f, 100f, 100f)
        val p = PlayerController(5f, 2f, 0f, world) // facing -z, toward the wall
        repeat(200) { p.update(0.05f, 0f, 1f, 0f, 0f) }
        assertTrue("player must stop before the wall", p.z >= 0.2f + p.radius - 1e-3f)
        // Diagonal push into the wall still slides along X.
        val x0 = p.x
        repeat(20) { p.update(0.05f, 1f, 1f, 0f, 0f) }
        assertTrue(p.x > x0 + 0.5f)
    }

    @Test
    fun pitchIsClamped() {
        val world = CollisionWorld(emptyList(), -10f, -10f, 10f, 10f)
        val p = PlayerController(0f, 0f, 0f, world)
        p.update(0.016f, 0f, 0f, 0f, 10f)
        assertEquals(PlayerController.MAX_PITCH, p.pitch, 1e-5f)
    }

    @Test
    fun fastFramesCannotTunnelThroughThinWalls() {
        val world = CollisionWorld(listOf(Aabb(Vec3(-5f, 0f, -0.05f), Vec3(5f, 3f, 0.05f))), -100f, -100f, 100f, 100f)
        val p = PlayerController(0f, 1f, 0f, world)
        p.walkSpeed = 50f
        p.update(0.1f, 0f, 1f, 0f, 0f)
        assertTrue(p.z > 0f)
    }

    private fun aimAt(game: SimulationGame, target: Vec3) {
        // Walk next to the target, then rotate the camera toward it.
        val eye = game.player.eye
        val d = target - eye
        val yaw = atan2(d.x, -d.z)
        val pitch = atan2(d.y, sqrt(d.x * d.x + d.z * d.z))
        game.input.addLook(yaw - game.player.yaw, pitch - game.player.pitch)
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

    @Test
    fun fullProcedureReachesPackagedStateAndRecordsEvents() {
        val game = newGame()
        // Sign log
        walkNear(game, game.layout.sceneLog.center)
        aimAt(game, game.layout.sceneLog.center)
        assertTrue(game.hud.value.focus?.isSceneLog == true)
        game.post(GameAction.Interact); game.step(0.016f)
        game.post(GameAction.ToggleGloves); game.step(0.016f)

        for (ev in game.layout.evidence) {
            walkNear(game, ev.bounds.center)
            aimAt(game, ev.bounds.center)
            assertEquals(ev.id, game.hud.value.focus?.evidenceId)
            for (tool in listOf(Tool.MARKER, Tool.CAMERA, Tool.SCALE, ev.type.recommendedCollect)) {
                game.post(GameAction.SelectTool(tool)); game.post(GameAction.Interact); game.step(0.016f)
            }
            game.post(GameAction.Package(ev.id, ev.type.recommendedPackaging.first())); game.step(0.016f)
        }
        game.post(GameAction.SubmitReport); game.step(0.016f)

        val hud = game.hud.value
        assertEquals(2, hud.packaged)
        assertTrue(hud.reportSubmitted)
        assertTrue(game.recordsSnapshot().all { it.state == EvidenceState.SUBMITTED && !it.contaminated })
        assertEquals(listOf<String>(), LocalReview.issues(game))
        val actions = game.eventLog().map { it.action }
        assertEquals("sign_log", actions.first())
        assertTrue(actions.containsAll(listOf("don_gloves", "marker", "photograph", "photograph_scale", "collect", "package", "submit_report")))
        assertEquals((1..actions.size).toList(), game.eventLog().map { it.seq })
        assertEquals(2, game.custody().size)
        assertTrue(game.custody().all { it.sealedAt != null && it.collectedBy == "Test Student" })
    }

    @Test
    fun collectingWithoutGlovesContaminatesAndCoaches() {
        val game = newGame()
        val ev = game.layout.evidence.first { it.type.key == "cartridge_case" }
        walkNear(game, ev.bounds.center)
        aimAt(game, ev.bounds.center)
        game.post(GameAction.SelectTool(Tool.HAND)); game.post(GameAction.Interact); game.step(0.016f)
        val record = game.recordsSnapshot().first { it.placed.id == ev.id }
        assertEquals(EvidenceState.COLLECTED, record.state)
        assertTrue(record.contaminated)
        assertNotNull(game.hud.value.coach)
        assertTrue(LocalReview.issues(game).any { it.contains("without gloves") })
    }

    @Test
    fun assessmentModeGivesNoHintsAndHidesTotal() {
        val game = newGame(SimMode.ASSESSMENT)
        val ev = game.layout.evidence.first()
        walkNear(game, ev.bounds.center)
        aimAt(game, ev.bounds.center)
        game.post(GameAction.SelectTool(Tool.FORCEPS)); game.post(GameAction.Interact); game.step(0.016f)
        assertNull(game.hud.value.coach)
        assertNull(game.hud.value.total)
        assertNull(game.hud.value.tutorialStep)
        assertNull(game.hud.value.guide)
    }

    /** Does exactly what the arrow points at; returns false once there is no arrow. */
    private fun followGuide(game: SimulationGame): Boolean {
        when (val g = game.hud.value.guide ?: return false) {
            is GuideTarget.World -> { walkNear(game, g.point); aimAt(game, g.point) }
            GuideTarget.Interact -> game.post(GameAction.Interact)
            GuideTarget.Gloves -> game.post(GameAction.ToggleGloves)
            is GuideTarget.ToolChip -> game.post(GameAction.SelectTool(g.tool))
            is GuideTarget.Package -> game.post(GameAction.Package(g.evidenceId, g.tool))
            GuideTarget.Report -> game.post(GameAction.SubmitReport)
            GuideTarget.FinishPart -> return false
        }
        game.step(0.016f)
        return true
    }

    private fun trainingGame(part: TrainingPart) =
        SimulationGame(SceneGenerator.generate(template, manifest, 1001), SimMode.TUTORIAL, "Test Student", trainingPart = part) { Instant.parse("2026-11-22T10:00:00Z") }

    @Test
    fun trainingGuideStartsAtTheSceneLogAndEndsAtFinishPart() {
        for (part in listOf(TrainingPart.BASICS, TrainingPart.PACKAGING)) {
            val game = trainingGame(part)
            game.step(0.016f)
            assertEquals(GuideTarget.World(game.layout.sceneLog.center), game.hud.value.guide)
            var steps = 0
            while (followGuide(game) && steps++ < 200) Unit
            assertTrue("$part should be complete", game.hud.value.trainingComplete)
            assertEquals(GuideTarget.FinishPart, game.hud.value.guide)
            // Guided parts end with every item collected and packaged in the right container.
            assertEquals(listOf<String>(), LocalReview.issues(game).filter { "report" !in it.lowercase() })
        }
    }

    @Test
    fun guidedTrainingRefusesTheWrongContainerAndExplainsWhy() {
        val game = trainingGame(TrainingPart.PACKAGING)
        game.step(0.016f)
        var steps = 0
        // Follow the guide until an item is ready to package, then pick a wrong container.
        while (game.hud.value.guide !is GuideTarget.Package && followGuide(game) && steps++ < 200) Unit
        val pkg = game.hud.value.guide as GuideTarget.Package
        val wrong = com.forensim.app.engine.interaction.Tool.packaging.first { it != pkg.tool && it !in game.layout.evidence.first { e -> e.id == pkg.evidenceId }.type.recommendedPackaging }
        game.post(GameAction.Package(pkg.evidenceId, wrong)); game.step(0.016f)
        assertEquals(pkg, game.hud.value.guide) // still waiting for the right container
        assertTrue(game.hud.value.coach!!.text.contains("should not go in"))
    }

    @Test
    fun guidedPracticeArrowsLeadToACleanFinishButAssessmentsNeverGuide() {
        val practice = SimulationGame(SceneGenerator.generate(template, manifest, 1001), SimMode.PRACTICE, "Test Student", guided = true) {
            Instant.parse("2026-11-22T10:00:00Z")
        }
        practice.step(0.016f)
        assertNotNull(practice.hud.value.guide)
        var steps = 0
        while (followGuide(practice) && steps++ < 300) Unit
        assertTrue(practice.hud.value.reportSubmitted)
        assertNull(practice.hud.value.guide)
        assertEquals(listOf<String>(), LocalReview.issues(practice))

        val graded = SimulationGame(SceneGenerator.generate(template, manifest, 1001), SimMode.ASSESSMENT, "Test Student", guided = true)
        graded.step(0.016f)
        assertNull(graded.hud.value.guide)
    }

    @Test
    fun part3HasNoArrowsOrStepByStepHints() {
        val game = trainingGame(TrainingPart.SOLO)
        game.step(0.016f)
        assertNull(game.hud.value.guide)
        assertTrue(game.hud.value.tutorialStep!!.startsWith("On your own"))
    }

    @Test
    fun followingTheGuideThroughTheFullTutorialDoesItCleanlyAndThenTheGuideDisappears() {
        val game = newGame(SimMode.TUTORIAL)
        game.step(0.016f)
        var steps = 0
        while (followGuide(game) && steps++ < 300) Unit
        val hud = game.hud.value
        assertTrue(hud.reportSubmitted)
        assertNull(hud.guide)
        assertEquals(listOf<String>(), LocalReview.issues(game))
    }

    @Test
    fun wallsOccludeTargeting() {
        val game = newGame()
        // Face away from everything at start toward the south wall: nothing should be focused.
        game.input.addLook(Math.PI.toFloat(), 0f)
        game.step(0.016f)
        assertFalse(game.hud.value.focus?.evidenceId != null)
    }
}
