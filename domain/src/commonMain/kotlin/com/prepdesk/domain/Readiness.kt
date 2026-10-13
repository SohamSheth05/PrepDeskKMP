package com.prepdesk.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.ceil

/** Questions the plan targets: chosen track and the selected experience tier plus the one below it. */
fun planPool(questions: List<Q>, plan: StudyPlan): List<Q> = questions.filter {
    (plan.area == "All" || plan.area == "All areas" || it.area == plan.area) && it.level in maxOf(1, plan.lv - 1)..plan.lv
}

fun StudyPlan.interviewDay(): LocalDate? = interviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
fun StudyPlan.daysLeft(on: LocalDate = today()): Int? = interviewDay()?.let { on.daysUntil(it) }
/** With an interview date, the preparation budget follows the countdown instead of the manual day count. */
fun StudyPlan.effectiveDays(on: LocalDate = today()): Int = daysLeft(on)?.coerceIn(1, 120) ?: days

enum class PrepPhase(val label: String, val advice: String) {
    Open("Steady preparation", "Set an interview date to get a countdown plan."),
    Build("Build", "Learn new questions daily and finish new material a week before the interview."),
    Sharpen("Sharpen", "Fewer new questions. Drill weak topics and practise full mock rounds."),
    Recap("Final recap", "No new material. Review due cards, revisit weak answers, and run one short mock."),
    Day("Interview day", "Light recap only: skim bookmarks and your weakest topic, then rest."),
    Done("Interview passed", "Set your next interview date to start a new countdown."),
}

data class DailyPlan(val phase: PrepPhase, val daysLeft: Int?, val newCount: Int, val mockToday: Boolean)

const val SHARPEN_DAYS = 7
// More new questions than this in a day crowds out review; a broad plan simply takes longer.
const val MAX_NEW_PER_DAY = 15

fun dailyPlan(state: AppState, questions: List<Q>, on: LocalDate = today(), nowMillis: Long = nowMs()): DailyPlan {
    val fresh = planPool(questions, state.plan).count { !state.inDeck(it) }
    val lastMock = state.history.maxOfOrNull { it.completedAt }
    fun mockDue(everyDays: Int) = lastMock == null || nowMillis - lastMock >= (everyDays - 1) * DAY_MS
    val left = state.plan.daysLeft(on)
        ?: return DailyPlan(PrepPhase.Open, null, minOf(fresh, ceilDiv(fresh, state.plan.days).coerceIn(3, MAX_NEW_PER_DAY)), mockDue(3))
    return when {
        left < 0 -> DailyPlan(PrepPhase.Done, left, 0, false)
        left == 0 -> DailyPlan(PrepPhase.Day, 0, 0, false)
        left == 1 -> DailyPlan(PrepPhase.Recap, 1, 0, mockDue(2))
        left <= SHARPEN_DAYS -> DailyPlan(PrepPhase.Sharpen, left, minOf(fresh, 2), mockDue(2))
        // Spread remaining material over the days before the sharpen week.
        else -> DailyPlan(PrepPhase.Build, left, minOf(fresh, ceilDiv(fresh, left - SHARPEN_DAYS).coerceIn(3, MAX_NEW_PER_DAY)), mockDue(3))
    }
}

private fun ceilDiv(a: Int, b: Int) = ceil(a.toDouble() / b.coerceAtLeast(1)).toInt()

data class TopicReadiness(val area: String, val subtopic: String, val questions: List<Q>, val practised: Int, val mastery: Float) {
    val label: String get() = "$area · $subtopic"
}

data class Readiness(val overall: Float, val practised: Int, val total: Int, val topics: List<TopicReadiness>, val scores: Map<String, Float>) {
    /** Lowest mastery first; among equals, larger topics matter more. */
    val weakest: List<TopicReadiness> get() = topics.sortedWith(compareBy<TopicReadiness> { it.mastery }.thenByDescending { it.questions.size })
}

/**
 * Per-question mastery in 0..1. Review stage measures retention; the latest mock checklist
 * measures how completely the answer was explained. Unseen questions count as 0 so coverage
 * is part of readiness.
 */
fun questionScores(state: AppState, pool: List<Q>): Map<String, Float> {
    val ids = pool.map { it.id }.toSet()
    val review = state.cards.filter { it.questionId in ids }.associate { it.questionId!! to it.box / 4f }
    val mock = mutableMapOf<String, Float>()
    // History is newest first, so the first attempt seen is the latest.
    state.history.sortedByDescending { it.completedAt }.forEach { result ->
        result.attempts.forEach { a -> if (a.questionId in ids && a.questionId !in mock) mock[a.questionId!!] = a.score.toFloat() / a.total }
    }
    return pool.associate { q ->
        val r = review[q.id]; val m = mock[q.id]
        q.id to when {
            r != null && m != null -> 0.6f * r + 0.4f * m
            else -> r ?: m ?: 0f
        }
    }
}

fun readiness(state: AppState, questions: List<Q>): Readiness {
    val pool = planPool(questions, state.plan)
    val scores = questionScores(state, pool)
    val practisedIds = state.cards.mapNotNull { it.questionId }.toSet() + state.history.flatMap { r -> r.attempts.mapNotNull { it.questionId } }
    val topics = pool.groupBy { it.area to it.subtopic }.map { (key, qs) ->
        TopicReadiness(key.first, key.second, qs, qs.count { it.id in practisedIds }, qs.map { scores.getValue(it.id) }.average().toFloat())
    }
    return Readiness(if (pool.isEmpty()) 0f else scores.values.average().toFloat(), pool.count { it.id in practisedIds }, pool.size, topics, scores)
}

/** The least-mastered questions in a topic, for a short drill session. */
fun drillQuestions(topic: TopicReadiness, scores: Map<String, Float>, limit: Int = 5): List<Q> =
    topic.questions.sortedWith(compareBy<Q> { scores[it.id] ?: 0f }.thenBy { it.level }.thenBy { it.id }).take(limit)
