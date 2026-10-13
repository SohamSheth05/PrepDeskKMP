package com.prepdesk.data

import com.prepdesk.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString

/** [AccountRepository] backed by Supabase Auth, the sync function, and the content release table. */
class SupabaseAccountRepository(
    private val api: SupabaseApi,
    private val vault: SessionVault,
    private val progress: ProgressStore,
    private val queue: OperationQueue,
) : AccountRepository {
    private val _session = MutableStateFlow<AuthSession?>(null)
    override val session: StateFlow<AuthSession?> = _session.asStateFlow()
    private val _capabilities = MutableStateFlow(ServiceCapabilities())
    override val capabilities: StateFlow<ServiceCapabilities> = _capabilities.asStateFlow()
    private val _conflict = MutableStateFlow<CloudProgress?>(null)
    override val conflict: StateFlow<CloudProgress?> = _conflict.asStateFlow()
    private val _otpSent = MutableStateFlow(false)
    override val otpSent: StateFlow<Boolean> = _otpSent.asStateFlow()
    override val onlineConfigured get() = api.configured

    /** Restores a saved sign-in. A broken secure store must never block offline preparation. */
    override fun restoreSession() = queue.launch {
        _session.value = runCatching { vault.read()?.let { CONTENT_JSON.decodeFromString<AuthSession>(it) } }.getOrElse {
            runCatching { vault.write(null) }
            queue.notify("Your sign-in session could not be restored. You can continue preparing offline or sign in again.")
            null
        }
    }

    /** A valid access token, refreshed when it is about to expire. */
    internal suspend fun token(): String {
        var current = _session.value ?: error("Sign in to use this feature")
        if (current.expires_at <= nowMs() / 1000 + 60) {
            try {
                current = api.refresh(current)
                vault.write(CONTENT_JSON.encodeToString(current)); _session.value = current
            } catch (e: ServiceException) {
                if (e.status in listOf(400, 401)) { vault.write(null); _session.value = null }
                throw e
            }
        }
        return current.access_token
    }

    override fun sendCode(email: String) = queue.launch {
        require(email.length <= 254 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) { "Enter a valid email address" }
        api.requestCode(email); _otpSent.value = true; queue.notify("Check your email for a sign-in code.")
    }

    override fun verifyCode(email: String, code: String) = queue.launch {
        require(code.length in 6..10 && code.all(Char::isDigit)) { "Enter the code from your email" }
        val next = api.verifyCode(email, code)
        vault.write(CONTENT_JSON.encodeToString(next)); _session.value = next; _otpSent.value = false
        _capabilities.value = api.capabilities(token()); queue.notify("Signed in. Sync to back up this device.")
    }

    override fun signOut() = queue.launch {
        try { _session.value?.let { api.logout(it.access_token) } } finally {
            vault.write(null); _session.value = null; _capabilities.value = ServiceCapabilities(); _conflict.value = null
            queue.notify("Signed out. Your preparation remains on this device.")
        }
    }

    override fun refreshCapabilities() = queue.launch { if (_session.value != null) _capabilities.value = api.capabilities(token()) }

    override fun synchronize() = queue.launch {
        val user = _session.value?.user ?: error("Sign in to sync your progress")
        val state = progress.current
        check(state.sync.ownerId == null || state.sync.ownerId == user.id) { "This device has another account’s progress. Sign in to that account, or clear device data before syncing a different account." }
        val access = token()
        val remote = api.cloud(access)
        if (remote != null && remote.revision != state.sync.revision) {
            if (state.sync.dirty) { _conflict.value = remote; queue.notify("Both this device and the cloud have changes. Choose which copy to keep.") }
            else restoreCloud(remote, user.id)
        } else {
            if (state.sync.dirty || remote == null) {
                val revision = api.push(access, remote?.revision ?: 0, progress.syncDocument())
                progress.markSynced(SyncMeta(user.id, revision, false, nowMs()))
            }
            queue.notify("Progress synced.")
        }
    }

    private suspend fun restoreCloud(remote: CloudProgress, owner: String) {
        val backup = decodeBackup(CONTENT_JSON.encodeToString(remote.document), progress.library.value.questions)
        progress.replaceWith(backup, SyncMeta(owner, remote.revision, false, nowMs()))
        _conflict.value = null; queue.notify("Cloud progress restored.")
    }

    override fun resolveConflict(useCloud: Boolean) = queue.launch {
        val remote = _conflict.value ?: return@launch
        val user = _session.value?.user ?: error("Sign in again")
        check(progress.current.sync.ownerId == null || progress.current.sync.ownerId == user.id)
        // Keep a recoverable copy before either direction replaces progress.
        progress.keepRecoveryCopy()
        if (useCloud) restoreCloud(remote, user.id) else {
            val revision = api.push(token(), remote.revision, progress.syncDocument())
            progress.markSynced(SyncMeta(user.id, revision, false, nowMs()))
            _conflict.value = null; queue.notify("Device progress uploaded. A recovery copy was saved.")
        }
    }

    override fun deleteAccount() = queue.launch {
        api.deleteAccount(token())
        vault.write(null); _session.value = null; _capabilities.value = ServiceCapabilities()
        progress.clearNow(); _conflict.value = null
        queue.notify("Your account and its cloud progress were deleted.")
    }

    override fun clearDevice() = queue.launch {
        progress.clearNow(); _conflict.value = null
        queue.notify("Device progress cleared.")
    }

    override fun updateContent() = queue.launch {
        val pack = api.content(); validateContent(pack)
        progress.applyContent(pack)
        queue.notify("Question library updated. Your review progress is preserved.")
    }

    fun close() = api.close()
}
