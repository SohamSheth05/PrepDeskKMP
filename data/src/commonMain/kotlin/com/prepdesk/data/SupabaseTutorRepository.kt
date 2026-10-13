package com.prepdesk.data

import com.prepdesk.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * [TutorRepository] that asks the PrepDesk Edge Function, sending the IDs of related library questions so
 * the server can ground its answer in trusted content. Falls back to [libraryAnswer] when AI is unavailable.
 * Requests run on the application scope, not the save queue, so a slow answer never delays saving progress.
 */
class SupabaseTutorRepository(
    private val api: SupabaseApi,
    private val account: SupabaseAccountRepository,
    private val progress: ProgressStore,
    private val scope: CoroutineScope,
) : TutorRepository {
    private val _messages = MutableStateFlow<List<TutorMessage>>(emptyList())
    override val messages: StateFlow<List<TutorMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    override val busy: StateFlow<Boolean> = _busy.asStateFlow()
    override val aiAvailable: StateFlow<Boolean> = combine(account.session, account.capabilities) { session, caps -> session != null && caps.tutor }
        .stateIn(scope, SharingStarted.Eagerly, false)
    private var nextId = 1L

    @Serializable private data class Reply(val answer: String, val relatedIds: List<String> = emptyList())

    override fun ask(text: String) {
        val question = text.trim().take(MAX_QUESTION)
        if (question.isBlank() || _busy.value) return
        val bank = progress.library.value.questions
        respond(question, offline = { libraryAnswer(bank, question) }) { history ->
            put("mode", "ask"); put("question", question)
            putJsonArray("contextIds") { relatedQuestions(bank, question).forEach { add(it.id) } }
            putJsonArray("history") { history.forEach { m -> addJsonObject { put("role", if (m.source == TutorSource.User) "user" else "assistant"); put("text", m.text.take(MAX_TURN)) } } }
        }
    }

    override fun askAbout(q: Q, action: TutorAction) {
        if (_busy.value) return
        respond(action.request(q), offline = { libraryAnswer(q, action) }) { _ ->
            put("mode", action.mode); put("questionId", q.id)
            putJsonArray("contextIds") { add(q.id) }
        }
    }

    override fun clear() { if (!_busy.value) _messages.value = emptyList() }

    private fun respond(request: String, offline: () -> TutorMessage, input: kotlinx.serialization.json.JsonObjectBuilder.(List<TutorMessage>) -> Unit) {
        // Earlier AI turns give follow-up questions their context; library replies are not sent.
        val history = _messages.value.filter { it.source != TutorSource.Library }.takeLast(MAX_HISTORY)
        append(TutorMessage(0, TutorSource.User, request))
        _busy.value = true
        scope.launch {
            val reply = try {
                if (!aiAvailable.value) offline()
                else {
                    val body = buildJsonObject { input(history) }
                    val result = CONTENT_JSON.decodeFromString<Reply>(api.function(account.token(), "ask", body))
                    TutorMessage(0, TutorSource.AI, result.answer.trim(), result.relatedIds.filter { progress.question(it) != null })
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val fallback = offline()
                fallback.copy(text = "AI answers are unavailable right now (${e.message ?: "network error"}). From your library:\n\n${fallback.text}")
            }
            append(reply); _busy.value = false
        }
    }

    private fun append(message: TutorMessage) = _messages.update { (it + message.copy(id = nextId++)).takeLast(MAX_MESSAGES) }

    private companion object {
        const val MAX_QUESTION = 1_000
        const val MAX_TURN = 3_000
        const val MAX_HISTORY = 6
        const val MAX_MESSAGES = 100
    }
}
