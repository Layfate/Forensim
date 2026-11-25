package com.forensim.app.engine

import com.forensim.app.engine.scene.CaseFactory
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.CaseType
import com.forensim.app.engine.scene.ObjectKind
import com.forensim.app.engine.scene.SceneGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Random evidence placement (layouts with ScenarioTemplate.randomPlacement). */
class RandomPlacementTest {

    @Test
    fun olderCasesKeepFixedSpotsAndNewCasesPlaceAtRandom() {
        for (location in CaseLocations.all) {
            val since = location.randomSince!!
            assertTrue(location.id, location.template.randomPlacement)
            assertTrue(location.id, location.templateFor(location.version).randomPlacement)
            for (v in 1 until since) assertFalse("${location.id} v$v", location.templateFor(v).randomPlacement)
        }
        // Training always uses the original fixed layout.
        assertFalse(com.forensim.app.engine.scene.Training.template(TrainingPart.BASICS).randomPlacement)
    }

    @Test
    fun surfacesLieOnTheirFurniture() {
        for (location in CaseLocations.all) {
            val t = location.template
            assertTrue("${location.id} has surfaces", t.surfaces.isNotEmpty())
            for (s in t.surfaces) {
                assertTrue("${location.id}/${s.id} not empty", s.x1 > s.x0 + 0.1f && s.z1 > s.z0 + 0.1f)
                val f = t.furniture.firstOrNull {
                    s.x0 >= it.x - it.w / 2 && s.x1 <= it.x + it.w / 2 && s.z0 >= it.z - it.d / 2 && s.z1 <= it.z + it.d / 2
                }
                assertTrue("${location.id}/${s.id} is not on one piece of furniture", f != null)
                assertTrue("${location.id}/${s.id} height", s.y <= f!!.h + 1e-4f && s.y > 0f)
            }
        }
    }

    @Test
    fun everyRoomHasItsOwnShell() {
        // The renderer finds a room's model by its room + door, so they must be unique.
        val keys = CaseLocations.all.map { it.template.room to it.template.door }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(10, CaseLocations.all.size)
        assertEquals(CaseLocations.all.size, CaseLocations.all.map { it.id }.toSet().size)
        // Furniture models are looked up by id, so ids may not repeat across rooms.
        val ids = CaseLocations.all.flatMap { l -> l.template.furniture.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun randomScenesAreValidReproducibleAndVaried() {
        for (location in CaseLocations.all) {
            val t = location.template
            val furniture = t.furniture
            val positions = mutableSetOf<List<Pair<Int, Int>>>()
            for (run in 0 until 12) {
                val type = CaseType.entries[run % CaseType.entries.size]
                val draft = CaseFactory.draft(type, location, CaseFactory.MAX_ITEMS, Random(run * 977 + location.id.hashCode()),
                    redHerrings = run % (CaseFactory.MAX_RED_HERRINGS + 1))
                val a = SceneGenerator.generate(t, draft.manifest, draft.seed)
                val b = SceneGenerator.generate(t, draft.manifest, draft.seed)
                assertEquals("${location.id} reproducible", a.evidence, b.evidence)
                positions += a.evidence.map { (it.bounds.center.x * 10).toInt() to (it.bounds.center.z * 10).toInt() }
                val blockers = a.objects.filter { it.kind == ObjectKind.FURNITURE || it.kind == ObjectKind.SCENE_LOG }
                for ((i, e) in a.evidence.withIndex()) {
                    val msg = "${location.id}/${draft.seed}/${e.id} ${e.type.key}"
                    // Floor items keep clear of the walls; items on wall-side furniture may be close to them.
                    val m = if (e.spawnPointId == "floor") 0.15f else 0f
                    assertTrue("$msg inside", e.bounds.min.x >= m && e.bounds.max.x <= t.room.width - m &&
                        e.bounds.min.z >= m && e.bounds.max.z <= t.room.depth - m)
                    for (other in a.evidence.drop(i + 1)) assertFalse("$msg overlaps ${other.id}", e.bounds.overlapsXZ(other.bounds))
                    assertTrue(msg, e.locationLabel.isNotBlank())
                    if (e.spawnPointId == "floor") {
                        assertTrue("$msg allowed on floor", "floor" in e.type.surfaces)
                        assertEquals(msg, 0f, e.bounds.min.y, 0f)
                        assertTrue("$msg under furniture", blockers.none { it.bounds.overlapsXZ(e.bounds) })
                        assertTrue("$msg label", e.locationLabel.startsWith("floor"))
                    } else {
                        assertTrue("$msg allowed on furniture", "furniture" in e.type.surfaces)
                        val s = t.surfaces.single { "top:${it.id}" == e.spawnPointId }
                        assertEquals(msg, s.y, e.bounds.min.y, 1e-4f)
                        assertTrue("$msg off its surface", e.bounds.min.x >= s.x0 && e.bounds.max.x <= s.x1 && e.bounds.min.z >= s.z0 && e.bounds.max.z <= s.z1)
                        assertTrue(msg, furniture.any { f -> abs(f.x - (s.x0 + s.x1) / 2) <= f.w / 2 && abs(f.z - (s.z0 + s.z1) / 2) <= f.d / 2 })
                    }
                }
            }
            // A dozen cases in one room never repeat the same arrangement.
            assertEquals(location.id, 12, positions.size)
        }
    }

    @Test
    fun itemsUseTheWholeRoomNotAHandfulOfSpots() {
        // Over many cases, the same item type lands in many different places and at many angles.
        val location = CaseLocations.RESIDENCE
        val spots = mutableSetOf<Pair<Int, Int>>()
        val angles = mutableSetOf<Int>()
        repeat(40) { run ->
            val draft = CaseFactory.draft(CaseType.HOMICIDE, location, 4, Random(run))
            val layout = SceneGenerator.generate(location.template, draft.manifest, draft.seed)
            layout.evidence.filter { it.type.key == "knife" }.forEach {
                spots += (it.bounds.center.x * 2).toInt() to (it.bounds.center.z * 2).toInt()
                angles += (it.yawDeg / 30).toInt()
            }
        }
        assertTrue("knife only in ${spots.size} half-metre cells", spots.size >= 20)
        assertTrue("knife only at ${angles.size} angles", angles.size >= 8)
    }
}
