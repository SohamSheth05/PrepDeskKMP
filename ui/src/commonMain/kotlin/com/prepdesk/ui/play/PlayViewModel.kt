package com.prepdesk.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayUiState(
    val player: PlayerSummary = playerSummary(AppState(cards = emptyList()), emptyList()),
    val challenges: List<Challenge> = emptyList(), val areas: List<String> = emptyList(), val defaultArea: String = ALL_TRACKS,
    val bests: Map<String, Int> = emptyMap(), val badges: List<Pair<Badge, Boolean>> = emptyList(),
)

/** A running or finished Answer Rush game. */
data class RushGame(
    val area: String, val questions: List<RushQuestion>, val previousBest: Int,
    val index: Int = 0, val score: RushScore = RushScore(), val deadline: Long = nowMs() + RUSH_SECONDS * 1000L,
    val now: Long = nowMs(), val picked: Int? = null, val finished: Boolean = false, val missedAdded: Boolean = false,
) {
    val current: RushQuestion get() = questions[minOf(index, questions.lastIndex)]
    val secondsLeft: Double get() = (deadline - now).coerceAtLeast(0) / 1000.0
    val newBest: Boolean get() = score.score > previousBest && score.score > 0
    val accuracy: Int get() = if (score.answered == 0) 0 else score.correct * 100 / score.answered
}

class PlayViewModel(
    private val progress: ProgressSource, private val deck: DeckRepository, private val rewards: RewardsRepository, status: AppStatus, private val navigator: Navigator,
) : ViewModel() {
    val uiState: StateFlow<PlayUiState> = snapshotOf(progress, status).map { snap ->
        val s = snap.state; val questions = snap.library.questions
        val all = badges(questions)
        val areas = questionAreas(questions)
        PlayUiState(playerSummary(s, all), dailyChallenges(s), areas, s.plan.area.takeIf { it in areas } ?: ALL_TRACKS,
            s.arcade.bestRush, all.map { it to (it.id in s.arcade.earned) })
    }.flowOn(Dispatchers.Default).let { stateOf(it, PlayUiState()) }

    private val _game = MutableStateFlow<RushGame?>(null)
    /** Null when no game is open; also covers the empty state when a track has too few questions. */
    val game: StateFlow<RushGame?> = _game.asStateFlow()
    private var ticker: Job? = null

    fun start(area: String) {
        ticker?.cancel()
        viewModelScope.launch {
            val questions = withContext(Dispatchers.Default) { buildRush(progress.library.value.questions, area) }
            _game.value = RushGame(area, questions, progress.state.value.arcade.bestRush[area] ?: 0, finished = questions.isEmpty())
            if (questions.isNotEmpty()) tick()
        }
    }

    private fun tick() {
        ticker = viewModelScope.launch {
            while (true) {
                val g = _game.value ?: return@launch
                if (g.finished) return@launch
                val now = nowMs()
                if (now >= g.deadline || g.index >= g.questions.size) { finish(g.copy(now = now)); return@launch }
                _game.value = g.copy(now = now)
                delay(100)
            }
        }
    }

    private fun finish(g: RushGame) {
        _game.value = g.copy(finished = true, picked = null)
        rewards.recordRush(g.area, g.score)
    }

    /** Scores a choice; returns whether it was right so the screen can give haptic feedback. */
    fun answer(choice: Int): Boolean {
        val g = _game.value ?: return false
        if (g.picked != null || g.finished) return false
        val right = choice == g.current.correct
        _game.value = g.copy(picked = choice, score = g.score.answer(g.current.q, right), deadline = if (right) g.deadline else g.deadline - RUSH_PENALTY_MS)
        viewModelScope.launch {
            delay(650)
            _game.update { it?.takeIf { !it.finished }?.copy(picked = null, index = it.index + 1) ?: it }
        }
        return right
    }

    fun addMissed() {
        val g = _game.value ?: return
        val missed = g.score.missed.distinct().mapNotNull(progress::question)
        deck.addQuestions(missed)
        _game.value = g.copy(missedAdded = true)
    }

    fun playAgain() { _game.value?.let { start(it.area) } }
    fun quit() { ticker?.cancel(); _game.value = null }
    fun go(challenge: Challenge) = navigator.go(challenge.target.screen())
}
