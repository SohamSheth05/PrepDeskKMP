package com.prepdesk.ui.mock

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prepdesk.ui.platform.rememberAudioRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Optional voice answer: records locally and transcribes only after explicit consent. */
@Composable
fun VoiceAnswer(available: Boolean, saving: Boolean, transcribe: (ByteArray, (String) -> Unit) -> Unit, reportError: (String) -> Unit, onTranscript: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    val recorder = rememberAudioRecorder { recording = false }
    var audio by remember { mutableStateOf<ByteArray?>(null) }
    var consent by remember { mutableStateOf(false) }
    DisposableEffect(recorder) { onDispose { recorder.discard() } }
    fun stop() {
        try { audio = recorder.stop() } catch (_: Exception) { reportError("Could not finish the recording. Please try again.") }
        recording = false
    }
    LaunchedEffect(recording) { if (recording) { delay(120_000); stop() } }
    if (available) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speak your answer", style = MaterialTheme.typography.titleMedium)
        Text("Record up to two minutes. Audio stays temporary on this device until you choose Send for transcription.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton({
            if (recording) stop() else scope.launch {
                try { recorder.start(); audio = null; consent = false; recording = true }
                catch (_: Exception) { reportError("Microphone access is needed to record. You can keep typing your answer.") }
            }
        }, enabled = !saving) { Text(if (recording) "Stop recording" else "Record answer") }
        if (audio != null) {
            Row { Checkbox(consent, { consent = it }); Text("Send this recording to the transcription service.", Modifier.weight(1f)) }
            Button({ transcribe(audio!!) { text -> onTranscript(text); audio = null; consent = false } }, enabled = consent && !saving) { Text("Send for transcription") }
            TextButton({ audio = null; recorder.discard() }) { Text("Discard recording") }
        }
    }
}
