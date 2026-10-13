package com.prepdesk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.*
import com.prepdesk.ui.components.Celebration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppUiState(
    val ready: Boolean = false, val error: String? = null,
    val level: Int = 1, val due: Int = 0, val streak: Int = 0,
    val celebration: Celebration? = null,
)

/** App shell: startup, header, error banner, focus-timer completion, celebrations, and navigation. */
class AppViewModel(
    private val progress: ProgressSource,
    private val focus: FocusRepository,
    private val rewards: RewardsRepository,
    mocks: MockRepository,
    account: AccountRepository,
    private val status: AppStatus,
    private val navigator: Navigator,
) : ViewModel() {
    val screen: StateFlow<AppScreen> = navigator.screen

    val uiState: StateFlow<AppUiState> = combine(progress.ready, status.error, snapshotOf(progress, status), mocks.mock) { ready, error, snap, mock ->
        val s = snap.state
        AppUiState(ready, error, playerLevel(s.xp()).level, s.dueCards(null).size, s.streak(),
            if (ready && !snap.saving) celebrationFor(s, snap.library.questions, mock) else null)
    }.flowOn(Dispatchers.Default).let { stateOf(it, AppUiState()) }

    init {
        if (!progress.ready.value) progress.load()
        account.restoreSession()
        // Count a focus session as soon as its deadline passes, even on another screen.
        viewModelScope.launch {
            combine(progress.ready, progress.state.map { it.focus.deadline }) { ready, deadline -> if (ready) deadline else null }
                .distinctUntilChanged()
                .collectLatest { deadline -> if (deadline != null) { delay((deadline - nowMs()).coerceAtLeast(0)); focus.finishIfDue() } }
        }
        // Record a completed set of daily challenges once per day.
        viewModelScope.launch {
            combine(progress.ready, progress.state, status.pending) { ready, s, pending -> Triple(ready, s, pending) }.collect { (ready, s, pending) ->
                val day = today().toString()
                if (ready && pending == 0 && day !in s.arcade.sweeps && dailyChallenges(s).all { it.done })
                    rewards.recordSweep(day)
            }
        }
    }

    private var badgeCache: Pair<List<Q>, List<Badge>>? = null
    private fun celebrationFor(s: AppState, questions: List<Q>, mock: MockRun?): Celebration? {
        // Never interrupt a running mock answer timer; an abandoned round must not block celebrations.
        if (mock != null && mock.stage < 2 && mock.deadline > nowMs()) return null
        val all = badgeCache?.takeIf { it.first === questions }?.second ?: badges(questions).also { badgeCache = questions to it }
        val fresh = newBadges(s, all, readiness(s, questions).overall)
        val level = playerLevel(s.xp())
        val levelUp = level.level > s.arcade.seenLevel
        if (fresh.isEmpty() && !levelUp) return null
        return Celebration(welcome = s.arcade.seenLevel == 0 && s.arcade.earned.isEmpty(), levelUp = levelUp, level = level, badges = fresh)
    }

    fun dismissCelebration(c: Celebration) = rewards.acknowledge(c.badges.map { it.id }, c.level.level)

    fun select(screen: AppScreen) = navigator.go(screen)
    fun back() = navigator.back()
    fun retry() = progress.load()
    fun dismissError() = status.dismissError()
}
