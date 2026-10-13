package com.prepdesk.ui.mock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** [questions] are the spoken questions a round can draw from; hands-on exercises live in the Practical tab. */
data class MockUiState(
    val run: MockRun? = null, val questions: List<Q> = emptyList(), val areas: List<String> = emptyList(), val defaultArea: String = "All areas",
    val deckSize: Int = 0, val lastResult: MockResult? = null, val sources: List<Source> = emptyList(), val saving: Boolean = false,
    val signedIn: Boolean = false, val capabilities: ServiceCapabilities = ServiceCapabilities(),
) {
    /** How many questions a round on [area] and [level] (0 for all levels) can draw from. */
    fun available(area: String, level: Int): Int =
        if (area == "My deck") deckSize else questions.count { (area == "All areas" || it.area == area) && (level == 0 || it.level == level) }
}

private fun mockUiState(snap: Snapshot, run: MockRun?, signedIn: Boolean, capabilities: ServiceCapabilities): MockUiState {
    val s = snap.state; val all = snap.library.questions
    val areas = questionAreas(all)
    return MockUiState(run, all.filter { it.practical == null }, areas, s.plan.area.takeIf { it in areas } ?: "All areas",
        s.cards.size, s.history.firstOrNull(), snap.library.sources, snap.saving, signedIn, capabilities)
}

class MockViewModel(
    progress: ProgressSource, private val mocks: MockRepository, account: AccountRepository, private val tools: ToolsRepository, private val status: AppStatus,
) : ViewModel() {
    val uiState: StateFlow<MockUiState> = combine(snapshotOf(progress, status), mocks.mock, account.session, account.capabilities) { snap, run, session, caps ->
        mockUiState(snap, run, session != null, caps)
    }.let { stateOf(it, mockUiState(currentSnapshot(progress, status), mocks.mock.value, account.session.value != null, account.capabilities.value)) }

    fun start(area: String, level: Int) = mocks.start(area, level)
    fun setDraft(value: String) = mocks.setDraft(value)
    fun setFollowups(value: String) = mocks.setFollowups(value)
    /** Applies a transcript only if the learner is still on the same question and stage. */
    fun applyTranscript(run: MockRun, text: String) = mocks.applyTranscript(run, text)
    fun advance(run: MockRun) = mocks.advance(run)
    fun check(index: Int, on: Boolean) = mocks.setChecked(index, on)
    fun assess(position: Int, solid: Boolean? = null) = mocks.assess(position, solid)
    fun cancel() = mocks.cancel()
    fun requestFeedback() = tools.requestFeedback()
    fun transcribe(audio: ByteArray, apply: (String) -> Unit) = tools.transcribe(audio) { text -> viewModelScope.launch { apply(text) } }
    fun reportError(message: String) = status.reportError(message)
}
