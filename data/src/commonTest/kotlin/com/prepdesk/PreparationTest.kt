package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import kotlin.test.*

class PreparationTest {
    private val question = QUESTIONS.first { it.id == "android-search-flow" }

    @Test fun oldOmittedPlanRetainsItsOriginalDefault() {
        val loaded = decodeLegacyState("{\"cards\":[],\"pomos\":3}")
        assertEquals("All", loaded.plan.area)
        assertEquals(3, loaded.pomos)
    }

    @Test fun searchCombinesConceptsAndFilters() {
        val result = filterQuestions(QUESTIONS, QuestionFilter(area = "Android", search = "search stale", round = "Coding"), emptySet())
        assertTrue(result.any { it.id == question.id })
        assertTrue(result.all { it.area == "Android" && it.round == "Coding" })
        assertEquals(listOf(question), filterQuestions(listOf(question), QuestionFilter(bookmarkedOnly = true), setOf(question.id)))
        assertTrue(filterQuestions(listOf(question), QuestionFilter(bookmarkedOnly = true), emptySet()).isEmpty())
    }

    @Test fun additionsUseStableQuestionIdsAndNeverDuplicate() {
        val start = AppState(cards = emptyList()).addQuestions(listOf(question, question))
        assertEquals(1, start.cards.size)
        assertEquals(question.id, start.cards.single().questionId)
        assertEquals(start, start.addQuestions(listOf(question.copy(q = "Updated wording", version = 2))))
    }

    @Test fun personalAnswersDoNotBlockLibraryRevision() {
        val personal = Card(1, "Personal", question.q, "My project-specific answer", box = 3, due = Long.MAX_VALUE)
        val start = AppState(cards = listOf(personal))
        assertFalse(start.inDeck(question))
        val added = start.addQuestions(listOf(question))
        assertEquals(2, added.cards.size)
        assertEquals(personal, added.cards.first())
        assertEquals(question.id, added.cards.last().questionId)
        assertTrue(added.inDeck(question))
    }

    @Test fun legacyMigrationPreservesCustomContentAndReviewProgress() {
        val custom = Card(2, "Personal", question.q, "My own answer", 3, 5000)
        val matching = Card(1, question.area, question.q, question.a, 2, 4000)
        val legacy = AppState(cards = listOf(matching, custom), days = mapOf("2026-10-01" to 4), pomos = 7)
        val migrated = migrateLegacy(legacy, listOf(question))
        assertEquals(question.id, migrated.cards[0].questionId)
        assertNull(migrated.cards[1].questionId)
        assertEquals(custom, migrated.cards[1])
        assertEquals(2, migrated.cards[0].box)
        assertEquals(4000L, migrated.cards[0].due)
        assertEquals(legacy.days, migrated.days)
        assertEquals(7, migrated.pomos)
    }

    @Test fun migrationRejectsDuplicateIdsInsteadOfLosingCards() {
        val c = Card(1, "Personal", "Question", "Answer")
        assertFailsWith<IllegalArgumentException> { migrateLegacy(AppState(cards = listOf(c, c.copy(q = "Another"))), emptyList()) }
    }

    @Test fun assessmentIgnoresInvalidChecklistIndicesAndSnapshotsAnswers() {
        val c = Card(1, question.area, question.q, question.a, questionId = question.id)
        val run = MockRun(1, 0, "Android", listOf(c), listOf(question), stage = 2, draft = "My reasoning", checked = setOf(0, 99), elapsed = 48)
        val attempt = run.assess()
        assertEquals(1, attempt.score)
        assertEquals(question.rubric.size, attempt.total)
        assertEquals(setOf(0), attempt.checked)
        assertEquals("My reasoning", attempt.userAnswer)
        assertEquals(48, attempt.elapsedSeconds)
        assertTrue(attempt.weak)
        assertFalse(run.copy(checked = question.rubric.indices.toSet()).assess().weak)
    }

    @Test fun missedCardsAreDueAndCorrectCardsAdvance() {
        val c = Card(1, "Android", "Question", "Answer", box = 2)
        val before = nowMs()
        val correct = AppState(cards = listOf(c)).rate(1, true).cards.single()
        assertEquals(3, correct.box)
        assertTrue(correct.due >= before + 7 * DAY_MS)
        val missed = AppState(cards = listOf(correct)).rate(1, false)
        assertEquals(0, missed.cards.single().box)
        assertEquals(1, missed.dueCards(null).size)
    }

    @Test fun allTracksHaveSourcedExercisesAtEveryLevel() {
        val sources = SOURCES.map { it.id }.toSet()
        val areas = questionAreas(QUESTIONS)
        assertTrue(areas.containsAll(listOf("Android", "Java and Spring", "Python", "DevOps and Cloud", "Go", "Rust", "C# and .NET", "React", "iOS and Swift", "Flutter")))
        assertEquals(QUESTIONS.size, QUESTIONS.map { it.id }.distinct().size)
        for (area in areas) for (level in 1..4) {
            val enriched = QUESTIONS.filter { it.area == area && it.level == level && it.explanation.isNotBlank() }
            assertTrue(enriched.size >= 2, "$area level $level has insufficient exercises")
            assertTrue(enriched.all { q -> q.rubric.size >= 3 && q.followups.size >= 2 && q.mistake.isNotBlank() && q.sourceIds.isNotEmpty() && q.sourceIds.all { it in sources } })
        }
    }

    @Test fun mockSelectionHonorsEachTrackAndExperienceAndBalancesRounds() {
        for (area in questionAreas(QUESTIONS)) for (level in 0..4) {
            val pool = QUESTIONS.filter { it.practical == null && it.area == area && (level == 0 || it.level == level) }
            val selected = selectMockCards(QUESTIONS, emptyList(), area, level)
            assertEquals(minOf(5, pool.size), selected.size)
            assertEquals(selected.size, selected.map { it.questionId }.distinct().size)
            assertTrue(selected.all { card -> pool.any { it.id == card.questionId } })
            val selectedRounds = selected.map { c -> pool.first { it.id == c.questionId }.round }.distinct()
            val rich = pool.filter { it.rubric.isNotEmpty() && it.explanation.isNotBlank() }
            val roundPool = if (rich.size >= 5) rich else pool
            assertEquals(minOf(5, roundPool.map { it.round }.distinct().size), selectedRounds.size)
            if (level == 0) assertTrue(selected.all { c -> pool.first { it.id == c.questionId }.rubric.isNotEmpty() }, "$area should prefer enriched exercises")
        }
        assertTrue(selectMockCards(QUESTIONS, emptyList(), "Unsupported track", 0).isEmpty())
        val custom = Card(1, "Personal", "My question", "My answer")
        assertEquals(listOf(custom), selectMockCards(QUESTIONS, listOf(custom), "My deck", 4))
    }

    @Test fun nonAndroidSearchAndBookmarksUseTheSameLibraryFilters() {
        val python = QUESTIONS.first { it.id == "python-async-block" }
        assertEquals(listOf(python), filterQuestions(QUESTIONS, QuestionFilter(area = "Python", level = 2,
            round = "Debugging", search = "blocking synchronous", bookmarkedOnly = true), setOf(python.id)))
        assertTrue(filterQuestions(QUESTIONS, QuestionFilter(area = "Go", search = "race"), emptySet()).any { it.id == "go-race" })
    }

    @Test fun studyPoolsRespectTechnologyExperienceAndTopic() {
        for (area in questionAreas(QUESTIONS)) for (level in 1..4) {
            val pool = studyQuestions(QUESTIONS, area, level, "All topics")
            assertTrue(pool.isNotEmpty())
            assertTrue(pool.all { it.area == area && it.level == level })
            val topic = pool.first().subtopic
            assertEquals(pool.filter { it.subtopic == topic }, studyQuestions(QUESTIONS, area, level, topic))
        }
        assertTrue(studyQuestions(QUESTIONS, "Unsupported", 0, "All topics").isEmpty())
    }

    @Test fun studyReviewsRequireRevealAndSaveRevisionWithoutDuplicates() {
        val session = StudySession(question.area, question.level, question.subtopic, listOf(question.id))
        val before = AppState(cards = emptyList(), study = session)
        assertEquals(before, before.reviewStudy(question, true))
        val missed = before.copy(study = session.copy(revealed = true)).reviewStudy(question, false)
        assertTrue(missed.study!!.complete)
        assertFalse(missed.study!!.revealed)
        assertEquals(setOf(question.id), missed.study!!.reviewed)
        assertEquals(1, missed.dueCards(question.area).size)
        assertEquals(missed, missed.reviewStudy(question, false))
        val revisited = missed.copy(study = session.copy(revealed = true)).reviewStudy(question, true)
        assertEquals(1, revisited.cards.size)
        assertEquals(1, revisited.cards.single().box)
        assertTrue(revisited.dueCards(question.area).isEmpty())
        assertEquals(before, before.copy(study = session.copy(revealed = true)).reviewStudy(question.copy(id = "wrong"), true).copy(study = session))
    }

    @Test fun readingCatalogCoversEveryTrackAndHonorsExperience() {
        for (area in questionAreas(QUESTIONS)) for (level in 0..4) {
            val links = readingLinks(QUESTIONS, SOURCES, area, level, STUDY_RESOURCES)
            assertTrue(links.isNotEmpty(), "$area level $level has no references")
            assertEquals(links.size, links.map { it.source.id }.distinct().size)
        }
        val all = readingLinks(QUESTIONS, SOURCES, "Android", 0, STUDY_RESOURCES).map { it.source.id }
        assertTrue(all.containsAll(listOf("amit-android", "android-midlevel-blog", "android-modern-blog", "android-lifecycle-blog", "kotlin-interview-blog", "android-experienced-blog")))
        val junior = readingLinks(QUESTIONS, SOURCES, "Android", 1, STUDY_RESOURCES).map { it.source.id }
        assertFalse("android-experienced-blog" in junior)
        assertFalse("android-midlevel-blog" in junior)
        assertTrue(readingLinks(QUESTIONS, SOURCES, "React", 0, STUDY_RESOURCES).any { it.source.id == "react-interviews" })
        assertTrue(readingLinks(QUESTIONS, SOURCES, "Python", 0, STUDY_RESOURCES).any { it.source.id == "python-interviews" })
    }
}
