package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import kotlin.test.*

class PracticalExerciseTest {
    private val practicals = QUESTIONS.filter { it.practical != null }

    @Test fun bundledPracticalsAreValidAndRunnableOnesHaveTests() {
        assertTrue(practicals.count { it.area == "Kotlin" } >= 5)
        assertTrue(practicals.count { it.area == "Android" } >= 5)
        practicals.forEach { q ->
            val p = q.practical!!
            assertTrue(validPractical(p), q.id)
            assertEquals("Practical", q.round, q.id)
            if (p.runnable) {
                assertTrue(p.tests.size >= 2, q.id)
                // The harness checks exactly the displayed cases.
                assertEquals(p.tests.size, Regex("practicalCheck\\(\\d+,").findAll(p.harness).count(), q.id)
            }
        }
    }

    @Test fun reportCountsPassAndFailLines() {
        val report = testReport("PASS 1\nnoise\nFAIL 2: expected 3 but was 4\n  PASS 3\n")!!
        assertEquals(2, report.passed)
        assertEquals(listOf("FAIL 2: expected 3 but was 4"), report.failed)
        assertEquals(3, report.total)
        assertNull(testReport("Exception in thread main"))
    }

    @Test fun programAppendsHarnessAfterLearnerCode() {
        val p = Practical("Write code", "Kotlin", "task", solution = "s", tests = listOf(PracticalTest("a", "b"), PracticalTest("c", "d")), harness = "fun main() {}")
        assertEquals("fun f() = 1\n\nfun main() {}", p.program("fun f() = 1"))
        assertFalse(validPractical(p.copy(tests = p.tests.take(1))))
        assertFalse(validPractical(p.copy(kind = "Essay")))
        assertTrue(validPractical(p.copy(harness = "", tests = emptyList())))
    }

    @Test fun mockRoundsExcludeHandsOnExercises() {
        repeat(5) {
            val cards = selectMockCards(QUESTIONS, emptyList(), "Kotlin", 0)
            assertTrue(cards.none { card -> practicals.any { it.id == card.questionId } })
        }
    }

    @Test fun assessingAPracticalSchedulesItInTheDeck() {
        val q = practicals.first()
        val solved = AppState(cards = emptyList()).assessPractical(q, true)
        assertEquals(1, solved.cards.single().box)
        val missed = solved.assessPractical(q, false)
        assertEquals(1, missed.cards.size)
        assertEquals(0, missed.cards.single().box)
        assertTrue(missed.cards.single().due <= nowMs())
    }
}
