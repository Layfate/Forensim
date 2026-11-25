package com.forensim.app.feature.student

import com.forensim.app.core.data.DailyEntry
import com.forensim.app.core.data.DailyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyQuizTest {
    private val terms = (1..9).map { "Term $it" to "Definition $it" }
    private val start = LocalDate.of(2026, 11, 1).toEpochDay()

    @Test fun answerMovesBetweenLettersAndIsBalanced() {
        val days = (0 until 400).map { start + it }
        val slots = days.map { d ->
            val q = DailyQuiz.build(d, terms, emptyList())
            assertEquals(4, q.options.size)
            assertEquals(4, q.options.distinct().size)
            q.options.indexOf(q.answer)
        }
        // Each letter A-D once in every four-day block.
        days.zip(slots).groupBy({ Math.floorDiv(it.first, 4L) }, { it.second }).values.filter { it.size == 4 }
            .forEach { assertEquals(setOf(0, 1, 2, 3), it.toSet()) }
        // About a quarter each (the partial blocks at either end can tip it by a few).
        (0..3).forEach { s -> assertTrue(slots.count { it == s } in 96..104) }
        // Never the same letter three days running.
        slots.windowed(3).forEach { assertTrue("same letter 3 days: $it", it.toSet().size > 1) }
    }

    @Test fun sameDayGivesTheSameQuiz() {
        assertEquals(DailyQuiz.build(start + 5, terms, emptyList()), DailyQuiz.build(start + 5, terms, emptyList()))
    }

    private fun entries(vararg days: Pair<String, Boolean>) =
        days.associate { (d, ok) -> d to DailyEntry(d, quizChoice = "x", quizCorrect = ok) }

    @Test fun streakCarriesOverToTheNextDay() {
        val today = LocalDate.of(2026, 11, 25)
        val e = entries("2026-11-23" to true, "2026-11-24" to true)
        assertEquals(2, DailyRepository.streak(e, today)) // not answered yet today: still 2
        assertEquals(3, DailyRepository.streak(e + entries("2026-11-25" to true), today))
        assertEquals(0, DailyRepository.streak(e + entries("2026-11-25" to false), today))
        assertEquals(0, DailyRepository.streak(entries("2026-11-23" to true), today)) // missed yesterday
    }
}
