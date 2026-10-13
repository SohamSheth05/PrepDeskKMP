package com.prepdesk.ui.platform

import androidx.compose.runtime.Composable

interface AudioRecorder { suspend fun start(); fun stop(): ByteArray; fun discard() }

@Composable expect fun rememberAudioRecorder(onBackground: () -> Unit): AudioRecorder

/** What the platform backup buttons need from the Account screen. */
class BackupActions(
    val enabled: Boolean,
    /** Prepares the backup document and returns it, or null if that failed. */
    val prepare: suspend () -> String?,
    val restore: (String) -> Unit,
    val reportError: (String) -> Unit,
)

@Composable expect fun BackupFileActions(actions: BackupActions)
