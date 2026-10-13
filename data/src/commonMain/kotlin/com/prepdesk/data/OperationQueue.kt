package com.prepdesk.data

import com.prepdesk.domain.AppStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Runs every database and network change one at a time on an application-scoped coroutine, so
 * writes never interleave and are not cancelled when a screen or ViewModel goes away.
 * Failures become a user-visible [error] instead of crashing.
 */
class OperationQueue(scope: CoroutineScope) : AppStatus {
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _pending = MutableStateFlow(0)
    override val pending: StateFlow<Int> = _pending.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    override val message: StateFlow<String?> = _message.asStateFlow()

    init {
        scope.launch {
            for (operation in operations) {
                try { operation() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _error.value = e.message ?: "Could not save. Please try again." }
            }
        }
    }

    /** Queue an operation without waiting for it. */
    fun launch(action: suspend () -> Unit) = enqueue(action, null)

    /** Queue an operation and wait until it finishes; returns false when it failed. */
    suspend fun run(action: suspend () -> Unit): Boolean {
        val done = CompletableDeferred<Boolean>()
        enqueue(action, done)
        return done.await()
    }

    private fun enqueue(action: suspend () -> Unit, done: CompletableDeferred<Boolean>?) {
        _pending.update { it + 1 }
        val sent = operations.trySend {
            try { action(); done?.complete(true) }
            catch (e: Throwable) { done?.complete(false); throw e }
            finally { _pending.update { it - 1 } }
        }
        if (sent.isFailure) { _pending.update { it - 1 }; done?.complete(false) }
    }

    override fun reportError(message: String) { _error.value = message }
    override fun dismissError() { _error.value = null }
    fun notify(message: String?) { _message.value = message }
}
