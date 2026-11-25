package com.forensim.app.engine

import com.forensim.app.engine.scene.CaseBriefings
import com.forensim.app.engine.scene.CaseLocations
import com.forensim.app.engine.scene.CaseType
import com.forensim.app.engine.scene.Victim
import com.forensim.app.engine.scene.VictimPose
import com.forensim.app.engine.scene.VictimSex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CaseBriefingsTest {
    private val body = Victim(VictimSex.FEMALE, VictimPose.FACE_UP)
    private val crimeWords = listOf("theft", "burglar", "assault", "homicide", "murder", "shooting", "shot", "gun",
        "drug", "robbery", "robbed", "vandal", "stolen", "break-in", "broke in")

    @Test fun briefingsMatchTheCaseAndNeverNameTheCrime() {
        val random = Random(7)
        for (type in CaseType.entries) for (location in CaseLocations.all) for (victim in listOf(null, body)) repeat(30) {
            val text = CaseBriefings.random(type, location, victim, random)
            val lower = text.lowercase()
            val tag = "$type/${location.id}/${victim != null}: $text"
            assertTrue(tag, text.length in 10..600)
            assertFalse(tag, '{' in text || '}' in text)
            crimeWords.forEach { w -> assertFalse("$tag names '$w'", Regex("\\b$w").containsMatchIn(lower)) }
            if (victim != null) {
                assertFalse(tag, "hospital" in lower)
                assertFalse(tag, "nobody was hurt" in lower || "no one was injured" in lower || "no one was in the room" in lower)
                assertFalse(tag, "removed" in lower || "taken away" in lower || "has taken the body" in lower)
            }
        }
    }

    @Test fun newSummaryDiffersFromTheCurrentOne() {
        val random = Random(3)
        for (type in CaseType.entries) for (location in CaseLocations.all) {
            val current = CaseBriefings.random(type, location, null, random)
            repeat(20) { assertNotEquals(current, CaseBriefings.random(type, location, null, random, avoid = current)) }
        }
    }

    @Test fun manyDifferentBriefingsPerCase() {
        val random = Random(1)
        for (type in CaseType.entries) {
            val seen = (1..200).map { CaseBriefings.random(type, CaseLocations.OFFICE, null, random) }.toSet()
            assertTrue("$type: only ${seen.size} different", seen.size >= 40)
        }
    }
}
