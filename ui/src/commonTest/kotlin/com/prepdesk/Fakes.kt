package com.prepdesk

import com.prepdesk.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A small question bank, so UI tests do not depend on the :data module's content. */
val TEST_QUESTIONS = listOf(
    Q("android-1", "Android", 2, "Why does a ViewModel survive rotation?", "It is retained across configuration changes.", subtopic = "Lifecycle",
        explanation = "The ViewModelStore outlives the Activity instance.", rubric = listOf("Configuration change", "ViewModelStore", "Not process death")),
    Q("android-2", "Android", 1, "What is state hoisting?", "Moving state to the caller.", subtopic = "Compose"),
    Q("kotlin-1", "Kotlin", 1, "Does val make an object immutable?", "No, only the reference.", subtopic = "Variables"),
)

/**
 * In-memory progress for ViewModel tests: applies the same domain changes as the local repositories,
 * but immediately and without a database, so tests observe each change synchronously.
 */
class FakeProgress(
    state: AppState = AppState(cards = emptyList()),
    questions: List<Q> = TEST_QUESTIONS,
    ready: Boolean = true,
) : ProgressSource, DeckRepository, StudyRepository, FocusRepository, RewardsRepository, MockRepository {
    override val ready = MutableStateFlow(ready)
    override val state = MutableStateFlow(state)
    override val library = MutableStateFlow(Library(questions))
    override val mock = MutableStateFlow<MockRun?>(null)
    var loads = 0; private set

    override fun question(id: String?): Q? = library.value.questions.firstOrNull { it.id == id }
    override fun load() { loads++; ready.value = true }
    private fun change(f: (AppState) -> AppState) = state.update(f)

    override fun addQuestions(questions: List<Q>) = change { it.addQuestions(questions) }
    override fun rate(card: Card, recalled: Boolean) = change { it.rate(card.id, recalled) }
    override fun addCard(topic: String, prompt: String, answer: String) = change { it.addCard(topic, prompt, answer) }
    override fun deleteCard(card: Card) = change { it.copy(cards = it.cards - card) }
    override fun toggleBookmark(questionId: String) = change { it.toggleBookmark(questionId) }
    override fun assessPractical(q: Q, solved: Boolean) = change { it.assessPractical(q, solved) }

    override suspend fun startStudy(area: String, level: Int, subtopic: String, selectedIds: List<String>?): StudySession? =
        studySessionFor(library.value.questions, area, level, subtopic, selectedIds)?.also { study -> change { it.copy(study = study) } }
    override fun reveal(q: Q) = change { it.revealStudy(q) }
    override fun review(q: Q, confident: Boolean) = change { it.reviewStudy(q, confident) }
    override fun skip(q: Q) = change { it.skipStudy(q) }
    override fun updatePlan(change: (StudyPlan) -> StudyPlan) = state.update { it.copy(plan = change(it.plan)) }

    override fun toggle() = change { it.toggleFocus(nowMs()) }
    override fun reset() = change { it.copy(focus = FocusSession()) }
    override fun finishIfDue() = change { it.finishFocus(nowMs()) }

    override fun recordRush(area: String, score: RushScore) = change { it.recordRush(area, score) }
    override fun recordSweep(day: String) = change { it.recordSweep(day) }
    override fun acknowledge(badgeIds: List<String>, level: Int) = change { it.acknowledgeRewards(badgeIds, level) }

    // Mock rounds are covered by RepositoryTest against the real store; the fake only tracks the round.
    override fun start(area: String, level: Int) {}
    override fun setDraft(value: String) { mock.update { it?.copy(draft = value) } }
    override fun setFollowups(value: String) { mock.update { it?.copy(followupNotes = value) } }
    override fun applyTranscript(run: MockRun, text: String) { mock.update { it?.withTranscript(run, text) } }
    override fun setChecked(index: Int, checked: Boolean) { mock.update { it?.copy(checked = if (checked) it.checked + index else it.checked - index) } }
    override fun advance(run: MockRun) {}
    override fun assess(position: Int, solid: Boolean?) {}
    override fun cancel() { mock.value = null }
}

class FakeStatus : AppStatus {
    override val pending = MutableStateFlow(0)
    override val error = MutableStateFlow<String?>(null)
    override val message = MutableStateFlow<String?>(null)
    override fun reportError(message: String) { error.value = message }
    override fun dismissError() { error.value = null }
}

class FakeAccount : AccountRepository {
    override val session = MutableStateFlow<AuthSession?>(null)
    override val capabilities = MutableStateFlow(ServiceCapabilities())
    override val conflict = MutableStateFlow<CloudProgress?>(null)
    override val otpSent = MutableStateFlow(false)
    override val onlineConfigured = false
    var restored = 0; private set
    override fun restoreSession() { restored++ }
    override fun sendCode(email: String) { otpSent.value = true }
    override fun verifyCode(email: String, code: String) {}
    override fun signOut() { session.value = null }
    override fun refreshCapabilities() {}
    override fun synchronize() {}
    override fun resolveConflict(useCloud: Boolean) { conflict.value = null }
    override fun deleteAccount() {}
    override fun clearDevice() {}
    override fun updateContent() {}
}

/** Answers from the library, as the real repository does offline, but synchronously. */
class FakeTutor(private val bank: List<Q> = TEST_QUESTIONS) : TutorRepository {
    override val messages = MutableStateFlow<List<TutorMessage>>(emptyList())
    override val busy = MutableStateFlow(false)
    override val aiAvailable = MutableStateFlow(false)
    private fun add(m: TutorMessage) = messages.update { it + m.copy(id = it.size + 1L) }
    override fun ask(text: String) { add(TutorMessage(0, TutorSource.User, text)); add(libraryAnswer(bank, text)) }
    override fun askAbout(q: Q, action: TutorAction) { add(TutorMessage(0, TutorSource.User, action.request(q))); add(libraryAnswer(q, action)) }
    override fun clear() { messages.value = emptyList() }
}

class FakeTools : ToolsRepository {
    override val codeResult: StateFlow<CodeResult?> = MutableStateFlow(null)
    override val languages: StateFlow<List<CodeLanguage>> = MutableStateFlow(emptyList())
    override val practicalRun: StateFlow<String?> = MutableStateFlow(null)
    override fun requestFeedback() {}
    override fun transcribe(audio: ByteArray, apply: (String) -> Unit) {}
    override fun loadLanguages() {}
    override fun runCode(language: Int, code: String, stdin: String) {}
    override fun runPractical(q: Q, code: String) {}
    override fun pollCode() {}
}
