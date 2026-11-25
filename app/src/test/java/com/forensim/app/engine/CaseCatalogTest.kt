package com.forensim.app.engine

import com.forensim.app.engine.scene.CaseFactory
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.CaseType
import com.forensim.app.engine.scene.EvidenceType
import com.forensim.app.engine.scene.SceneGenerator
import com.forensim.app.engine.scene.Training
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CaseCatalogTest {

    @Test
    fun everyCaseTypeBuildsInEveryLocationAtEverySize() {
        for (type in CaseType.entries) for (location in CaseLocations.all) for (count in CaseFactory.MIN_ITEMS..CaseFactory.MAX_ITEMS) {
            repeat(5) { run ->
                val draft = CaseFactory.draft(type, location, count, Random(run * 7919 + count))
                assertEquals(count, draft.manifest.size)
                // Required evidence is always present.
                type.pool.filter { it.required }.forEach { req -> assertTrue("$type missing ${req.type}", draft.manifest.any { it.type == req.type }) }
                // The draft regenerates identically from its seed (what every student will see).
                val a = SceneGenerator.generate(location.template, draft.manifest, draft.seed)
                val b = SceneGenerator.generate(location.template, draft.manifest, draft.seed)
                assertEquals(a.evidence.map { it.bounds }, b.evidence.map { it.bounds })
            }
        }
    }

    @Test
    fun everySpawnPointInEveryLocationCanHoldEvidence() {
        // A spot blocked by furniture, too close to a wall or out of reach would be skipped
        // silently by the generator; make sure every spot is really usable.
        for (location in CaseLocations.all) for (point in location.template.spawnPoints) {
            val only = location.template.copy(spawnPoints = listOf(point), randomPlacement = false)
            val ok = (1..20).any { seed ->
                runCatching { SceneGenerator.generate(only, listOf(com.forensim.app.core.model.ManifestItem("ev_1", "hair_fiber")), seed) }.isSuccess
            }
            assertTrue("${location.id}/${point.id} can't hold evidence", ok)
        }
    }

    @Test
    fun sevenItemCasesRepeatOnlySupportingEvidence() {
        for (type in CaseType.entries) {
            val draft = CaseFactory.draft(type, CaseLocations.WAREHOUSE, CaseFactory.MAX_ITEMS, Random(11))
            type.pool.filter { it.required }.forEach { req -> assertEquals("$type ${req.type}", 1, draft.manifest.count { it.type == req.type }) }
            assertEquals(draft.manifest.size, draft.manifest.map { it.id }.toSet().size)
        }
    }

    @Test
    fun casesMadeBeforeTheNewSpotsKeepTheirOriginalLayout() {
        // A case must never land on a spot that was added after its layout version.
        for (location in CaseLocations.all.filter { it.addedSpots.isNotEmpty() }) {
            // Versions that later gained spots (the random-placement version adds none).
            for (v in 1 until location.addedSpots.keys.max()) {
                val old = location.templateFor(v)
                val later = location.addedSpots.filterKeys { it > v }.values.flatten().toSet()
                assertTrue("${location.id} v$v", later.isNotEmpty() && old.spawnPoints.none { it.id in later })
                assertTrue(old.spawnPoints.size < location.template.spawnPoints.size)
                // Older layouts keep their spots in the same order (same seed -> same scene).
                assertEquals(old.spawnPoints, location.template.spawnPoints.filter { it.id !in later })
            }
        }
        // The rooms that already had version 2 keep exactly their original version-1 spots.
        for (location in listOf(CaseLocations.RESIDENCE, CaseLocations.APARTMENT, CaseLocations.OFFICE)) {
            assertTrue(location.templateFor(1).spawnPoints.none { it.id in CaseLocations.ADDED_IN_V2 })
        }
    }

    @Test
    fun fullCasesWithTenRedHerringsBuildEverywhereEvenWithAVictim() {
        val victims = com.forensim.app.engine.scene.VictimSex.entries.flatMap { s ->
            com.forensim.app.engine.scene.VictimPose.entries.map { p -> com.forensim.app.engine.scene.Victim(s, p) }
        }
        for (type in CaseType.entries) for (location in CaseLocations.all) repeat(4) { run ->
            val victim = if (run % 2 == 0) null else victims[(type.ordinal + run) % victims.size]
            val draft = CaseFactory.draft(type, location, CaseFactory.MAX_ITEMS, Random(run * 101 + type.ordinal * 7 + location.id.length),
                victim = victim, redHerrings = CaseFactory.MAX_RED_HERRINGS)
            assertEquals("$type/${location.id}", CaseFactory.MAX_ITEMS + CaseFactory.MAX_RED_HERRINGS, draft.manifest.size)
            val layout = SceneGenerator.generate(location.template, draft.manifest, draft.seed, victim)
            assertEquals(draft.manifest.size, layout.evidence.size)
            if (victim != null) assertNotNull("$type/${location.id} victim", layout.victim)
        }
    }

    @Test
    fun everyPoolTypeIsAKnownEvidenceType() {
        CaseType.entries.flatMap { it.pool }.forEach {
            assertNotNull(it.type, EvidenceType.from(it.type))
            assertTrue("${it.type} is a red herring, not evidence", EvidenceType.from(it.type)?.redHerring == false)
        }
    }

    @Test
    fun casesWithRedHerringsBuildEverywhere() {
        for (type in CaseType.entries) for (location in CaseLocations.all) for (h in 1..CaseFactory.MAX_RED_HERRINGS) {
            val count = if (h == CaseFactory.MAX_RED_HERRINGS) CaseFactory.MAX_ITEMS else 4
            val draft = CaseFactory.draft(type, location, count, Random(h * 31 + type.ordinal), redHerrings = h)
            val herrings = draft.manifest.mapNotNull { EvidenceType.from(it.type) }.filter { it.redHerring }
            assertEquals("$type/${location.id}", h, herrings.size)
            assertEquals("different red herrings", h, herrings.toSet().size)
            assertEquals(count + h, draft.manifest.size)
            assertEquals(draft.manifest.size, draft.manifest.map { it.id }.toSet().size)
        }
    }

    @Test
    fun poolsAndRedHerringsMatchTheServer() {
        // The server validates manifests against private.case_evidence_types / red_herring_types.
        val sql = listOf(java.io.File("../supabase/migrations"), java.io.File("supabase/migrations")).first { it.isDirectory }
            .resolve("20261130000001_new_rooms_and_evidence.sql").readText()
        fun quoted(block: String) = Regex("'([a-z_]+)'").findAll(block).map { it.groupValues[1] }.toList()
        val herringBlock = sql.substringAfter("red_herring_types()").substringBefore("$$;")
        assertEquals(EvidenceType.redHerrings.map { it.key }.toSet(), quoted(herringBlock).toSet())
        for (t in CaseType.entries) {
            val line = Regex("when '${t.key}'\\s+then array\\[([^\\]]*)\\]").find(sql)?.groupValues?.get(1) ?: error("no pool for ${t.key}")
            assertEquals(t.key, t.pool.map { it.type }.toSet(), quoted(line).toSet())
        }
    }

    @Test
    fun trainingPartsGenerateWithTheirFixedSeeds() {
        for (part in TrainingPart.entries) {
            val layout = SceneGenerator.generate(Training.template(part), Training.manifest(part), Training.seed(part))
            assertEquals(Training.manifest(part).size, layout.evidence.size)
            assertTrue(layout.tapes.isNotEmpty())
        }
    }
}
