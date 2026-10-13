package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.*

class ReadinessTest {
    private val day = LocalDate(2026, 10, 9)
    private val now = 1_800_000_000_000L
    private fun q(id: String, subtopic: String, level: Int = 2, rubric: Int = 4) =
        Q(id, "Kotlin", level, "Prompt $id", "Answer $id", subtopic = subtopic, rubric = List(rubric) { "Point $it" })
    private val bank = listOf(q("a1", "Coroutines"), q("a2", "Coroutines"), q("b1", "Types"), q("b2", "Types"), q("other", "Types", level = 4))
    private val plan = StudyPlan(lv = 2, area = "Kotlin")
    private fun dated(days: Int) = plan.copy(interviewDate = day.plus(days, DateTimeUnit.DAY).toString())

    @Test fun planPoolUsesTargetAndAdjacentLevel() {
        assertEquals(listOf("a1", "a2", "b1", "b2"), planPool(bank, plan).map { it.id })
    }

    @Test fun countdownPhasesFollowDaysLeft() {
        fun phase(days: Int) = dailyPlan(AppState(cards = emptyList(), plan = dated(days)), bank, day, now).phase
        assertEquals(PrepPhase.Build, phase(20))
        assertEquals(PrepPhase.Sharpen, phase(SHARPEN_DAYS))
        assertEquals(PrepPhase.Recap, phase(1))
        assertEquals(PrepPhase.Day, phase(0))
        assertEquals(PrepPhase.Done, phase(-1))
        assertEquals(PrepPhase.Open, dailyPlan(AppState(cards = emptyList(), plan = plan), bank, day, now).phase)
    }

    @Test fun newMaterialStopsBeforeTheInterviewAndNeverExceedsWhatIsLeft() {
        val build = dailyPlan(AppState(cards = emptyList(), plan = dated(30)), bank, day, now)
        assertEquals(3, build.newCount) // Spread over 23 build days, raised to the daily minimum of three.
        assertEquals(0, dailyPlan(AppState(cards = emptyList(), plan = dated(1)), bank, day, now).newCount)
        val allAdded = AppState(cards = emptyList(), plan = dated(30)).addQuestions(bank)
        assertEquals(0, dailyPlan(allAdded, bank, day, now).newCount)
    }

    @Test fun broadPlansCapNewQuestionsPerDay() {
        val big = List(400) { q("q$it", "Topic ${it % 9}") }
        val open = dailyPlan(AppState(cards = emptyList(), plan = plan), big, day, now)
        assertEquals(MAX_NEW_PER_DAY, open.newCount)
        assertEquals(MAX_NEW_PER_DAY, dailyPlan(AppState(cards = emptyList(), plan = dated(10)), big, day, now).newCount)
    }

    @Test fun effectiveDaysFollowCountdownWithinBudgetLimits() {
        assertEquals(10, dated(10).effectiveDays(day))
        assertEquals(120, dated(400).effectiveDays(day))
        assertEquals(1, dated(0).effectiveDays(day))
        assertEquals(plan.days, plan.copy(interviewDate = "not a date").effectiveDays(day))
    }

    @Test fun mockIsDueOnlyAfterTheSpacingInterval() {
        val recent = MockResult(1, now - 1000, now - 1000, "Kotlin", emptyList())
        val state = AppState(cards = emptyList(), plan = dated(5), history = listOf(recent))
        assertFalse(dailyPlan(state, bank, day, now).mockToday)
        assertTrue(dailyPlan(state, bank, day, now + 2 * DAY_MS).mockToday)
        assertTrue(dailyPlan(state.copy(history = emptyList()), bank, day, now).mockToday)
    }

    @Test fun readinessCombinesReviewStageAndLatestMockCoverage() {
        val deck = AppState(cards = emptyList(), plan = plan).addQuestions(bank.take(2))
        val reviewed = deck.copy(cards = deck.cards.map { if (it.questionId == "a1") it.copy(box = 4) else it.copy(box = 2) })
        fun attempt(id: String, score: Int) = MockAttempt(0, id, 1, "Kotlin", "p", "a", "", "", 0, score, 4, emptyList(), emptySet(), emptyList())
        val older = MockResult(1, 0, 10, "Kotlin", listOf(attempt("a1", 4)))
        val latest = MockResult(2, 20, 30, "Kotlin", listOf(attempt("a1", 0), attempt("b1", 2)))
        val r = readiness(reviewed.copy(history = listOf(latest, older)), bank)

        assertEquals(0.6f, r.scores.getValue("a1"), 1e-4f) // Review 1.0 * 0.6 + latest mock 0 * 0.4.
        assertEquals(0.5f, r.scores.getValue("a2"), 1e-4f)
        assertEquals(0.5f, r.scores.getValue("b1"), 1e-4f)
        assertEquals(0f, r.scores.getValue("b2"))
        assertEquals(3, r.practised)
        assertEquals(4, r.total)
        assertEquals(1.6f / 4, r.overall, 1e-4f)
        assertEquals("Types", r.weakest.first().subtopic)
    }

    @Test fun drillPicksLeastMasteredQuestionsInTheTopic() {
        val deck = AppState(cards = emptyList(), plan = plan).addQuestions(listOf(bank[0])).let { s -> s.copy(cards = s.cards.map { it.copy(box = 4) }) }
        val r = readiness(deck, bank)
        val topic = r.topics.first { it.subtopic == "Coroutines" }
        assertEquals(listOf("a2", "a1"), drillQuestions(topic, r.scores).map { it.id })
        assertEquals(listOf("a2"), drillQuestions(topic, r.scores, limit = 1).map { it.id })
    }

    @Test fun backupRejectsInvalidInterviewDate() {
        val good = encodeBackup(AppState(cards = emptyList(), plan = dated(5).copy(target = "Acme · Android")), null)
        assertEquals("Acme · Android", decodeBackup(good, QUESTIONS).state.plan.target)
        val bad = good.replace(dated(5).interviewDate!!, "2026-13-40")
        assertFailsWith<IllegalArgumentException> { decodeBackup(bad, QUESTIONS) }
    }
}
