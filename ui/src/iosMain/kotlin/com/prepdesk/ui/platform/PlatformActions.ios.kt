@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.prepdesk.ui.platform

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { array -> if (array.isNotEmpty()) array.usePinned { getBytes(it.addressOf(0), length) } }
private fun presenter(): UIViewController? {
    var view = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (view?.presentedViewController != null) view = view.presentedViewController
    return view
}

@Composable actual fun rememberAudioRecorder(onBackground: () -> Unit): AudioRecorder {
    val recorder = remember { IOSRecorder() }
    val background by rememberUpdatedState(onBackground)
    DisposableEffect(recorder) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { recorder.discard(); background() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer); recorder.discard() }
    }
    return recorder
}
private class IOSRecorder : AudioRecorder {
    private val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + "prepdesk-answer.m4a")
    private var recorder: AVAudioRecorder? = null
    override suspend fun start() {
        val session = AVAudioSession.sharedInstance()
        val permitted = suspendCancellableCoroutine<Boolean> { continuation -> session.requestRecordPermission { if (continuation.isActive) continuation.resume(it) } }
        check(permitted); discard()
        check(session.setCategory(AVAudioSessionCategoryRecord, error = null))
        check(session.setActive(true, error = null))
        val next = AVAudioRecorder(file, mapOf<Any?, Any?>(AVFormatIDKey to 1633772320, AVSampleRateKey to 44100, AVNumberOfChannelsKey to 1, AVEncoderBitRateKey to 64000), null)
        recorder = next; check(next.prepareToRecord()); check(next.recordForDuration(120.0))
    }
    override fun stop(): ByteArray = try {
        recorder?.stop(); recorder = null
        val data = NSData.dataWithContentsOfURL(file) ?: error("No recording")
        check(data.length in 1uL..3_000_000uL); data.toByteArray()
    } finally { discard() }
    override fun discard() { recorder?.stop(); recorder = null; NSFileManager.defaultManager.removeItemAtURL(file, null); AVAudioSession.sharedInstance().setActive(false, error = null) }
}

private class ImportDelegate(private val imported: (String) -> Unit, private val failed: () -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val access = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: error("Cannot read file")
            require(data.length <= 2_000_000uL); imported(data.toByteArray().decodeToString())
        } catch (_: Exception) { failed() } finally { if (access) url.stopAccessingSecurityScopedResource() }
    }
}

@Composable actual fun BackupFileActions(actions: BackupActions) {
    val scope = rememberCoroutineScope()
    var incoming by remember { mutableStateOf<String?>(null) }
    val delegate = remember { ImportDelegate({ incoming = it }, { actions.reportError("Could not read this backup. Choose a PrepDesk JSON file under 2 MB.") }) }
    fun export(document: String) {
        val bytes = document.encodeToByteArray()
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + "prepdesk-backup.json")
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        if (data.writeToURL(file, true)) {
            val picker = UIDocumentPickerViewController(forExportingURLs = listOf(file), asCopy = true)
            presenter()?.presentViewController(picker, true, null)
        } else actions.reportError("Could not prepare the backup file")
    }
    OutlinedButton({ scope.launch { actions.prepare()?.let(::export) } }, enabled = actions.enabled) { Text("Save backup file") }
    OutlinedButton({
        val picker = UIDocumentPickerViewController(documentTypes = listOf("public.json", "public.text"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
        picker.delegate = delegate; presenter()?.presentViewController(picker, true, null)
    }, enabled = actions.enabled) { Text("Restore from file") }
    incoming?.let { document -> AlertDialog(onDismissRequest = { incoming = null }, title = { Text("Restore backup?") }, text = { Text("This replaces device progress. A recovery copy is saved first.") },
        confirmButton = { TextButton({ actions.restore(document); incoming = null }) { Text("Restore") } }, dismissButton = { TextButton({ incoming = null }) { Text("Cancel") } }) }
}
