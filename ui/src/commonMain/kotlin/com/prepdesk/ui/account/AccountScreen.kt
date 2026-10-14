package com.prepdesk.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prepdesk.ui.platform.BackupFileActions
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountScreen(vm: AccountViewModel = koinViewModel()) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val saving = ui.saving
    val uri = LocalUriHandler.current
    var email by rememberSaveable { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var restore by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Account and your data", style = MaterialTheme.typography.headlineSmall)
            Text("Prepare offline without an account. Sign in to sync progress and use available online interview tools.")
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            if (!ui.onlineConfigured) Text("Online services are not available in this build. Offline preparation and backups work normally.")
            else if (ui.session?.user?.is_anonymous != false) {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button({ vm.sendCode(email.trim()) }, enabled = !saving && email.isNotBlank()) { Text(if (ui.otpSent) "Resend code" else "Send sign-in code") }
                if (ui.otpSent) {
                    OutlinedTextField(code, { code = it }, label = { Text("Email verification code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button({ vm.verifyCode(email.trim(), code.trim()); code = "" }, enabled = !saving && code.isNotBlank()) { Text("Verify and sign in") }
                }
            } else {
                Text("Signed in as ${ui.session!!.user.email}")
                Text(if (ui.unsynced) "Device changes are waiting to sync." else "Device progress is synced.")
                Button({ vm.synchronize() }, enabled = !saving) { Text("Sync progress") }
                OutlinedButton({ vm.refreshCapabilities() }, enabled = !saving) { Text("Check available online tools") }
                TextButton({ vm.signOut() }, enabled = !saving) { Text("Sign out") }
                TextButton({ confirmation = "delete" }, enabled = !saving) { Text("Delete my account and cloud data") }
            }
            if (ui.onlineConfigured) OutlinedButton({ vm.updateContent() }, enabled = !saving) { Text("Check for question updates") }
        }
        if (ui.conflict != null) item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose your progress copy", style = MaterialTheme.typography.titleMedium)
                    Text("Device: ${ui.cardCount} cards, ${ui.mockCount} mock rounds. Cloud: ${ui.conflict!!.document.state.cards.size} cards, ${ui.conflict!!.document.state.history.size} mock rounds.")
                    Text("The selected copy replaces the other copy. A local recovery backup is saved first.")
                    Button({ confirmation = "cloud" }, enabled = !saving) { Text("Use cloud progress on this device") }
                    OutlinedButton({ confirmation = "device" }, enabled = !saving) { Text("Replace cloud with device progress") }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Backup and restore", style = MaterialTheme.typography.titleLarge)
            Text("A backup includes cards, bookmarks, your plan, study position, and mock answers. It does not contain sign-in tokens. Keep it private.")
            BackupFileActions(vm.backupActions(saving))
            OutlinedButton({ vm.recoveryBackup() }, enabled = !saving) { Text("Open recovery backup") }
            ui.backupDocument?.let { document ->
                OutlinedTextField(document, {}, label = { Text("Backup JSON — select to copy") }, readOnly = true, maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(restore, { restore = it }, label = { Text("Or paste backup JSON") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedButton({ confirmation = "restore" }, enabled = !saving && restore.isNotBlank()) { Text("Restore pasted backup") }
            TextButton({ confirmation = "clear" }, enabled = !saving) { Text("Clear preparation data on this device") }
        }
        item {
            HorizontalDivider()
            Text("Privacy", style = MaterialTheme.typography.titleLarge)
            Text("Offline study data stays on this device. Sync sends your preparation data to your account’s cloud storage. AI feedback sends only the selected answer and follow-up notes. Transcription sends a recording only when you choose Send. Code runs send your program and standard input to the execution service.")
            if (vm.support.privacyUrl.isNotBlank()) TextButton({ runCatching { uri.openUri(vm.support.privacyUrl) }.onFailure { vm.reportError("Could not open the privacy policy. Please try again.") } }) { Text("Read the full privacy policy") }
            if (vm.support.supportEmail.isNotBlank()) Text("Support and privacy requests: ${vm.support.supportEmail}")
            Text("You can continue using self-assessment and typed answers without any online tool. AI suggestions can be inaccurate; check them against the reference explanation.")
        }
    }
    confirmation?.let { action ->
        val description = when (action) {
            "clear" -> "This removes your cards, bookmarks, history, plan, saved sessions, and recovery copy from this device. Export a backup first if you want to keep them."
            "delete" -> "This permanently deletes your account and its cloud data, and clears preparation data on this device. Copies on other devices and exported files must be removed separately."
            "cloud" -> "Replace device progress with the cloud copy? Your current device progress will be kept as a local recovery backup."
            "device" -> "Replace cloud progress with this device’s copy? Other devices will see this copy on their next sync."
            else -> "Replace current device progress with the backup? Your existing progress will be retained as a recovery copy."
        }
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("Confirm data change") }, text = { Text(description) },
            confirmButton = { TextButton({
                when (action) { "clear" -> vm.clearDevice(); "delete" -> vm.deleteAccount(); "cloud" -> vm.resolveConflict(true); "device" -> vm.resolveConflict(false); else -> vm.restoreBackup(restore) }
                confirmation = null
            }) { Text("Confirm") } }, dismissButton = { TextButton({ confirmation = null }) { Text("Cancel") } })
    }
}
