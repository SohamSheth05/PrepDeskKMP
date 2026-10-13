package com.prepdesk.ui.ask

import androidx.lifecycle.ViewModel
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.flow.*

data class AskUiState(
    val messages: List<TutorMessage> = emptyList(), val busy: Boolean = false, val aiAvailable: Boolean = false,
    /** Related questions referenced by the conversation, by ID. */
    val related: Map<String, Q> = emptyMap(), val inDeck: Set<String> = emptySet(), val suggestions: List<String> = emptyList(),
)

/** Starter prompts tailored to the plan's track where PrepDesk has a good set; otherwise general ones. */
fun suggestionsFor(area: String): List<String> = when (area) {
    "Android" -> listOf("How do I explain process death vs rotation?", "When should I use WorkManager?", "StateFlow vs SharedFlow?", "How do I make a Compose list fast?")
    "Kotlin" -> listOf("launch vs async: how do failures differ?", "What does structured concurrency mean?", "When is a sealed class better than an enum?", "How do inline functions work?")
    else -> listOf("How do I structure a system design answer?", "How should I answer \"tell me about yourself\"?", "Explain caching trade-offs simply", "How do I talk about a project I led?")
}

class AskViewModel(
    private val tutor: TutorRepository, private val progress: ProgressSource, private val deck: DeckRepository,
) : ViewModel() {
    val uiState: StateFlow<AskUiState> = combine(tutor.messages, tutor.busy, tutor.aiAvailable, progress.state, progress.library) { messages, busy, ai, state, library ->
        val ids = messages.flatMap { it.related }.toSet()
        val related = library.questions.filter { it.id in ids }
        AskUiState(messages, busy, ai, related.associateBy { it.id }, state.deckQuestionIds(related), suggestionsFor(state.plan.area))
    }.let { stateOf(it, AskUiState(tutor.messages.value, tutor.busy.value, tutor.aiAvailable.value, suggestions = suggestionsFor(progress.state.value.plan.area))) }

    fun send(text: String) = tutor.ask(text)
    fun askAbout(q: Q, action: TutorAction) = tutor.askAbout(q, action)
    fun addToDeck(q: Q) = deck.addQuestions(listOf(q))
    fun clear() = tutor.clear()
}
