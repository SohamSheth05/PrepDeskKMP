package com.prepdesk

import com.prepdesk.data.*
import com.prepdesk.domain.*
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncTest {
    private class Vault(var document: String?) : SessionVault {
        override fun read()=document
        override fun write(value: String?) { document=value }
    }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(20_000) { while(!condition()) delay(10) }
    private class Repos(val progress: ProgressStore, val account: SupabaseAccountRepository, val queue: OperationQueue) {
        val idle get() = queue.pending.value == 0
    }
    private suspend fun withStore(owner: String? = null, remote: CloudProgress? = null, vaultBroken: Boolean = false, test: suspend (Repos,PrepRepository,MutableList<String>) -> Unit) {
        val db=Room.inMemoryDatabaseBuilder<PrepDatabase>(RuntimeEnvironment.getApplication()).setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val repository=PrepRepository(db,MapSettings());val initial=repository.initialize()
        if(owner!=null) repository.save(initial.copy(sync=SyncMeta(owner,1,true)))
        val calls=mutableListOf<String>()
        val api=SupabaseApi("https://example.supabase.co","public",HttpClient(MockEngine { request ->
            synchronized(calls) { calls.add(request.url.encodedPath) }
            val response=when(request.url.encodedPath) {
                "/rest/v1/progress" -> CONTENT_JSON.encodeToString(listOfNotNull(remote))
                "/rest/v1/rpc/save_progress" -> "3"
                else -> "{}"
            }
            respond(response,HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }) { install(HttpTimeout) })
        val vault=Vault(CONTENT_JSON.encodeToString(AuthSession("access","refresh",nowMs()/1000+3600,user=SignedInUser("account-a","a@example.com"))))
        val secureVault = if (vaultBroken) object : SessionVault { override fun read(): String? = error("Keychain unavailable"); override fun write(value: String?) {} } else vault
        val queue=OperationQueue(scope)
        val progress=ProgressStore(repository,queue)
        val account=SupabaseAccountRepository(api,secureVault,progress,queue)
        progress.load();account.restoreSession()
        val repos=Repos(progress,account,queue)
        try { waitFor { progress.ready.value && repos.idle };test(repos,repository,calls) } finally { scope.cancel();account.close();db.close() }
    }
    @Test fun dirtyCopiesConflictInsteadOfSilentlyOverwritingCloud() = runBlocking {
        val cloud=CloudProgress(2,ProgressBackup(state=AppState(cards=emptyList(),pomos=7)))
        withStore(owner="account-a",remote=cloud) { store,repository,calls ->
            store.account.synchronize();waitFor { store.idle }
            assertEquals(cloud,store.account.conflict.value);assertFalse(calls.contains("/rest/v1/rpc/save_progress"))
            store.account.resolveConflict(true);waitFor { store.idle }
            val state=store.progress.state.value
            assertEquals(7,state.pomos);assertFalse(state.sync.dirty);assertEquals(2L,state.sync.revision)
            assertNotNull(repository.recovery());assertNull(store.account.conflict.value)
        }
    }
    @Test fun anotherAccountsDeviceDataCannotBeUploaded() = runBlocking {
        withStore(owner="account-b") { store,_,calls ->
            store.account.synchronize();waitFor { store.idle }
            assertTrue(store.queue.error.value!!.contains("another account"));assertTrue(calls.isEmpty())
            assertEquals("account-b",store.progress.state.value.sync.ownerId)
        }
    }
    @Test fun restoreFailureRetainsExistingStateAndClearRemovesRecovery() = runBlocking {
        withStore { store,repository,_ ->
            val before=store.progress.state.value
            store.progress.restoreBackup("invalid JSON");waitFor { store.idle }
            assertEquals(before,store.progress.state.value);assertEquals(before,repository.readState())
            repository.keepRecovery(encodeBackup(before,null))
            store.account.clearDevice();waitFor { store.idle }
            val state=store.progress.state.value
            assertTrue(state.cards.isEmpty());assertNull(repository.recovery());assertNull(state.sync.ownerId)
        }
    }
    @Test fun secureSessionFailureDoesNotBlockOfflinePreparation() = runBlocking {
        withStore(vaultBroken = true) { store,_,_ ->
            assertTrue(store.progress.ready.value);assertTrue(store.progress.library.value.questions.isNotEmpty());assertNull(store.account.session.value)
            assertTrue(store.queue.message.value!!.contains("offline"));assertNull(store.queue.error.value)
        }
    }
}
