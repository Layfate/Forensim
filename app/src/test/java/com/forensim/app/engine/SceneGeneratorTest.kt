package com.forensim.app.engine

import com.forensim.app.core.model.ManifestItem
import com.forensim.app.engine.scene.ObjectKind
import com.forensim.app.engine.scene.ScenarioTemplate
import com.forensim.app.engine.scene.SceneGenerator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan section 13 "Procedural tests": deterministic seeds, reachability, spawn collisions. */
class SceneGeneratorTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun template(id: String): ScenarioTemplate =
        json.decodeFromString(ScenarioTemplate.serializer(), javaClass.getResource("/templates/$id.json")!!.readText())

    private fun manifestFor(t: ScenarioTemplate, seed: Int): List<ManifestItem> {
        // Mirrors the server's ordering rule: required items first, then a seed-dependent order.
        val ordered = t.evidencePool.sortedWith(compareBy({ if (it.required) 0 else 1 }, { (seed.toString() + it.type).hashCode() }))
        return ordered.take(t.evidenceCount).mapIndexed { i, e -> ManifestItem("ev_${i + 1}", e.type) }
    }

    @Test
    fun sameSeedProducesIdenticalPlacement() {
        for (id in listOf("tutorial_residence", "practice_apartment", "assessment_office")) {
            val t = template(id)
            val m = manifestFor(t, 4281)
            val a = SceneGenerator.generate(t, m, 4281).evidence
            val b = SceneGenerator.generate(t, m, 4281).evidence
            assertEquals("$id placement must be reproducible", a, b)
        }
    }

    @Test
    fun differentSeedsCanVaryPlacement() {
        val t = template("practice_apartment")
        val layouts = (1..20).map { seed -> SceneGenerator.generate(t, manifestFor(t, 7), seed).evidence.map { it.spawnPointId } }.toSet()
        assertTrue("expected more than one arrangement across seeds", layouts.size > 1)
    }

    @Test
    fun everyTemplateGeneratesValidScenesAcrossManySeeds() {
        for (id in listOf("tutorial_residence", "practice_apartment", "assessment_office")) {
            val t = template(id)
            for (seed in 1..200) {
                val m = manifestFor(t, seed)
                val layout = SceneGenerator.generate(t, m, seed)
                assertEquals(m.size, layout.evidence.size)
                val furniture = layout.objects.filter { it.kind == ObjectKind.FURNITURE }
                for ((i, e) in layout.evidence.withIndex()) {
                    // inside the room
                    assertTrue(e.bounds.min.x >= 0f && e.bounds.max.x <= t.room.width)
                    assertTrue(e.bounds.min.z >= 0f && e.bounds.max.z <= t.room.depth)
                    // no evidence overlap
                    for (other in layout.evidence.drop(i + 1)) assertFalse("$id/$seed overlap", e.bounds.overlapsXZ(other.bounds))
                    // floor evidence never inside furniture; surface evidence sits on top of furniture
                    if (e.bounds.min.y == 0f) {
                        assertTrue("$id/$seed floor item under furniture", furniture.none { it.bounds.overlapsXZ(e.bounds) })
                    } else {
                        assertTrue("$id/$seed floating item", furniture.any { kotlin.math.abs(it.bounds.max.y - e.bounds.min.y) < 0.02f && it.bounds.overlapsXZ(e.bounds) })
                    }
                    // type matches surface rules
                    val surface = t.spawnPoints.first { it.id == e.spawnPointId }.surface
                    assertTrue(surface in e.type.surfaces)
                }
            }
        }
    }

    @Test
    fun latentPrintPrefersGlass() {
        val t = template("tutorial_residence")
        val layout = SceneGenerator.generate(t, listOf(ManifestItem("ev_1", "latent_print")), 99)
        assertTrue(layout.evidence.single().locationLabel.contains("glass"))
    }
}
