package com.prepdesk.domain

import kotlin.test.*

class TutorTest {
    private val flow = Q("flow", "Kotlin", 2, "How do StateFlow and SharedFlow differ?", "StateFlow holds a current value; SharedFlow broadcasts events.",
        subtopic = "Flow", explanation = "Use StateFlow for screen state.", followups = listOf("When would replay help?"))
    private val room = Q("room", "Android", 2, "How do you migrate a Room schema?", "Write a Migration and test it.", subtopic = "Database",
        code = "val MIGRATION_1_2 = object : Migration(1, 2) { }")
    private val bank = listOf(room, flow)

    @Test fun relatedQuestionsRankPromptAndTopicMatchesFirst() {
        assertEquals(listOf("flow"), relatedQuestions(bank, "StateFlow vs SharedFlow?").map { it.id })
        assertEquals("room", relatedQuestions(bank, "room migration")[0].id)
        assertTrue(relatedQuestions(bank, "what is the difference").isEmpty(), "Stop words alone match nothing")
    }

    @Test fun libraryAnswerQuotesTheClosestQuestionOrSaysItFoundNothing() {
        val answer = libraryAnswer(bank, "When should I use StateFlow?")
        assertEquals(TutorSource.Library, answer.source)
        assertTrue(answer.text.contains(flow.a) && answer.text.contains(flow.explanation))
        assertEquals(listOf("flow"), answer.related)
        assertTrue(libraryAnswer(bank, "quantum chromodynamics").related.isEmpty())
    }

    @Test fun oneTapActionsUseSavedContentOffline() {
        assertTrue(libraryAnswer(room, TutorAction.Code).text.contains("MIGRATION_1_2"))
        assertTrue(libraryAnswer(flow, TutorAction.Code).text.contains("no code sample"))
        assertTrue(libraryAnswer(flow, TutorAction.FollowUps).text.contains("1. When would replay help?"))
        assertTrue(libraryAnswer(flow, TutorAction.Explain).text.contains(flow.explanation))
        assertEquals("Show code: ${room.q}", TutorAction.Code.request(room))
    }
}
