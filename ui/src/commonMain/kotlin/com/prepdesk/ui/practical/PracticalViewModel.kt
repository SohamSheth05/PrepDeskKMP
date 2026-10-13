package com.prepdesk.ui.practical

import androidx.lifecycle.ViewModel
import com.prepdesk.domain.*
import com.prepdesk.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

data class PracticalUiState(
    val exercises: List<Q> = emptyList(), val areas: List<String> = emptyList(), val defaultArea: String = ALL_TRACKS,
    /** Review stage of each attempted exercise by question ID. */
    val stages: Map<String, Int> = emptyMap(), val saving: Boolean = false,
)

data class CodeRunState(
    val signedIn: Boolean = false, val available: Boolean = false, val result: CodeResult? = null,
    val resultFor: String? = null, val languages: List<CodeLanguage> = emptyList(),
)

/** Hands-on practical exercises plus the free code workbench. */
class PracticalViewModel(
    private val progress: ProgressSource, private val deck: DeckRepository, account: AccountRepository, private val tools: ToolsRepository, status: AppStatus,
) : ViewModel() {
    val uiState: StateFlow<PracticalUiState> = snapshotOf(progress, status).map { snap ->
        val all = snap.library.questions.filter { it.practical != null }
        val areas = all.map { it.area }.distinct().sorted()
        val ids = all.map { it.id }.toSet()
        PracticalUiState(all.sortedWith(compareBy<Q> { it.level }.thenBy { it.id }), areas,
            snap.state.plan.area.takeIf { it in areas } ?: ALL_TRACKS,
            snap.state.cards.filter { it.questionId in ids }.associate { it.questionId!! to it.box }, snap.saving)
    }.flowOn(Dispatchers.Default).let { stateOf(it, PracticalUiState()) }

    val code: StateFlow<CodeRunState> = combine(account.session, account.capabilities, tools.codeResult, tools.practicalRun, tools.languages) { session, caps, result, runFor, languages ->
        CodeRunState(session != null, caps.code, result, runFor, languages)
    }.let { stateOf(it, CodeRunState()) }

    fun exercise(id: String?): Q? = progress.question(id)?.takeIf { it.practical != null }
    fun assess(q: Q, solved: Boolean) = deck.assessPractical(q, solved)
    fun runTests(q: Q, code: String) = tools.runPractical(q, code)
    fun pollCode() = tools.pollCode()
    fun loadLanguages() = tools.loadLanguages()
    fun runCode(language: Int, code: String, stdin: String) = tools.runCode(language, code, stdin)
}
