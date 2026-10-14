package com.prepdesk.ui.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberAudioRecorder(onBackground: () -> Unit): AudioRecorder {
    val context = LocalContext.current
    var request by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { request?.complete(it) }
    val recorder = remember(context) { AndroidRecorder(context) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) true else {
            val result = CompletableDeferred<Boolean>(); request = result
            permission.launch(Manifest.permission.RECORD_AUDIO); result.await()
        }
    } }
    val background by rememberUpdatedState(onBackground)
    DisposableEffect(context, recorder) {
        val owner = context as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { recorder.discard(); background() }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer); recorder.discard() }
    }
    return recorder
}

private class AndroidRecorder(private val context: Context, private val permission: suspend () -> Boolean) : AudioRecorder {
    private var recorder: MediaRecorder? = null
    private val file = File(context.cacheDir, "prepdesk-answer.m4a")
    override suspend fun start() {
        check(permission()); discard()
        @Suppress("DEPRECATION")
        val next = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = next
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(64_000); next.setAudioSamplingRate(44_100)
            next.setMaxFileSize(3_000_000); next.setOutputFile(file.absolutePath)
            next.prepare(); next.start()
        } catch (e: Exception) { discard(); throw e }
    }
    override fun stop(): ByteArray = try {
        recorder?.stop(); recorder?.release(); recorder = null
        check(file.length() in 1..3_000_000); file.readBytes()
    } finally { discard() }
    override fun discard() { runCatching { recorder?.release() }; recorder = null; file.delete() }
}

@Composable
actual fun BackupFileActions(actions: BackupActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val text = document
        if (uri != null && text != null) scope.launch {
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) } ?: error("Cannot write file") } }
            catch (_: Exception) { actions.reportError("Could not save the backup file. Your preparation is unchanged.") }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { incoming = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (output.size() <= 2_000_000) {
                        val count = stream.read(chunk); if (count < 0) break
                        output.write(chunk, 0, count)
                    }
                    val bytes = output.toByteArray()
                    require(bytes.size <= 2_000_000); bytes.decodeToString()
                } ?: error("Cannot read file")
            } } catch (_: Exception) { actions.reportError("Could not read this backup. Choose a MockMentor JSON file under 2 MB.") }
        }
    }
    OutlinedButton({ scope.launch { actions.prepare()?.let { document = it; export.launch("prepdesk-backup.json") } } }, enabled = actions.enabled) { Text("Save backup file") }
    OutlinedButton({ import.launch(arrayOf("application/json", "text/plain")) }, enabled = actions.enabled) { Text("Restore from file") }
    incoming?.let { text -> AlertDialog(onDismissRequest = { incoming = null }, title = { Text("Restore backup?") }, text = { Text("This replaces device progress. A recovery copy of your current preparation is saved first.") },
        confirmButton = { TextButton({ actions.restore(text); incoming = null }) { Text("Restore") } }, dismissButton = { TextButton({ incoming = null }) { Text("Cancel") } }) }
}
