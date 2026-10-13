package com.prepdesk.data

import com.prepdesk.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** [ToolsRepository] backed by the PrepDesk Edge Function. */
class SupabaseToolsRepository(
    private val api: SupabaseApi,
    private val account: SupabaseAccountRepository,
    private val progress: ProgressStore,
    private val queue: OperationQueue,
) : ToolsRepository {
    private val _codeResult = MutableStateFlow<CodeResult?>(null)
    override val codeResult: StateFlow<CodeResult?> = _codeResult.asStateFlow()
    private val _languages = MutableStateFlow<List<CodeLanguage>>(emptyList())
    override val languages: StateFlow<List<CodeLanguage>> = _languages.asStateFlow()
    private val _practicalRun = MutableStateFlow<String?>(null)
    override val practicalRun: StateFlow<String?> = _practicalRun.asStateFlow()

    override fun requestFeedback() = queue.launch {
        val run = progress.mock.value ?: return@launch
        val q = run.questions[run.index] ?: error("AI feedback is available for library questions")
        require(q.rubric.isNotEmpty()) { "AI coaching requires a question with expected points" }
        require(run.draft.isNotBlank()) { "Write or transcribe an answer first" }
        val result = CONTENT_JSON.decodeFromString<AIFeedback>(api.function(account.token(), "assess", buildJsonObject {
            put("questionId", q.id); put("version", q.version); put("answer", run.draft); put("followups", run.followupNotes)
        }))
        require(result.covered.all { it in q.rubric.indices }) { "Feedback returned invalid expected points" }
        progress.saveMock { it.copy(aiFeedback = result) }
    }

    override fun transcribe(audio: ByteArray, apply: (String) -> Unit) = queue.launch {
        require(audio.size in 1..3_000_000) { "Recording exceeds the supported size" }
        val text = CONTENT_JSON.parseToJsonElement(api.function(account.token(), "transcribe", buildJsonObject {
            put("audio", kotlin.io.encoding.Base64.encode(audio))
        })).jsonObject.getValue("text").jsonPrimitive.content
        apply(text)
    }

    override fun loadLanguages() = queue.launch { _languages.value = CONTENT_JSON.decodeFromString(api.function(account.token(), "languages")) }

    override fun runCode(language: Int, code: String, stdin: String) = queue.launch {
        require(code.isNotBlank() && code.length <= 32_000 && stdin.length <= 8_000)
        _codeResult.value = CONTENT_JSON.decodeFromString(api.function(account.token(), "run-code", buildJsonObject { put("language", language); put("code", code); put("stdin", stdin) }))
        _practicalRun.value = null
    }

    // One submission runs every test: the harness prints a PASS/FAIL line per case.
    override fun runPractical(q: Q, code: String) = queue.launch {
        val p = q.practical ?: return@launch
        require(p.runnable) { "This exercise has no automated tests" }
        val program = p.program(code)
        require(code.isNotBlank() && program.length <= 32_000) { "Code is too long to run" }
        if (_languages.value.isEmpty()) _languages.value = CONTENT_JSON.decodeFromString(api.function(account.token(), "languages"))
        val language = _languages.value.filter { it.name.startsWith(p.language, ignoreCase = true) }.maxByOrNull { it.id }
            ?: error("${p.language} is not available on the code service")
        _codeResult.value = CONTENT_JSON.decodeFromString(api.function(account.token(), "run-code", buildJsonObject { put("language", language.id); put("code", program); put("stdin", "") }))
        _practicalRun.value = q.id
    }

    override fun pollCode() = queue.launch {
        val previous = _codeResult.value ?: return@launch
        _codeResult.value = CONTENT_JSON.decodeFromString(api.function(account.token(), "code-result", buildJsonObject { put("token", previous.token) }))
    }
}
