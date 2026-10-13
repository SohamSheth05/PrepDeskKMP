package com.prepdesk

import com.prepdesk.domain.*
import com.prepdesk.ui.AppViewModel
import com.prepdesk.ui.Navigator
import com.prepdesk.ui.ask.AskViewModel
import com.prepdesk.ui.AppScreen
import com.prepdesk.ui.cards.CardsViewModel
import com.prepdesk.ui.practice.PracticeViewModel
import com.prepdesk.ui.components.Celebration
import com.prepdesk.ui.mock.MockViewModel
import com.prepdesk.ui.prepare.PrepareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

/** ViewModels against in-memory fakes: no database, network, or Android runtime. */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private val main = UnconfinedTestDispatcher()
    private val status = FakeStatus()

    @BeforeTest fun setUp() = Dispatchers.setMain(main)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun cardsAreAddedAndDeletedThroughTheDeck() = runTest(main) {
        val progress = FakeProgress()
        val vm = CardsViewModel(progress, progress)
        backgroundScope.launch { vm.cards.collect {} }
        vm.add("  ", " What is a Flow? ", " A cold stream ")
        val card = vm.cards.value.single()
        assertEquals(Triple("General", "What is a Flow?", "A cold stream"), Triple(card.topic, card.q, card.a))
        vm.delete(card)
        assertTrue(vm.cards.value.isEmpty())
    }

    @Test fun questOpensRevealsAndSavesAConfidentRevisionCard() = runTest(main) {
        val progress = FakeProgress()
        val vm = PrepareViewModel(progress, progress, progress, progress, FakeTutor(), status, Navigator())
        backgroundScope.launch { vm.uiState.collect {} }
        val q = TEST_QUESTIONS.first { it.rubric.isNotEmpty() }
        vm.startQuest(q.area, 0, "All topics", listOf(q.id))
        assertTrue(vm.uiState.value.studying)
        assertEquals(q, vm.uiState.value.currentQuestion)
        vm.review(q, confident = true)
        assertFalse(q.id in vm.uiState.value.inDeck, "A question cannot be assessed before its answer is revealed")
        vm.reveal(q)
        assertTrue(vm.uiState.value.session!!.revealed)
        vm.review(q, confident = true)
        assertTrue(q.id in vm.uiState.value.inDeck && q.id in vm.uiState.value.confident)
        assertTrue(vm.uiState.value.session!!.complete)
    }

    @Test fun appShellCountsAnExpiredFocusSessionAndRemembersCelebrations() = runTest(main) {
        val progress = FakeProgress(AppState(cards = emptyList(), focus = FocusSession(deadline = nowMs() - 1)))
        val account = FakeAccount()
        val vm = AppViewModel(progress, progress, progress, progress, account, status, Navigator())
        assertEquals(1, progress.state.value.pomos)
        assertEquals(1, account.restored)
        assertEquals(0, progress.loads, "Already-loaded progress is not reloaded")
        val level = playerLevel(5_000)
        vm.dismissCelebration(Celebration(welcome = false, levelUp = true, level = level, badges = emptyList()))
        assertEquals(level.level, progress.state.value.arcade.seenLevel)
    }

    @Test fun lateTranscriptsDoNotOverwriteALaterQuestion() = runTest(main) {
        val progress = FakeProgress()
        val run = MockRun(1, 0, "Kotlin", listOf(Card(1, "Kotlin", "Q1", "A1"), Card(2, "Kotlin", "Q2", "A2")), listOf(null, null))
        progress.mock.value = run
        val vm = MockViewModel(progress, progress, FakeAccount(), FakeTools(), status)
        vm.applyTranscript(run, "first answer")
        assertEquals("first answer", progress.mock.value!!.draft)
        progress.mock.value = progress.mock.value!!.copy(index = 1, draft = "")
        vm.applyTranscript(run, "arrived late")
        assertEquals("", progress.mock.value!!.draft)
        vm.reportError("Microphone unavailable")
        assertEquals("Microphone unavailable", status.error.value)
    }

    @Test fun askShowsLibraryAnswersWithRelatedQuestionsThatCanBeSaved() = runTest(main) {
        val progress = FakeProgress()
        val vm = AskViewModel(FakeTutor(), progress, progress)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.send("Why does a ViewModel survive rotation?")
        val reply = vm.uiState.value.messages.last()
        assertEquals(TutorSource.Library, reply.source)
        val q = vm.uiState.value.related.getValue(reply.related.first())
        assertEquals("android-1", q.id)
        assertFalse(q.id in vm.uiState.value.inDeck)
        vm.addToDeck(q)
        assertTrue(q.id in vm.uiState.value.inDeck)
        vm.clear()
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test fun oneTapActionsAskAboutTheQuestionAndOpenAsk() = runTest(main) {
        val progress = FakeProgress()
        val tutor = FakeTutor(); val navigator = Navigator()
        val vm = PracticeViewModel(progress, progress, tutor, status, navigator)
        val q = TEST_QUESTIONS.first()
        vm.askAbout(q, TutorAction.FollowUps)
        assertEquals(AppScreen.Ask, navigator.screen.value)
        assertEquals(TutorAction.FollowUps.request(q), tutor.messages.value.first().text)
    }
}
