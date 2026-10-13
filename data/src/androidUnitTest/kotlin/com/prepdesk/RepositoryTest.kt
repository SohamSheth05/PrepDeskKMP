package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: PrepDatabase
    private lateinit var settings: MapSettings
    private val q get() = QUESTIONS.first { it.id == "android-search-flow" }

    @Before fun setup() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder<PrepDatabase>(context).setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).build()
        settings = MapSettings()
    }
    @After fun cleanup() { db.close() }

    @Test fun migratesLegacyOnceAndPreservesRecoveryCopy() = runTest {
        val legacy = AppState(cards = listOf(Card(42, q.area, q.q, q.a, 3, 123456)), days = mapOf("2026-10-01" to 5), pomos = 8)
        val document = CONTENT_JSON.encodeToString(legacy)
        settings.putString("state", document)
        val repository = PrepRepository(db, settings)
        val loaded = repository.initialize()
        assertEquals(42L, loaded.cards.single().id)
        assertEquals(q.id, loaded.cards.single().questionId)
        assertEquals(3, loaded.cards.single().box)
        assertEquals(123456L, loaded.cards.single().due)
        assertEquals(8, loaded.pomos)
        assertEquals(document, settings.getString("state", ""))
        repository.save(loaded.copy(pomos = 9))
        assertEquals(9, PrepRepository(db, settings).initialize().pomos)
    }

    @Test fun malformedLegacyDoesNotBecomeASeedDeck() = runTest {
        settings.putString("state", "not valid JSON")
        assertFails { PrepRepository(db, settings).initialize() }
        assertNull(db.dao().metadata("user_initialized"))
        assertTrue(db.dao().cards().isEmpty())
        assertEquals("not valid JSON", settings.getString("state", ""))
    }

    @Test fun contentUpgradeKeepsBookmarksAndProgressAndRejectsDowngrades() = runTest {
        val repository = PrepRepository(db, settings)
        val state = repository.initialize().copy(cards = emptyList()).addQuestions(listOf(q))
            .copy(bookmarks = setOf(q.id))
        repository.save(state.copy(cards = state.cards.map { it.copy(box = 3, due = 87654) }))
        val upgraded = q.copy(a = "A corrected explanation", version = 2)
        repository.publish(listOf(upgraded), SOURCES)
        val loaded = repository.readState()
        assertEquals(upgraded.a, loaded.cards.single().a)
        assertEquals(3, loaded.cards.single().box)
        assertEquals(87654L, loaded.cards.single().due)
        assertEquals(setOf(q.id), loaded.bookmarks)
        repository.publish(listOf(q), SOURCES)
        assertEquals(upgraded.a, repository.readState().cards.single().a)
    }

    @Test fun restartUsesBundledQuestionsPlusStoredUpdates() = runTest {
        val repository = PrepRepository(db, settings)
        repository.initialize()
        assertEquals(db.dao().questions().map { it.id }, repository.questions.map { it.id }, "Same order as the stored table")
        val corrected = q.copy(a = "Corrected by a content update", version = q.version + 1)
        val serverOnly = q.copy(id = "server-only-question", q = "A question only the server has", version = 1)
        repository.publish(listOf(corrected, serverOnly), SOURCES)
        val reopened = PrepRepository(db, settings)
        reopened.initialize()
        assertEquals(corrected, reopened.questions.single { it.id == q.id })
        assertEquals(serverOnly, reopened.questions.single { it.id == serverOnly.id })
        assertEquals(QUESTIONS.size + 1, reopened.questions.size)
        assertEquals(db.dao().questions().map { CONTENT_JSON.decodeFromString<Q>(it.document) }, reopened.questions)
    }

    @Test fun failedUserTransactionKeepsPreviouslySavedState() = runTest {
        val repository = PrepRepository(db, settings)
        val before = repository.initialize()
        assertFails { repository.save(before.copy(bookmarks = setOf("missing-question"), cards = emptyList())) }
        assertEquals(before.cards, repository.readState().cards)
        assertTrue(repository.readState().bookmarks.isEmpty())
    }

    @Test fun activeRoundResumesAndCompletedAttemptKeepsContentSnapshot() = runTest {
        val repository = PrepRepository(db, settings)
        val state = repository.initialize()
        val card = Card(100, q.area, q.q, q.a, questionId = q.id)
        val run = MockRun(100, 1000, "Android", listOf(card), listOf(q), stage = 2, draft = "An explanation", checked = setOf(0))
        repository.saveMock(state, run)
        val reopened = PrepRepository(db, settings)
        reopened.initialize()
        assertEquals(run, reopened.activeMock())
        val attempt = run.assess()
        reopened.saveMock(state, run.copy(index = 1, attempts = listOf(attempt)), attempt, completedAt = 2000)
        assertNull(reopened.activeMock())
        assertEquals("An explanation", reopened.readState().history.single().attempts.single().userAnswer)
        reopened.publish(listOf(q.copy(a = "New answer", version = 2)), SOURCES)
        assertEquals(q.a, reopened.readState().history.single().attempts.single().answer)
    }

    @Test fun expandedPacksImportIntoAnExistingDatabaseWithoutResettingPreparation() = runTest {
        val old = PrepRepository(db, settings)
        old.publish(listOf(q), SOURCES.filter { it.id in q.sourceIds })
        db.dao().initializeUser(
            listOf(CardRow(9, q.id, "", "", "", 3, 87654, 0), CardRow(10, null, "Personal", "Custom question", "Custom answer", 2, 76543, 1)),
            listOf(BookmarkRow(q.id)), listOf(ActivityRow("2026-10-01", 4)),
            listOf(MetadataRow("pomos", "7"), MetadataRow("plan", CONTENT_JSON.encodeToString(StudyPlan(area = "Python"))))
        )
        val before = old.readState()
        val round = MockRun(12, 1000, "Android", listOf(before.cards.first()), listOf(q), draft = "Saved before upgrade")
        old.saveRound(round)
        val expanded = PrepRepository(db, settings)
        val after = expanded.initialize()
        assertEquals(before, after)
        assertEquals(round, expanded.activeMock())
        assertEquals(QUESTIONS.associateBy { it.id }, expanded.questions.associateBy { it.id })
        assertEquals(SOURCES.map { it.id }.toSet(), expanded.sources.map { it.id }.toSet())
        val python = expanded.questions.first { it.id == "python-async-block" }
        expanded.save(after.addQuestions(listOf(python)).copy(bookmarks = after.bookmarks + python.id))
        val reopened = PrepRepository(db, settings)
        val loaded = reopened.initialize()
        assertTrue(loaded.cards.any { it.questionId == python.id && it.a == python.a })
        assertTrue(python.id in loaded.bookmarks)
        assertEquals(3, loaded.cards.first().box)
    }

    @Test fun studySessionResumesAndAssessmentSavesWithItsRevisionCard() = runTest {
        val repository = PrepRepository(db, settings)
        val before = repository.initialize()
        assertNull(before.study)
        val session = StudySession(q.area, q.level, q.subtopic, listOf(q.id), revealed = true)
        repository.save(before.copy(study = session))
        val reopened = PrepRepository(db, settings)
        val resumed = reopened.initialize()
        assertEquals(session, resumed.study)
        reopened.save(resumed.reviewStudy(q, false))
        val reviewed = PrepRepository(db, settings).initialize()
        assertTrue(reviewed.study!!.complete)
        assertEquals(setOf(q.id), reviewed.study!!.reviewed)
        assertEquals(0, reviewed.cards.first { it.questionId == q.id }.box)
        assertEquals(before.plan, reviewed.plan)
        assertEquals(before.bookmarks, reviewed.bookmarks)
        reopened.save(reviewed.copy(study = null))
        assertNull(PrepRepository(db, settings).initialize().study)
    }

    @Test fun backupRestoreIsAtomicAndPreservesHistoricalAnswers() = runTest {
        val repository = PrepRepository(db, settings)
        val before = repository.initialize()
        val card = Card(99, q.area, q.q, q.a, questionId = q.id)
        val run = MockRun(900, 1000, "Android", listOf(card), listOf(q), stage = 2, draft = "Saved answer", checked = setOf(0))
        val attempt = run.assess()
        val progress = before.copy(cards = listOf(card), history = listOf(MockResult(900, 1000, 2000, "Android", listOf(attempt))), focus = FocusSession(remainingMs = 12345))
        repository.keepRecovery(encodeBackup(before, null))
        repository.restore(decodeBackup(encodeBackup(progress,null), QUESTIONS), SyncMeta("owner",3,false))
        val loaded = repository.readState()
        assertEquals("Saved answer", loaded.history.single().attempts.single().userAnswer)
        assertEquals(12345L, loaded.focus.remainingMs)
        assertEquals("owner",loaded.sync.ownerId)
        assertNotNull(repository.recovery())
        assertFails { repository.restore(ProgressBackup(state = progress.copy(bookmarks = setOf("missing")))) }
        assertEquals(loaded,repository.readState())
    }

    @Test fun remoteReadingCorrectionsSurviveRepositoryRestart() = runTest {
        val repository = PrepRepository(db, settings)
        repository.initialize()
        val changed = SOURCES.map { if (it.id == "amit-android") it.copy(name = "Corrected reading title") else it }
        val resources = listOf(ResourceRecord("amit-android",listOf("Android"),listOf(1),"Updated reading note"))
        repository.publish(QUESTIONS,changed,resources)
        val reopened = PrepRepository(db, settings)
        reopened.initialize()
        assertEquals("Corrected reading title",reopened.sources.first { it.id == "amit-android" }.name)
        assertEquals("Updated reading note",reopened.resources().single().note)
    }
}
