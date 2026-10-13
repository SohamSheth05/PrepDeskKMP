package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.*

class GameTest {
    private val day = LocalDate(2026, 10, 9)

    @Test fun levelsNeedGrowingXp() {
        assertEquals(PlayerLevel(1, "Rookie", 0, 100), playerLevel(0))
        assertEquals(1, playerLevel(99).level)
        assertEquals(PlayerLevel(2, "Apprentice", 0, 200), playerLevel(100))
        assertEquals(3, playerLevel(300).level)
        assertEquals(4, playerLevel(600).level)
        assertEquals("Legend", playerLevel(1_000_000).title)
    }

    @Test fun xpComesOnlyFromGrowingCounters() {
        val attempt = MockAttempt(0, null, 1, "t", "p", "a", "", "", 0, 3, 4, emptyList(), emptySet(), emptyList())
        val s = AppState(cards = emptyList(), days = mapOf("2026-10-08" to 2, "2026-10-09" to 3), pomos = 2,
            history = listOf(MockResult(1, 0, 1, "Kotlin", listOf(attempt))), arcade = Arcade(rushPoints = 250, sweeps = setOf("2026-10-08")))
        assertEquals(5 * 10 + 3 * 5 + 2 * 15 + 25 + SWEEP_XP, s.xp())
        // A missed review lowers a card's stage but never XP.
        val q = QUESTIONS.first()
        val reviewed = s.addQuestions(listOf(q)).let { it.rate(it.cards.single().id, true) }
        assertTrue(reviewed.rate(reviewed.cards.single().id, false).xp() > reviewed.xp() - 1)
    }

    @Test fun longestStreakSurvivesGaps() {
        val days = listOf("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-05", "2026-10-06").associateWith { 1 }
        assertEquals(3, AppState(cards = emptyList(), days = days).longestStreak())
        assertEquals(0, AppState(cards = emptyList()).longestStreak())
    }

    @Test fun rushQuestionsHaveFourDistinctOptionsIncludingTheAnswer() {
        val rush = buildRush(QUESTIONS, "Kotlin", random = Random(1))
        assertTrue(rush.size >= 20)
        rush.forEach { r ->
            assertEquals(4, r.options.size)
            assertEquals(4, r.options.distinct().size)
            assertEquals(r.q.a, r.options[r.correct])
            assertEquals("Kotlin", r.q.area)
            assertNull(r.q.practical)
        }
        assertTrue(buildRush(QUESTIONS, "Unknown track").isEmpty())
        // All tracks keeps distractors within the question's own track.
        val byAnswer = QUESTIONS.groupBy { it.a }
        buildRush(QUESTIONS, ALL_TRACKS, count = 30, random = Random(2)).forEach { r ->
            assertTrue(r.options.all { option -> byAnswer.getValue(option).any { it.area == r.q.area } })
        }
    }

    @Test fun combosMultiplyPointsAndMissesResetThem() {
        val q = QUESTIONS.first()
        var s = RushScore()
        repeat(3) { s = s.answer(q, true) }
        assertEquals(30, s.score)
        assertEquals(2, s.multiplier)
        s = s.answer(q, true)
        assertEquals(50, s.score)
        s = s.answer(q, false)
        assertEquals(1, s.multiplier)
        assertEquals(4, s.bestCombo)
        assertEquals(listOf(q.id), s.missed)
        repeat(20) { s = s.answer(q, true) }
        assertEquals(4, s.multiplier)
    }

    @Test fun recordingAGameKeepsBestsAndTodaysBest() {
        val first = AppState(cards = emptyList()).recordRush("Kotlin", RushScore(score = 120), day)
        val second = first.recordRush("Kotlin", RushScore(score = 80), day)
        assertEquals(120, second.arcade.bestRush["Kotlin"])
        assertEquals(120, second.arcade.lastRushBest)
        assertEquals(2, second.arcade.rushGames)
        assertEquals(200, second.arcade.rushPoints)
        val tomorrow = second.recordRush("Kotlin", RushScore(score = 40), day.plus(1, DateTimeUnit.DAY))
        assertEquals(40, tomorrow.arcade.lastRushBest)
        assertEquals(120, tomorrow.arcade.bestRush["Kotlin"])
    }

    @Test fun dailyChallengesAreStableVariedAndIncludeAGame() {
        val state = AppState(cards = emptyList())
        val picks = (0 until 14).map { dailyChallenges(state, day.plus(it, DateTimeUnit.DAY)).map { c -> c.id } }
        picks.forEach { ids ->
            assertEquals(3, ids.distinct().size)
            assertEquals(1, ids.count { it.startsWith("rush") })
            assertTrue(ids.count { it.startsWith("actions") } <= 1)
        }
        assertEquals(picks.first(), dailyChallenges(state, day).map { it.id })
        assertTrue(picks.distinct().size > 1)
    }

    @Test fun challengeProgressReflectsTodaysWork() {
        val played = AppState(cards = emptyList(), days = mapOf(day.toString() to 12)).recordRush("Kotlin", RushScore(score = 150), day)
        val todays = dailyChallenges(played, day).filter { it.id.startsWith("rush") || it.id.startsWith("actions") }
        assertTrue(todays.isNotEmpty())
        todays.forEach { assertTrue(it.done, it.id) }
        // Yesterday's game does not count toward today's challenges.
        val tomorrow = day.plus(1, DateTimeUnit.DAY)
        assertTrue(dailyChallenges(played, tomorrow).filter { it.id.startsWith("rush") }.none { it.done })
    }

    @Test fun earnedBadgesAreNotCelebratedTwice() {
        val all = badges(QUESTIONS)
        val state = AppState(cards = emptyList(), days = mapOf(day.toString() to 1))
        assertTrue(newBadges(state, all, 0f).any { it.id == "first-step" })
        val seen = state.copy(arcade = Arcade(earned = setOf("first-step")))
        assertTrue(newBadges(seen, all, 0f).none { it.id == "first-step" })
        assertTrue(newBadges(state, all, 0.6f).any { it.id == "ready-50" })
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test fun gameProgressSurvivesBackupAndRejectsBadDates() {
        val state = AppState(cards = emptyList(), arcade = Arcade(bestRush = mapOf("Kotlin" to 90), rushGames = 2, earned = setOf("rush-1"), seenLevel = 2, sweeps = setOf("2026-10-08")))
        val restored = decodeBackup(encodeBackup(state, null), QUESTIONS).state.arcade
        assertEquals(state.arcade, restored)
        val bad = encodeBackup(state.copy(arcade = state.arcade.copy(sweeps = setOf("yesterday"))), null)
        assertFailsWith<IllegalArgumentException> { decodeBackup(bad, QUESTIONS) }
    }
}
