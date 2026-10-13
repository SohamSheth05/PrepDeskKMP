package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.*

class ProgressTest {
    @Test fun expiredFocusCompletesExactlyOnceAndBreakDoesNotLogStudy() {
        val running = AppState(cards = emptyList(), pomos = 4, focus = FocusSession(deadline = 1000))
        assertEquals(running, running.finishFocus(999))
        val finished = running.finishFocus(1000)
        assertEquals(5, finished.pomos); assertFalse(finished.focus.focus); assertNull(finished.focus.deadline)
        assertEquals(finished, finished.finishFocus(2000))
        val afterBreak = finished.copy(focus = finished.focus.copy(deadline = 3000)).finishFocus(4000)
        assertEquals(5, afterBreak.pomos); assertEquals(finished.days, afterBreak.days); assertTrue(afterBreak.focus.focus)
    }
    @Test fun deckQuestionIdsMatchesInDeckForLinkedAndCopiedCards() {
        val (linked, copied, absent) = QUESTIONS.take(3)
        val state = AppState(cards = listOf(Card(1, linked.area, "", "", questionId = linked.id), Card(2, "Personal", copied.q, copied.a)))
        assertEquals(setOf(linked.id, copied.id), state.deckQuestionIds(listOf(linked, copied, absent)))
        assertEquals(QUESTIONS.filter { state.inDeck(it) }.map { it.id }.toSet(), state.deckQuestionIds(QUESTIONS))
    }
    @Test fun portableBackupsStripAccountOwnershipAndPauseTimers() {
        val state = AppState(cards = listOf(Card(42, "Personal", "Question", "Answer")), focus = FocusSession(deadline = nowMs() + 100_000), sync = SyncMeta("account-private", 8, false))
        val text = encodeBackup(state, null)
        assertFalse(text.contains("account-private"))
        val restored = decodeBackup(text, QUESTIONS)
        assertEquals(state.cards, restored.state.cards); assertNull(restored.state.focus.deadline)
        assertTrue(restored.state.focus.remainingMs in 1..100_000); assertNull(restored.state.sync.ownerId)
    }
    @Test fun invalidBackupNeverBecomesEmptyProgress() {
        val card = Card(1, "Personal", "Q", "A")
        val state = AppState(cards = listOf(card, card))
        assertFails { decodeBackup(encodeBackup(state, null), QUESTIONS) }
        assertFails { decodeBackup("not JSON", QUESTIONS) }
        assertFails { decodeBackup(CONTENT_JSON.encodeToString(ProgressBackup(format = 2, state = AppState())), QUESTIONS) }
    }
    @Test fun unknownContentBecomesCustomCardsAndStudyIsDiscarded() {
        val state = AppState(cards = listOf(Card(1,"Old","Prompt","Original answer",questionId="missing")),bookmarks=setOf("missing"),study=StudySession("Old",1,"General",listOf("missing")))
        val restored = decodeBackup(encodeBackup(state,null), QUESTIONS).state
        assertNull(restored.cards.single().questionId); assertEquals("Original answer",restored.cards.single().a)
        assertTrue(restored.bookmarks.isEmpty());assertNull(restored.study)
    }
    @Test fun malformedRemoteContentAndUnsafeLinksAreRejected() {
        val pack = ContentPack(questions = QUESTIONS, sources = SOURCES, resources = STUDY_RESOURCES.map { ResourceRecord(it.sourceId,it.areas,it.levels,it.note) })
        validateContent(pack)
        assertFails { validateContent(pack.copy(sources = SOURCES.mapIndexed { index, source -> if(index == 0) source.copy(url="http://example.com") else source })) }
        assertFails { validateContent(pack.copy(questions = QUESTIONS + QUESTIONS.first())) }
        assertFails { validateContent(pack.copy(resources = listOf(ResourceRecord("missing",listOf("Android"),listOf(1),"bad")))) }
    }
    @Test fun authorizationAndCompareAndSwapAreSentToSupabase() = runTest {
        val engine = MockEngine { request ->
            assertEquals("public-key",request.headers["apikey"]);assertEquals("Bearer user-token",request.headers["Authorization"])
            assertEquals("/rest/v1/rpc/save_progress",request.url.encodedPath)
            assertTrue(request.body.toByteArray().decodeToString().contains("\"expected_revision\":7"))
            respond("8",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }
        val client=HttpClient(engine){install(HttpTimeout)}
        val api=SupabaseApi("https://example.supabase.co","public-key",client)
        try { assertEquals(8L,api.push("user-token",7,ProgressBackup(state=AppState(cards=emptyList())))) } finally {api.close()}
    }
    @Test fun cloudConflictIsVisibleAndProviderErrorContentIsNotExposed() = runTest {
        val api=SupabaseApi("https://example.supabase.co","public",HttpClient(MockEngine { respond("sensitive provider details",HttpStatusCode.Conflict) }){install(HttpTimeout)})
        try {
            val failure=assertFailsWith<ServiceException>{api.push("token",1,ProgressBackup(state=AppState()))}
            assertEquals(409,failure.status);assertFalse(failure.message!!.contains("sensitive"))
        } finally { api.close() }
    }
    @Test fun everyTrackHasAnInterviewOrPreparationReadingCollection() {
        for(area in questionAreas(QUESTIONS)) assertTrue(STUDY_RESOURCES.any { area in it.areas }, "$area has no curated preparation resource")
        assertTrue(STUDY_RESOURCES.any { it.sourceId == "android-preparation-series" })
        assertTrue(STUDY_RESOURCES.any { it.sourceId == "android-basics-2024" })
        assertTrue(STUDY_RESOURCES.any { it.sourceId == "android-sde-roadmap" })
    }
}
