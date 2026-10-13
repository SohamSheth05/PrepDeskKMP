package com.prepdesk

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.prepdesk.data.*
import com.prepdesk.ui.viewModelModule
import com.prepdesk.domain.*
import com.prepdesk.ui.AppScreen
import com.prepdesk.ui.AppViewModel
import com.prepdesk.ui.Navigator
import com.prepdesk.ui.account.AccountViewModel
import com.prepdesk.ui.cards.CardsViewModel
import com.prepdesk.ui.focus.FocusViewModel
import com.prepdesk.ui.history.HistoryViewModel
import com.prepdesk.ui.library.LibraryViewModel
import com.prepdesk.ui.mock.MockViewModel
import com.prepdesk.ui.plan.PlanViewModel
import com.prepdesk.ui.play.PlayViewModel
import com.prepdesk.ui.practical.PracticalViewModel
import com.prepdesk.ui.practice.PracticeViewModel
import com.prepdesk.ui.prepare.PrepareViewModel
import com.prepdesk.ui.today.TodayViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

/** Verifies the assembled Koin graph across :data and :ui, and that ViewModels drive the real repositories end to end. */
@RunWith(RobolectricTestRunner::class)
// A plain Application, so this test starts Koin with an in-memory database instead of the app's own setup.
@Config(sdk = [34], application = android.app.Application::class)
class ArchitectureTest {
    private lateinit var db: PrepDatabase
    private val koin get() = KoinPlatform.getKoin()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder<PrepDatabase>(RuntimeEnvironment.getApplication()).setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).build()
        // Later definitions override the app's: an in-memory database and settings for each test.
        startKoin { modules(dataModule, viewModelModule, module {
            single { db }; single<Settings> { MapSettings() }
            single<SessionVault> { object : SessionVault { override fun read(): String? = null; override fun write(value: String?) {} } }
        }) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After fun tearDown() { stopKoin(); db.close(); Dispatchers.resetMain() }

    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(20_000) { while (!condition()) delay(10) }
    private suspend fun ready() {
        koin.get<AppViewModel>()
        val progress = koin.get<ProgressStore>()
        waitFor { progress.ready.value && koin.get<OperationQueue>().pending.value == 0 }
    }

    @Test fun graphResolvesEveryViewModelWithSharedSingletons() = runBlocking {
        ready()
        listOf(TodayViewModel::class, PlayViewModel::class, PrepareViewModel::class, PracticeViewModel::class, MockViewModel::class,
            LibraryViewModel::class, PlanViewModel::class, FocusViewModel::class, CardsViewModel::class, HistoryViewModel::class,
            AccountViewModel::class, PracticalViewModel::class, com.prepdesk.ui.ask.AskViewModel::class).forEach { assertNotNull(koin.get<Any>(it)) }
        assertSame(koin.get<ProgressStore>(), koin.get<ProgressStore>())
        // Every interface is backed by the one store and queue, so saves stay ordered and atomic.
        assertSame(koin.get<ProgressStore>(), koin.get<ProgressSource>())
        assertSame(koin.get<OperationQueue>(), koin.get<AppStatus>())
        assertSame(koin.get<SupabaseAccountRepository>(), koin.get<AccountRepository>())
        listOf(DeckRepository::class, StudyRepository::class, MockRepository::class, FocusRepository::class,
            RewardsRepository::class, BackupRepository::class, ToolsRepository::class, TutorRepository::class).forEach { assertNotNull(koin.get<Any>(it)) }
        assertSame(koin.get<Navigator>(), koin.get<Navigator>())
        assertTrue(koin.get<ProgressStore>().library.value.questions.size > 1000)
    }

    @Test fun libraryFiltersInTheViewModelAndAddsToTheDeck() = runBlocking {
        ready()
        val vm = koin.get<LibraryViewModel>()
        val collector = launch(Dispatchers.Default) { vm.uiState.collect {} }
        vm.setFilter(QuestionFilter(area = "Kotlin", round = "Practical"))
        waitFor { vm.uiState.value.filter.area == "Kotlin" && vm.uiState.value.results.isNotEmpty() }
        val results = vm.uiState.value.results
        assertTrue(results.all { it.area == "Kotlin" && it.round == "Practical" })
        vm.addToDeck(results.take(2))
        waitFor { vm.uiState.value.inDeck.containsAll(results.take(2).map { it.id }) }
        assertEquals(2, koin.get<ProgressStore>().state.value.cards.count { it.questionId in results.map { q -> q.id } })
        collector.cancel()
    }

    @Test fun answerRushScoresCombosAndPenalisesMisses() = runBlocking {
        ready()
        val vm = koin.get<PlayViewModel>()
        vm.start("Kotlin")
        waitFor { vm.game.value != null }
        repeat(3) {
            val g = vm.game.value!!
            assertTrue(vm.answer(g.current.correct))
            waitFor { vm.game.value!!.picked == null }
        }
        assertEquals(30, vm.game.value!!.score.score)
        assertEquals(2, vm.game.value!!.score.multiplier)
        val before = vm.game.value!!
        assertFalse(vm.answer((before.current.correct + 1) % 4))
        assertEquals(before.deadline - RUSH_PENALTY_MS, vm.game.value!!.deadline)
        assertEquals(1, vm.game.value!!.score.multiplier)
        vm.quit()
        assertNull(vm.game.value)
    }

    @Test fun drillFromTodayOpensTheQuestOnPrepare() = runBlocking {
        ready()
        val today = koin.get<TodayViewModel>()
        val collector = launch(Dispatchers.Default) { today.uiState.collect {} }
        waitFor { today.uiState.value.readiness.topics.isNotEmpty() }
        val topic = today.uiState.value.readiness.weakest.first()
        today.drill(topic)
        val navigator = koin.get<Navigator>()
        waitFor { navigator.screen.value == AppScreen.Prepare && navigator.openQuest.value }
        val study = koin.get<ProgressStore>().state.value.study!!
        assertEquals(topic.subtopic, study.subtopic)
        assertTrue(study.questionIds.isNotEmpty() && study.questionIds.size <= 5)
        koin.get<PrepareViewModel>()
        waitFor { !navigator.openQuest.value }
        collector.cancel()
    }
}
