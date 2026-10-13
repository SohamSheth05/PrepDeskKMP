package com.prepdesk.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Persisted game state. Everything else (XP, level, challenge progress) is derived from study
 * progress so it stays consistent across backups and sync.
 */
@Serializable
data class Arcade(
    val bestRush: Map<String, Int> = emptyMap(), val rushGames: Int = 0, val rushPoints: Int = 0,
    val lastRushDate: String? = null, val lastRushBest: Int = 0,
    /** Badges and level already celebrated; earned badges stay earned even if progress later dips. */
    val earned: Set<String> = emptySet(), val seenLevel: Int = 0,
    /** Days on which every daily challenge was completed. */
    val sweeps: Set<String> = emptySet(),
)

private fun dateOf(millis: Long): LocalDate = kotlin.time.Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date

// ---------- XP and levels ----------

const val SWEEP_XP = 50
val LEVEL_TITLES = listOf("Rookie", "Apprentice", "Problem Solver", "Debugger", "Builder", "Architect", "Interview Pro", "Tech Lead", "Hiring Magnet", "Legend")

/** Only growing counters feed XP, so a missed review never takes XP away. */
fun AppState.xp(): Int = days.values.sum() * 10 + history.sumOf { it.score } * 5 + pomos * 15 + arcade.rushPoints / 10 + arcade.sweeps.size * SWEEP_XP

data class PlayerLevel(val level: Int, val title: String, val into: Int, val needed: Int) {
    val progress: Float get() = into.toFloat() / needed
}

/** Level n needs 100·n more XP than level n-1: 0, 100, 300, 600, 1000, … */
fun playerLevel(xp: Int): PlayerLevel {
    var level = 1; var floor = 0
    while (xp >= floor + level * 100) { floor += level * 100; level++ }
    return PlayerLevel(level, LEVEL_TITLES[minOf(level - 1, LEVEL_TITLES.lastIndex)], xp - floor, level * 100)
}

fun AppState.longestStreak(): Int {
    val dates = days.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.distinct().sorted()
    var best = 0; var run = 0; var previous: LocalDate? = null
    for (d in dates) {
        run = if (previous != null && d.minus(1, DateTimeUnit.DAY) == previous) run + 1 else 1
        best = maxOf(best, run); previous = d
    }
    return best
}

data class PlayerSummary(val level: PlayerLevel, val streak: Int, val badgesEarned: Int, val badgesTotal: Int, val bestRush: Int)

fun playerSummary(state: AppState, all: List<Badge>) = PlayerSummary(playerLevel(state.xp()), state.streak(),
    all.count { it.id in state.arcade.earned }, all.size, state.arcade.bestRush.values.maxOrNull() ?: 0)

// ---------- Badges ----------

data class Badge(val id: String, val icon: String, val title: String, val description: String, val earned: (AppState, Float) -> Boolean)

private fun AppState.solvedPracticals(questions: List<Q>): Int {
    val ids = questions.filter { it.practical != null }.map { it.id }.toSet()
    return cards.count { it.questionId in ids && it.box > 0 }
}

fun badges(questions: List<Q>): List<Badge> = listOf(
    Badge("first-step", "🌱", "First step", "Complete your first study action") { s, _ -> s.days.isNotEmpty() },
    Badge("streak-3", "🔥", "On fire", "Study 3 days in a row") { s, _ -> s.longestStreak() >= 3 },
    Badge("streak-7", "⚡", "Week warrior", "Study 7 days in a row") { s, _ -> s.longestStreak() >= 7 },
    Badge("streak-30", "🏆", "Unstoppable", "Study 30 days in a row") { s, _ -> s.longestStreak() >= 30 },
    Badge("mock-1", "🎤", "Mic check", "Finish a mock interview round") { s, _ -> s.history.isNotEmpty() },
    Badge("mock-perfect", "💯", "Flawless", "Cover every expected point in a mock round") { s, _ -> s.history.any { it.total > 0 && it.score == it.total } },
    Badge("mock-10", "🎯", "Mock marathon", "Finish 10 mock rounds") { s, _ -> s.history.size >= 10 },
    Badge("master-10", "🧠", "Memory palace", "Reach the top review stage on 10 cards") { s, _ -> s.cards.count { it.box >= 4 } >= 10 },
    Badge("practical-1", "🛠️", "Hands-on", "Solve a practical exercise") { s, _ -> s.solvedPracticals(questions) >= 1 },
    Badge("practical-5", "🧪", "Builder", "Solve 5 practical exercises") { s, _ -> s.solvedPracticals(questions) >= 5 },
    Badge("polyglot", "🌍", "Polyglot", "Practise questions from 3 tracks") { s, _ -> s.cards.mapNotNull { c -> c.questionId?.let { c.topic } }.distinct().size >= 3 },
    Badge("focus-5", "⏱️", "Deep focus", "Finish 5 focus sessions") { s, _ -> s.pomos >= 5 },
    Badge("rush-1", "🎮", "Player one", "Play Answer Rush") { s, _ -> s.arcade.rushGames >= 1 },
    Badge("rush-200", "🚀", "Rush hour", "Score 200 in one Answer Rush game") { s, _ -> (s.arcade.bestRush.values.maxOrNull() ?: 0) >= 200 },
    Badge("countdown", "📅", "Locked in", "Set your interview date") { s, _ -> s.plan.interviewDate != null },
    Badge("ready-50", "📈", "Halfway there", "Reach 50% readiness on your plan") { _, ready -> ready >= 0.5f },
)

fun newBadges(state: AppState, all: List<Badge>, readiness: Float): List<Badge> =
    all.filter { it.id !in state.arcade.earned && it.earned(state, readiness) }

// ---------- Daily challenges ----------

/** Where a challenge is completed; the UI maps this to a screen. */
enum class ChallengeTarget { Study, Game, Mock, Review }

data class Challenge(val id: String, val icon: String, val title: String, val progress: Int, val goal: Int, val target: ChallengeTarget) {
    val done: Boolean get() = progress >= goal
}

fun dailyChallenges(state: AppState, on: LocalDate = today(), nowMillis: Long = nowMs()): List<Challenge> {
    val actions = state.days[on.toString()] ?: 0
    val playedToday = state.arcade.lastRushDate == on.toString()
    val rushToday = if (playedToday) state.arcade.lastRushBest else 0
    val mocks = state.history.count { dateOf(it.completedAt) == on }
    val due = state.dueCards(null).size
    val pool = listOf(
        Challenge("actions-5", "📚", "Warm up: 5 study actions", minOf(actions, 5), 5, ChallengeTarget.Study),
        Challenge("actions-12", "💪", "Grind: 12 study actions", minOf(actions, 12), 12, ChallengeTarget.Study),
        Challenge("rush-play", "🎮", "Play a round of Answer Rush", if (playedToday) 1 else 0, 1, ChallengeTarget.Game),
        Challenge("rush-100", "🚀", "Score 100+ in Answer Rush", minOf(rushToday, 100), 100, ChallengeTarget.Game),
        Challenge("mock", "🎤", "Finish a mock round", minOf(mocks, 1), 1, ChallengeTarget.Mock),
        Challenge("due", "🧹", "Clear every due review card", if (due == 0 && actions > 0) 1 else 0, 1, ChallengeTarget.Review),
    )
    // A stable daily pick: everyone gets variety, and the set does not change during the day.
    val random = Random(on.toEpochDays().toLong() * 31 + 7)
    val study = pool.filter { it.id.startsWith("actions") || it.id == "due" }.random(random)
    val game = pool.filter { it.id.startsWith("rush") }.random(random)
    val third = pool.filter { it != study && it != game && !(it.id.startsWith("actions") && study.id.startsWith("actions")) && !it.id.startsWith("rush") }.random(random)
    return listOf(study, game, third)
}

// ---------- Answer Rush ----------

const val RUSH_SECONDS = 60
const val RUSH_PENALTY_MS = 3_000L

data class RushQuestion(val q: Q, val options: List<String>, val correct: Int)

/** Multiple choice from short answers; distractors are other answers from the same track, preferring other subtopics. */
fun buildRush(questions: List<Q>, area: String, count: Int = 40, random: Random = Random.Default): List<RushQuestion> {
    val pool = questions.filter { it.practical == null && it.a.isNotBlank() && it.a.length <= 220 && (area == ALL_TRACKS || it.area == area) }
        .distinctBy { it.a.trim() }
    if (pool.size < 4) return emptyList()
    return pool.shuffled(random).take(count).map { q ->
        val others = pool.filter { it.id != q.id && (area != ALL_TRACKS || it.area == q.area) }.shuffled(random)
        val distractors = (others.filter { it.subtopic != q.subtopic } + others.filter { it.subtopic == q.subtopic })
            .map { it.a }.distinct().take(3)
            .let { if (it.size == 3) it else (it + pool.filter { o -> o.id != q.id && o.a !in it }.shuffled(random).map { o -> o.a }).distinct().take(3) }
        val options = (distractors + q.a).shuffled(random)
        RushQuestion(q, options, options.indexOf(q.a))
    }
}

const val ALL_TRACKS = "All tracks"

data class RushScore(val score: Int = 0, val combo: Int = 0, val bestCombo: Int = 0, val correct: Int = 0, val answered: Int = 0, val missed: List<String> = emptyList()) {
    /** ×1, then ×2 from 3 in a row, ×3 from 6, ×4 from 9. */
    val multiplier: Int get() = minOf(4, 1 + combo / 3)
    fun answer(q: Q, right: Boolean): RushScore =
        if (right) copy(score = score + 10 * multiplier, combo = combo + 1, bestCombo = maxOf(bestCombo, combo + 1), correct = correct + 1, answered = answered + 1)
        else copy(combo = 0, answered = answered + 1, missed = missed + q.id)
}

fun AppState.recordSweep(day: String): AppState = if (day in arcade.sweeps) this else copy(arcade = arcade.copy(sweeps = arcade.sweeps + day))

/** Marks badges and a level as already celebrated. */
fun AppState.acknowledgeRewards(badgeIds: List<String>, level: Int): AppState =
    copy(arcade = arcade.copy(earned = arcade.earned + badgeIds, seenLevel = maxOf(arcade.seenLevel, level)))

fun AppState.recordRush(area: String, result: RushScore, on: LocalDate = today()): AppState {
    val date = on.toString()
    val a = arcade
    return logged().copy(arcade = a.copy(
        bestRush = a.bestRush + (area to maxOf(a.bestRush[area] ?: 0, result.score)),
        rushGames = a.rushGames + 1, rushPoints = a.rushPoints + result.score,
        lastRushDate = date, lastRushBest = if (a.lastRushDate == date) maxOf(a.lastRushBest, result.score) else result.score,
    ))
}
