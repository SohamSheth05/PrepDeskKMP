package com.prepdesk.domain

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class StudySession(
    val area: String, val level: Int, val subtopic: String, val questionIds: List<String>,
    val index: Int = 0, val revealed: Boolean = false, val reviewed: Set<String> = emptySet(),
) {
    val currentId: String? get() = questionIds.getOrNull(index)
    val complete: Boolean get() = index >= questionIds.size
}

fun studyQuestions(questions: List<Q>, area: String, level: Int, subtopic: String): List<Q> =
    filterQuestions(questions, QuestionFilter(area = area, level = level, subtopic = subtopic), emptySet())
        .sortedWith(compareBy<Q> { it.explanation.isBlank() }.thenBy { it.level }.thenBy { it.subtopic }.thenBy { it.id })

fun AppState.confidentQuestionIds(): Set<String> = cards.filter { it.box > 0 }.mapNotNull { it.questionId }.toSet()

// Shuffle within confidence groups so short quests prioritize learning without a fixed repeated order.
fun questQuestions(pool: List<Q>, state: AppState, limit: Int, random: Random = Random.Default): List<Q> {
    val confident = state.confidentQuestionIds()
    return pool.distinctBy { it.id }.shuffled(random).sortedBy { it.id in confident }.take(limit.coerceAtLeast(0))
}

fun studySessionFor(bank: List<Q>, area: String, level: Int, topic: String, selectedIds: List<String>? = null): StudySession? {
    val eligible = studyQuestions(bank, area, level, topic).map { it.id }
    val ids = selectedIds?.filter { it in eligible }?.distinct() ?: eligible
    return ids.takeIf { it.isNotEmpty() }?.let { StudySession(area, level, topic, it) }
}

fun AppState.assessPractical(q: Q, solved: Boolean): AppState {
    val added = addQuestions(listOf(q))
    val card = added.cards.first { it.questionId == q.id || (it.q == q.q && it.a == q.a) }
    return added.rate(card.id, solved)
}

/** Reveals the answer, if [q] is still the quest's current question. */
fun AppState.revealStudy(q: Q): AppState = study?.takeIf { it.currentId == q.id }?.let { copy(study = it.copy(revealed = true)) } ?: this

/** Moves past [q] without assessing it, if it is still the current question. */
fun AppState.skipStudy(q: Q): AppState = study?.takeIf { it.currentId == q.id }?.let { copy(study = it.copy(index = it.index + 1, revealed = false)) } ?: this

fun AppState.reviewStudy(q: Q, confident: Boolean): AppState {
    val session = study ?: return this
    if (session.currentId != q.id || !session.revealed) return this
    val added = addQuestions(listOf(q))
    val card = added.cards.first { it.questionId == q.id || (it.q == q.q && it.a == q.a) }
    return added.rate(card.id, confident).copy(study = session.copy(
        index = session.index + 1, revealed = false, reviewed = session.reviewed + q.id,
    ))
}
