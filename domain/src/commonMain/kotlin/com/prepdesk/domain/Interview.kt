package com.prepdesk.domain

import kotlinx.serialization.Serializable

@Serializable data class AIFeedback(val summary: String, val strengths: List<String>, val improvements: List<String>, val covered: List<Int>)

@Serializable
data class MockRun(
    val id: Long, val startedAt: Long, val area: String, val cards: List<Card>, val questions: List<Q?>,
    val index: Int = 0, val stage: Int = 0, val draft: String = "", val followupNotes: String = "",
    val checked: Set<Int> = emptySet(), val deadline: Long = nowMs() + 120_000,
    val elapsed: Int = 0, val attempts: List<MockAttempt> = emptyList(),
    val aiFeedback: AIFeedback? = null,
)

@Serializable
data class MockAttempt(
    val position: Int, val questionId: String?, val version: Int, val topic: String, val prompt: String,
    val answer: String, val userAnswer: String, val followupNotes: String, val elapsedSeconds: Int,
    val score: Int, val total: Int, val expected: List<String>, val checked: Set<Int>, val followups: List<String>,
    val aiFeedback: AIFeedback? = null,
) { val weak: Boolean get() = score * 4 < total * 3 }

@Serializable
data class MockResult(val id: Long, val startedAt: Long, val completedAt: Long, val area: String, val attempts: List<MockAttempt>) {
    val score: Int get() = attempts.sumOf { it.score }
    val total: Int get() = attempts.sumOf { it.total }
}

fun MockRun.assess(solid: Boolean? = null): MockAttempt {
    val card = cards[index]
    val q = questions[index]
    val expected = q?.rubric.orEmpty()
    val selected = checked.filter { it in expected.indices }.toSet()
    return MockAttempt(index, card.questionId, q?.version ?: 1, card.topic, card.q, card.a, draft, followupNotes,
        elapsed, if (expected.isEmpty()) { if (solid == true) 1 else 0 } else selected.size,
        expected.size.coerceAtLeast(1), expected, selected, q?.followups.orEmpty(), aiFeedback)
}

/** Applies a transcript only if the round is still on the same question and stage as [at]. */
fun MockRun.withTranscript(at: MockRun, text: String): MockRun = when {
    id != at.id || index != at.index || stage != at.stage -> this
    at.stage == 0 -> copy(draft = text, aiFeedback = null)
    else -> copy(followupNotes = text, aiFeedback = null)
}

fun AppState.addQuestions(qs: List<Q>): AppState {
    val haveIds = cards.mapNotNull { it.questionId }.toSet()
    val haveText = cards.map { it.q to it.a }.toSet()
    val fresh = qs.distinctBy { it.id }.filter { it.id !in haveIds && (it.q to it.a) !in haveText }
    val firstId = maxOf(nowMs(), (cards.maxOfOrNull { it.id } ?: 0) + 1)
    return copy(cards = cards + fresh.mapIndexed { i, q -> Card(firstId + i, q.area, q.q, q.a, questionId = q.id) })
}
