package com.prepdesk.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.prepdesk.domain.ChallengeTarget
import com.prepdesk.ui.components.PrepIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Every destination. The first five are bottom-bar tabs; the rest open from More with a back arrow. */
enum class AppScreen(val label: String, val icon: ImageVector, val description: String = "") {
    Today("Today", PrepIcons.Home),
    Prepare("Learn", PrepIcons.Learn),
    Ask("Ask", PrepIcons.Ask),
    Practice("Review", PrepIcons.Review),
    More("More", PrepIcons.More),
    Mock("Mock interview", PrepIcons.Mic, "Timed answers, follow-ups, and a checklist"),
    Code("Practical", PrepIcons.Code, "Write, debug, and run code exercises"),
    Play("Answer Rush", PrepIcons.Bolt, "A 60-second quiz game with streaks and badges"),
    Focus("Focus timer", PrepIcons.Timer, "Pomodoro sessions for deep study"),
    Plan("Plan", PrepIcons.Calendar, "Interview date, track, and daily targets"),
    Library("Library", PrepIcons.Book, "Search and filter every question"),
    Cards("My cards", PrepIcons.Cards, "Revision cards you wrote yourself"),
    History("Mock history", PrepIcons.History, "Saved answers and feedback"),
    Account("Account and data", PrepIcons.Person, "Sign-in, sync, backups, and privacy");

    val isTab: Boolean get() = ordinal <= More.ordinal

    companion object {
        val tabs = entries.filter { it.isTab }
        /** How More groups its destinations. */
        val moreSections = listOf(
            "Practise" to listOf(Mock, Code, Play, Focus),
            "Organise" to listOf(Plan, Library, Cards, History),
            "Settings" to listOf(Account),
        )
    }
}

fun ChallengeTarget.screen(): AppScreen = when (this) {
    ChallengeTarget.Study -> AppScreen.Prepare
    ChallengeTarget.Game -> AppScreen.Play
    ChallengeTarget.Mock -> AppScreen.Mock
    ChallengeTarget.Review -> AppScreen.Practice
}

/**
 * App-wide navigation state shared by ViewModels, so one screen can send the learner to another
 * (for example, a drill started on Today opens the quest on Prepare).
 */
class Navigator {
    private val _screen = MutableStateFlow(AppScreen.Today)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()
    private val _practiceArea = MutableStateFlow<String?>(null)
    /** Track filter for the Practice screen; null shows every due card. */
    val practiceArea: StateFlow<String?> = _practiceArea.asStateFlow()
    private val _openQuest = MutableStateFlow(false)
    /** Set when Prepare should open the saved study quest immediately. */
    val openQuest: StateFlow<Boolean> = _openQuest.asStateFlow()

    fun go(screen: AppScreen) {
        if (screen == AppScreen.Practice) _practiceArea.value = null
        _screen.value = screen
    }
    fun practice(area: String?) { _practiceArea.value = area; _screen.value = AppScreen.Practice }
    fun openQuest() { _openQuest.value = true; _screen.value = AppScreen.Prepare }
    fun questOpened() { _openQuest.value = false }

    /** Whether [back] has somewhere to go; on Today the platform handles back (for example, leaving the app). */
    val canGoBack: Boolean get() = _screen.value != AppScreen.Today
    /** Screens opened from More return there; other tabs return to Today. */
    fun back() { _screen.value = if (_screen.value.isTab) AppScreen.Today else AppScreen.More }
}
