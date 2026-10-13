package com.prepdesk.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodayUiState(
    val player: PlayerSummary = playerSummary(AppState(cards = emptyList()), emptyList()),
    val plan: StudyPlan = StudyPlan(), val daily: DailyPlan = DailyPlan(PrepPhase.Open, null, 0, false),
    val fresh: List<Q> = emptyList(), val due: Int = 0, val actionsToday: Int = 0, val weakPrompts: List<String> = emptyList(),
    val readiness: Readiness = Readiness(0f, 0, 0, emptyList(), emptyMap()), val challenges: List<Challenge> = emptyList(),
    val mockActive: Boolean = false,
)

class TodayViewModel(
    progress: ProgressSource, private val deck: DeckRepository, private val study: StudyRepository, mocks: MockRepository,
    status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> = combine(snapshotOf(progress, status), mocks.mock) { snap, mock ->
        val s = snap.state; val questions = snap.library.questions
        val daily = dailyPlan(s, questions)
        TodayUiState(
            player = playerSummary(s, badges(questions)), plan = s.plan, daily = daily,
            fresh = planPool(questions, s.plan).filterNot { s.inDeck(it) }.take(daily.newCount),
            due = s.dueCards(null).size, actionsToday = s.days[today().toString()] ?: 0,
            weakPrompts = s.history.flatMap { it.attempts }.filter { it.weak }.take(5).map { it.prompt }.distinct(),
            readiness = readiness(s, questions), challenges = dailyChallenges(s), mockActive = mock != null,
        )
    }.flowOn(Dispatchers.Default).let { stateOf(it, TodayUiState()) }

    fun addAndPractise(fresh: List<Q>) { deck.addQuestions(fresh); navigator.go(AppScreen.Practice) }
    fun drill(topic: TopicReadiness) = launchDrill(study, navigator, topic, uiState.value.readiness.scores)
    fun go(screen: AppScreen) = navigator.go(screen)
    fun go(challenge: Challenge) = navigator.go(challenge.target.screen())
}

/** Starts a short quest on a topic's least-mastered questions, then opens it on Prepare. */
fun ViewModel.launchDrill(study: StudyRepository, navigator: Navigator, topic: TopicReadiness, scores: Map<String, Float>) {
    viewModelScope.launch {
        if (study.startStudy(topic.area, 0, topic.subtopic, drillQuestions(topic, scores).map { it.id }) != null) navigator.openQuest()
    }
}
