package com.prepdesk.ui.practice

import androidx.lifecycle.ViewModel
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.flow.*

/** [due] is every due card, oldest first; the screen narrows it to the chosen topic. */
data class PracticeUiState(
    val area: String? = null, val topics: List<String> = emptyList(), val due: List<Card> = emptyList(), val sources: List<Source> = emptyList(),
)

private fun practiceUiState(snap: Snapshot, area: String?) = PracticeUiState(
    area, snap.state.cards.map { it.topic }.distinct(),
    snap.state.dueCards(null).sortedWith(compareBy<Card> { it.due }.thenBy { it.id }), snap.library.sources,
)

/** Spaced-repetition review of due cards. */
class PracticeViewModel(
    private val progress: ProgressSource, private val deck: DeckRepository, private val tutor: TutorRepository, status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    val uiState: StateFlow<PracticeUiState> = combine(snapshotOf(progress, status), navigator.practiceArea, ::practiceUiState)
        .let { stateOf(it, practiceUiState(currentSnapshot(progress, status), navigator.practiceArea.value)) }

    fun rate(card: Card, recalled: Boolean) = deck.rate(card, recalled)
    fun question(card: Card): Q? = progress.question(card.questionId)
    fun askAbout(q: Q, action: TutorAction) { tutor.askAbout(q, action); navigator.go(AppScreen.Ask) }
}
