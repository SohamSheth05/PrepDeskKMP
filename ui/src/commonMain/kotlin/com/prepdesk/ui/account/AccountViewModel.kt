package com.prepdesk.ui.account

import androidx.lifecycle.ViewModel
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import com.prepdesk.ui.platform.BackupActions
import kotlinx.coroutines.flow.*

data class AccountUiState(
    val onlineConfigured: Boolean = false, val unsynced: Boolean = false, val cardCount: Int = 0, val mockCount: Int = 0,
    val saving: Boolean = false, val message: String? = null,
    val session: AuthSession? = null, val otpSent: Boolean = false, val conflict: CloudProgress? = null, val backupDocument: String? = null,
)

class AccountViewModel(
    progress: ProgressSource, private val backups: BackupRepository, private val account: AccountRepository, private val status: AppStatus,
    /** Privacy-policy link and support address configured for this build. */
    val support: SupportContact,
) : ViewModel() {
    private val auth = combine(account.session, account.otpSent, account.conflict) { session, otp, conflict -> Triple(session, otp, conflict) }

    val uiState: StateFlow<AccountUiState> = combine(snapshotOf(progress, status), status.message, auth, backups.backupDocument) { snap, message, (session, otp, conflict), backup ->
        val s = snap.state
        AccountUiState(account.onlineConfigured, s.sync.dirty, s.cards.size, s.history.size, snap.saving, message, session, otp, conflict, backup)
    }.let { flow ->
        val s = progress.state.value
        stateOf(flow, AccountUiState(account.onlineConfigured, s.sync.dirty, s.cards.size, s.history.size, session = account.session.value))
    }

    fun sendCode(email: String) = account.sendCode(email)
    fun verifyCode(email: String, code: String) = account.verifyCode(email, code)
    fun synchronize() = account.synchronize()
    fun refreshCapabilities() = account.refreshCapabilities()
    fun signOut() = account.signOut()
    fun deleteAccount() = account.deleteAccount()
    fun resolveConflict(useCloud: Boolean) = account.resolveConflict(useCloud)
    fun updateContent() = account.updateContent()
    fun clearDevice() = account.clearDevice()
    fun recoveryBackup() = backups.recoveryBackup()
    fun restoreBackup(document: String) = backups.restoreBackup(document)
    fun reportError(message: String) = status.reportError(message)

    fun backupActions(saving: Boolean) = BackupActions(!saving, backups::prepareBackup, ::restoreBackup, ::reportError)
}
