package com.prepdesk.domain
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class Card(val id: Long, val topic: String, val q: String, val a: String, val box: Int = 0, val due: Long = 0, val questionId: String? = null)
@Serializable data class AppState(val cards: List<Card> = seed(), val days: Map<String, Int> = emptyMap(), val pomos: Int = 0, val plan: StudyPlan = StudyPlan(), val bookmarks: Set<String> = emptySet(), val history: List<MockResult> = emptyList(), val study: StudySession? = null, val focus: FocusSession = FocusSession(), val sync: SyncMeta = SyncMeta(), val arcade: Arcade = Arcade())
// interviewDate is an ISO date (yyyy-MM-dd); target is a free-form company or role label.
@Serializable data class StudyPlan(val lv: Int = 2, val area: String = "All areas", val days: Int = 14, val interviewDate: String? = null, val target: String = "")

val GAPS = listOf(0, 1, 3, 7, 14)
const val DAY_MS = 86_400_000L
fun nowMs() = Clock.System.now().toEpochMilliseconds()
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun AppState.streak(): Int {
    var d = today(); if (days[d.toString()] == null) d = d.minus(1, DateTimeUnit.DAY)
    var n = 0; while (days[d.toString()] != null) { n++; d = d.minus(1, DateTimeUnit.DAY) }
    return n
}
fun AppState.inDeck(q: Q) = cards.any { it.questionId == q.id || (it.q == q.q && it.a == q.a) }
/** IDs of [questions] that [inDeck] would match, in one pass over the cards. */
fun AppState.deckQuestionIds(questions: List<Q>): Set<String> {
    val linked = cards.mapNotNull { it.questionId }.toSet()
    val custom = cards.map { it.q to it.a }.toSet()
    return questions.filter { it.id in linked || (it.q to it.a) in custom }.map { it.id }.toSet()
}
/** Adds a learner-written card at the top of the deck. */
fun AppState.addCard(topic: String, prompt: String, answer: String) =
    copy(cards = listOf(Card(maxOf(nowMs(), (cards.maxOfOrNull { it.id } ?: 0) + 1), topic.trim().ifBlank { "General" }, prompt.trim(), answer.trim())) + cards)
fun AppState.toggleBookmark(id: String) = copy(bookmarks = if (id in bookmarks) bookmarks - id else bookmarks + id)
fun AppState.dueCards(topic: String?) = cards.filter { it.due <= nowMs() && (topic == null || it.topic == topic) }
fun AppState.logged() = copy(days = days + (today().toString() to ((days[today().toString()] ?: 0) + 1)))
fun AppState.rate(id: Long, ok: Boolean) = logged().let { s ->
    s.copy(cards = s.cards.map {
        if (it.id != id) it else { val b = if (ok) minOf(it.box + 1, 4) else 0; it.copy(box = b, due = nowMs() + GAPS[b] * DAY_MS) }
    })
}

fun seed() = listOf(
    Triple("Java", "== vs equals()?", "== compares references/primitives; equals() compares logical content when overridden. Override hashCode() with equals()."),
    Triple("Java", "How does HashMap work?", "hashCode picks a bucket; collisions chain, treeify at 8+. Resize at 0.75 load. Not thread-safe; use ConcurrentHashMap."),
    Triple("Java", "Checked vs unchecked exceptions?", "Checked must be caught or declared (IOException). Unchecked extend RuntimeException and signal programming errors."),
    Triple("Spring", "What is dependency injection?", "The container supplies dependencies instead of the object creating them. Prefer constructor injection for testability."),
    Triple("Spring", "How do you secure a REST API?", "Spring Security filter chain, stateless JWT, BCrypt passwords, role checks, HTTPS and CORS."),
    Triple("Kotlin", "val vs var, and what is null safety?", "val is read-only, var is mutable. Types are non-null by default; use ? , ?. and ?: to handle nulls explicitly."),
    Triple("Kotlin", "What are coroutines?", "Lightweight, structured concurrency. suspend functions pause without blocking threads; launch/async run inside a scope."),
    Triple("SQL", "INNER vs LEFT JOIN?", "INNER keeps matching rows only; LEFT keeps all left rows with NULLs where unmatched."),
    Triple("DSA", "Binary search complexity?", "O(log n) on sorted data by halving the range each step."),
    Triple("Behavioral", "Tell me about yourself.", "Present, past, future in under 90 seconds: current work, relevant experience, why this role."),
    Triple("Behavioral", "Describe a failure and what you learned.", "STAR: Situation, Task, Action, Result. Own your part and show what you changed."),
).mapIndexed { i, t -> Card(i + 1L, t.first, t.second, t.third) }
