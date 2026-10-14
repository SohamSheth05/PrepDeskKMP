package com.prepdesk.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serialization settings shared by stored documents, backups, and synced progress. */
val CONTENT_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class SyncMeta(val ownerId: String? = null, val revision: Long = 0, val dirty: Boolean = true, val lastSyncAt: Long = 0)

@Serializable
data class FocusSession(val focus: Boolean = true, val remainingMs: Long = 1_500_000, val deadline: Long? = null) {
    fun remaining(at: Long = nowMs()) = (deadline?.minus(at) ?: remainingMs).coerceAtLeast(0)
}

fun AppState.finishFocus(at: Long): AppState {
    val deadline = focus.deadline ?: return this
    if (deadline > at) return this
    return (if (focus.focus) logged().copy(pomos = pomos + 1) else this).copy(
        focus = FocusSession(!focus.focus, if (focus.focus) 300_000 else 1_500_000),
    )
}

/** Starts or pauses the timer, keeping the time left when paused. */
fun AppState.toggleFocus(at: Long): AppState = copy(focus = focus.deadline?.let { focus.copy(remainingMs = focus.remaining(at), deadline = null) }
    ?: focus.copy(deadline = at + focus.remainingMs))

@Serializable
data class ProgressBackup(val format: Int = 1, val createdAt: Long = nowMs(), val state: AppState, val activeMock: MockRun? = null)

fun encodeBackup(state: AppState, mock: MockRun?) = CONTENT_JSON.encodeToString(
    ProgressBackup(state = state.copy(sync = SyncMeta()), activeMock = mock),
)

fun decodeBackup(document: String, bank: List<Q>): ProgressBackup {
    require(document.encodeToByteArray().size <= 2_000_000) { "Backup exceeds the 2 MB limit" }
    val backup = CONTENT_JSON.decodeFromString<ProgressBackup>(document)
    require(backup.format == 1) { "This backup needs a newer version of MockMentor" }
    val s = backup.state
    require(s.cards.size <= 10_000 && s.history.size <= 2_000 && s.days.size <= 10_000)
    require(s.pomos >= 0 && s.plan.lv in 1..4 && s.plan.days in 1..120)
    require(s.plan.interviewDate == null || runCatching { kotlinx.datetime.LocalDate.parse(s.plan.interviewDate) }.isSuccess) { "Invalid interview date" }
    require(s.plan.target.length <= 200)
    s.arcade.let { a ->
        require(a.bestRush.size <= 100 && a.bestRush.all { it.key.length <= 100 && it.value in 0..1_000_000 } && a.rushGames >= 0 && a.rushPoints >= 0 && a.lastRushBest >= 0 && a.seenLevel >= 0) { "Invalid game progress" }
        require(a.earned.size <= 200 && a.sweeps.size <= 10_000 && (a.sweeps + listOfNotNull(a.lastRushDate)).all { runCatching { kotlinx.datetime.LocalDate.parse(it) }.isSuccess }) { "Invalid game progress" }
    }
    require(s.cards.map { it.id }.distinct().size == s.cards.size) { "Duplicate card IDs" }
    require(s.cards.all { it.id >= 0 && it.box in 0..4 && it.due >= 0 && it.q.isNotBlank() && it.a.isNotBlank() && it.q.length <= 20_000 && it.a.length <= 100_000 }) { "Invalid revision cards" }
    require(s.days.all { (date, count) -> runCatching { kotlinx.datetime.LocalDate.parse(date) }.isSuccess && count >= 0 })
    require(s.history.map { it.id }.distinct().size == s.history.size)
    require(s.history.all { result -> result.attempts.size <= 20 && result.attempts.map { it.position }.distinct().size == result.attempts.size && result.attempts.all { it.score in 0..it.total && it.total > 0 } })
    require(s.focus.remainingMs in 0..1_500_000 && (s.focus.deadline == null || s.focus.deadline >= 0))
    val ids = bank.map { it.id }.toSet()
    s.study?.let {
        require(it.level in 0..4 && it.index in 0..it.questionIds.size && it.questionIds.isNotEmpty() && it.questionIds.distinct().size == it.questionIds.size && it.reviewed.all { id -> id in it.questionIds })
    }
    backup.activeMock?.let {
        require(it.cards.isNotEmpty() && it.cards.size <= 5 && it.cards.size == it.questions.size && it.index in it.cards.indices && it.stage in 0..2)
        require(it.attempts.map { a -> a.position }.distinct().size == it.attempts.size)
    }
    val normalized = s.copy(
        cards = s.cards.map { it.copy(questionId = it.questionId?.takeIf(ids::contains)) },
        bookmarks = s.bookmarks.filter(ids::contains).toSet(),
        study = s.study?.takeIf { it.questionIds.all(ids::contains) },
        // Timer state is portable; a running countdown becomes paused on another device.
        focus = s.focus.copy(remainingMs = s.focus.remaining(), deadline = null), sync = SyncMeta(),
    )
    val mock = backup.activeMock?.let { run -> run.copy(
        cards = run.cards.map { it.copy(questionId = it.questionId?.takeIf(ids::contains)) },
        questions = run.questions.map { it?.takeIf { q -> q.id in ids } },
    ) }
    return backup.copy(state = normalized, activeMock = mock)
}
