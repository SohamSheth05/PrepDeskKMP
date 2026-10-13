package com.prepdesk.domain

import kotlinx.coroutines.flow.StateFlow

/** One-tap requests about a library question. [mode] is the server's name for the request. */
enum class TutorAction(val label: String, val mode: String) {
    Explain("Explain simpler", "explain"),
    Example("Real example", "example"),
    Code("Show code", "code"),
    FollowUps("Follow-ups", "followups"),
}

enum class TutorSource { User, AI, Library }

/**
 * A message in the Ask conversation. Library replies are composed on the device from saved questions,
 * so Ask still helps offline or before AI is configured. [related] holds question IDs to open or save.
 */
data class TutorMessage(val id: Long, val source: TutorSource, val text: String, val related: List<String> = emptyList())

/** The Ask PrepDesk conversation: AI answers grounded in the question bank, with an on-device fallback. */
interface TutorRepository {
    val messages: StateFlow<List<TutorMessage>>
    /** True while an answer is being prepared. */
    val busy: StateFlow<Boolean>
    /** True when the signed-in account can use AI answers; otherwise replies come from the library. */
    val aiAvailable: StateFlow<Boolean>
    fun ask(text: String)
    fun askAbout(q: Q, action: TutorAction)
    fun clear()
}

private val STOP_WORDS = setOf(
    "the", "and", "for", "are", "how", "what", "why", "when", "which", "with", "does", "can", "you", "your", "this", "that",
    "from", "into", "should", "would", "use", "using", "between", "difference", "explain", "about", "work", "works", "have", "its",
)

private fun terms(text: String) = text.lowercase().split(Regex("[^a-z0-9#+]+")).filter { it.length >= 3 && it !in STOP_WORDS }.toSet()

/**
 * Library questions most related to [query], best first. Prompts and topics weigh more than answers,
 * so "StateFlow vs SharedFlow" finds the Flow questions rather than any answer that mentions a flow.
 */
fun relatedQuestions(bank: List<Q>, query: String, limit: Int = 5): List<Q> {
    val wanted = terms(query)
    if (wanted.isEmpty()) return emptyList()
    return bank.asSequence().map { q ->
        val prompt = terms(q.q); val topic = terms("${q.area} ${q.subtopic}"); val body = terms("${q.a} ${q.explanation}")
        q to wanted.sumOf { t -> (if (t in prompt) 3 else 0) + (if (t in topic) 2 else 0) + (if (t in body) 1 else 0) }
    }.filter { it.second >= 2 }.sortedByDescending { it.second }.take(limit).map { it.first }.toList()
}

/** A reply built from saved content, used offline, signed out, or when the AI service is unavailable. */
fun libraryAnswer(bank: List<Q>, query: String): TutorMessage {
    val matches = relatedQuestions(bank, query)
    val best = matches.firstOrNull()
        ?: return TutorMessage(0, TutorSource.Library, "I couldn't find that in your library. Try naming a technology or concept, such as \"Kotlin coroutines\" or \"Room migrations\".")
    val detail = best.explanation.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
    return TutorMessage(0, TutorSource.Library, "The closest question in your library is \"${best.q}\"\n\n${best.a}$detail", matches.map { it.id })
}

/** What one-tap [action] can show for [q] from saved content alone. */
fun libraryAnswer(q: Q, action: TutorAction): TutorMessage {
    val text = when (action) {
        TutorAction.Explain -> q.explanation.ifBlank { q.a }.let { "In short: ${q.a}" + if (it != q.a) "\n\n$it" else "" }
        TutorAction.Example -> q.explanation.takeIf { it.isNotBlank() }?.let { "From the library's explanation:\n\n$it" }
            ?: "Your library has no worked example for this question yet. Sign in to ask AI for one."
        TutorAction.Code -> q.code.takeIf { it.isNotBlank() }?.let { "```\n$it\n```" }
            ?: "Your library has no code sample for this question. Sign in to ask AI for one."
        TutorAction.FollowUps -> q.followups.takeIf { it.isNotEmpty() }?.mapIndexed { i, f -> "${i + 1}. $f" }?.joinToString("\n")
            ?.let { "Interviewers often follow up with:\n\n$it" } ?: "No follow-up questions are saved for this question yet."
    }
    return TutorMessage(0, TutorSource.Library, text, listOf(q.id))
}

/** The user-visible request for a one-tap action, shown in the conversation. */
fun TutorAction.request(q: Q): String = "$label: ${q.q}"
