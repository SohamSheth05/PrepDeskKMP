package com.prepdesk.ui.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Question IDs in [confident], [inDeck] and [bookmarks] describe the learner's progress on [questions]. */
data class PrepareUiState(
    val questions: List<Q> = emptyList(), val areas: List<String> = emptyList(),
    val sources: List<Source> = emptyList(), val resources: List<StudyResource> = emptyList(),
    val session: StudySession? = null, val currentQuestion: Q? = null,
    val confident: Set<String> = emptySet(), val inDeck: Set<String> = emptySet(), val bookmarks: Set<String> = emptySet(),
    val dueByArea: Map<String, Int> = emptyMap(), val plan: StudyPlan = StudyPlan(), val streak: Int = 0,
    val saving: Boolean = false, val mock: MockRun? = null, val studying: Boolean = false,
)

private fun prepareUiState(snap: Snapshot, mock: MockRun?, studying: Boolean): PrepareUiState {
    val s = snap.state; val questions = snap.library.questions
    return PrepareUiState(
        questions, questionAreas(questions), snap.library.sources, snap.library.resources,
        s.study, s.study?.currentId?.let { id -> questions.firstOrNull { it.id == id } },
        s.confidentQuestionIds(), s.deckQuestionIds(questions), s.bookmarks,
        s.dueCards(null).groupingBy { it.topic }.eachCount(), s.plan, s.streak(),
        snap.saving, mock, studying,
    )
}

class PrepareViewModel(
    private val progress: ProgressSource, private val study: StudyRepository, private val deck: DeckRepository, private val mocks: MockRepository,
    private val tutor: TutorRepository, status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    private val studying = MutableStateFlow(false)

    val uiState: StateFlow<PrepareUiState> = combine(snapshotOf(progress, status), mocks.mock, studying, ::prepareUiState)
        .let { stateOf(it, prepareUiState(currentSnapshot(progress, status), mocks.mock.value, studying.value)) }

    init {
        // A drill started on Today or Plan has already saved its quest; open it here.
        viewModelScope.launch {
            navigator.openQuest.collect { if (it) { studying.value = true; navigator.questOpened() } }
        }
    }

    /** Saves a new quest and opens it only once the save succeeded. */
    fun startQuest(area: String, level: Int, topic: String, ids: List<String>? = null) {
        viewModelScope.launch { if (study.startStudy(area, level, topic, ids)?.index == 0) studying.value = true }
    }
    /** Starts a quest of up to [size] questions from [pool], favouring those not yet reviewed confidently. */
    fun startQuest(area: String, level: Int, topic: String, pool: List<Q>, size: Int) =
        startQuest(area, level, topic, questQuestions(pool, progress.state.value, size).map { it.id })
    fun continueQuest() { studying.value = true }
    fun closeQuest() { studying.value = false }

    fun bookmark(id: String) = deck.toggleBookmark(id)
    fun reveal(q: Q) = study.reveal(q)
    fun review(q: Q, confident: Boolean) = study.review(q, confident)
    fun skip(q: Q) = study.skip(q)
    fun usePlanTrack(area: String, level: Int) = study.updatePlan { it.copy(area = area, lv = if (level == 0) it.lv else level) }
    fun mock(area: String, level: Int) {
        if (mocks.mock.value == null) mocks.start(area, level)
        navigator.go(AppScreen.Mock)
    }
    fun practice(area: String) = navigator.practice(area)
    fun askAbout(q: Q, action: TutorAction) { tutor.askAbout(q, action); navigator.go(AppScreen.Ask) }
}
