package com.prepdesk.data

import com.prepdesk.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the learner's progress and the question bank on this device.
 * Every change is written through [OperationQueue] and published only after it is saved.
 * Progress is stored as one aggregate so backups, sync, and multi-feature changes stay atomic;
 * ViewModels reach it through the feature repositories in Repositories.kt, never directly.
 */
class ProgressStore(private val local: PrepRepository, private val queue: OperationQueue) : ProgressSource {
    private val _state = MutableStateFlow(AppState(cards = emptyList()))
    override val state: StateFlow<AppState> = _state.asStateFlow()
    private val _library = MutableStateFlow(Library(resources = STUDY_RESOURCES))
    override val library: StateFlow<Library> = _library.asStateFlow()
    private val _mock = MutableStateFlow<MockRun?>(null)
    /** The unfinished mock round, if any. */
    val mock: StateFlow<MockRun?> = _mock.asStateFlow()
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()
    private val _backup = MutableStateFlow<String?>(null)
    val backupDocument: StateFlow<String?> = _backup.asStateFlow()

    val current: AppState get() = _state.value
    override fun question(id: String?): Q? = id?.let { wanted -> _library.value.questions.firstOrNull { it.id == wanted } }

    override fun load() = queue.launch { loadNow() }

    internal suspend fun loadNow() {
        try {
            val loaded = local.initialize()
            _library.value = Library(local.questions, local.sources, local.resources())
            // A focus session that expired while the app was closed is counted once here.
            val next = loaded.finishFocus(nowMs()).let { if (it == loaded) it else it.dirty() }
            if (next != loaded) local.save(next)
            _state.value = next
            _mock.value = local.activeMock(); _ready.value = true; queue.dismissError()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _ready.value = false; queue.reportError("Could not load preparation data: ${e.message}. Your existing data has been retained.") }
    }

    private fun AppState.dirty() = copy(sync = sync.copy(dirty = true))

    fun update(f: (AppState) -> AppState) = queue.launch { save(f) }

    /** Applies a change and waits for it to be saved; returns false if it failed. */
    suspend fun updateAndWait(f: (AppState) -> AppState): Boolean = queue.run { save(f) }

    private suspend fun save(f: (AppState) -> AppState) {
        check(_ready.value)
        val next = f(_state.value).let { if (it == _state.value) it else it.dirty() }
        local.save(next)
        _state.value = next
    }

    // ---------- Mock interview rounds ----------

    fun startMock(area: String, level: Int) = queue.launch {
        if (!_ready.value || _mock.value != null) return@launch
        val questions = _library.value.questions
        val selected = selectMockCards(questions, _state.value.cards, area, level)
        check(selected.isNotEmpty()) { "No questions match this experience level. Select another level." }
        val id = maxOf(nowMs(), (_state.value.history.maxOfOrNull { it.id } ?: 0) + 1)
        val run = MockRun(id, nowMs(), area, selected, selected.map { card -> questions.firstOrNull { it.id == card.questionId } })
        local.saveRound(run); _mock.value = run
        _state.value = _state.value.dirty(); local.save(_state.value)
    }

    fun updateMock(f: (MockRun) -> MockRun) = queue.launch { saveMock(f) }

    internal suspend fun saveMock(f: (MockRun) -> MockRun) {
        val run = _mock.value ?: return
        val next = f(run)
        if (next != run) { local.saveRound(next); _mock.value = next; _state.value = _state.value.dirty(); local.save(_state.value) }
    }

    fun advanceMock(stage: Int, position: Int) = updateMock { run ->
        if (run.stage != stage || run.index != position) run else run.copy(stage = run.stage + 1,
            elapsed = if (run.stage == 0) ((120_000 - (run.deadline - nowMs())).coerceIn(0, 120_000) / 1000).toInt() else run.elapsed,
            deadline = nowMs() + 60_000)
    }

    fun assessMock(position: Int, solid: Boolean? = null) = queue.launch {
        val run = _mock.value ?: return@launch
        if (run.stage != 2 || run.index != position) return@launch
        val questions = _library.value.questions
        val attempt = run.assess(solid)
        var nextState = _state.value
        if (attempt.weak) {
            run.questions[run.index]?.let { q -> nextState = nextState.addQuestions(listOf(questions.firstOrNull { it.id == q.id } ?: q)) }
        }
        val card = nextState.cards.firstOrNull { it.questionId != null && it.questionId == attempt.questionId }
            ?: nextState.cards.firstOrNull { it.id == run.cards[run.index].id }
            ?: nextState.cards.firstOrNull { it.q == attempt.prompt && it.a == attempt.answer }
        nextState = if (card != null) nextState.rate(card.id, !attempt.weak) else nextState.logged()
        val next = run.copy(index = run.index + 1, stage = 0, draft = "", followupNotes = "", checked = emptySet(),
            deadline = nowMs() + 120_000, elapsed = 0, attempts = run.attempts + attempt, aiFeedback = null)
        val finished = next.index >= next.cards.size
        val completedAt = if (finished) nowMs() else null
        if (finished) nextState = nextState.copy(history = listOf(MockResult(run.id, run.startedAt, completedAt!!, run.area, next.attempts)) + nextState.history)
        nextState = nextState.dirty()
        local.saveMock(nextState, next, attempt, completedAt)
        _state.value = nextState; _mock.value = if (finished) null else next
    }

    fun cancelMock() = queue.launch {
        if (_mock.value == null) return@launch
        local.restore(ProgressBackup(state = _state.value.dirty()))
        _mock.value = null; queue.notify("Mock round ended. Completed history remains saved.")
    }

    // ---------- Backups and device data ----------

    /** Prepares a backup document and returns it once ready. */
    suspend fun prepareBackup(): String? = if (queue.run { _backup.value = encodeBackup(_state.value, _mock.value) }) _backup.value else null

    fun recoveryBackup() = queue.launch {
        _backup.value = local.recovery()
        queue.notify(if (_backup.value == null) "No recovery copy is available." else "Recovery copy is ready.")
    }

    fun restoreBackup(document: String) = queue.launch {
        val backup = decodeBackup(document, _library.value.questions)
        local.keepRecovery(encodeBackup(_state.value, _mock.value))
        local.restore(backup, _state.value.sync.copy(dirty = true))
        reload(); queue.notify("Backup restored. Your previous progress is retained as a recovery copy.")
    }

    // ---------- Operations used by the account and tools repositories ----------

    internal suspend fun clearNow() {
        local.restore(ProgressBackup(state = AppState(cards = emptyList())))
        local.clearRecovery(); reload(); _mock.value = null; _backup.value = null
    }

    internal suspend fun reload() { _state.value = local.readState(); _mock.value = local.activeMock() }

    internal fun syncDocument(): ProgressBackup = CONTENT_JSON.decodeFromString(encodeBackup(_state.value, _mock.value))

    internal suspend fun markSynced(meta: SyncMeta) { _state.value = _state.value.copy(sync = meta); local.save(_state.value) }

    internal suspend fun replaceWith(backup: ProgressBackup, meta: SyncMeta) { local.restore(backup, meta); reload() }

    /** Saves the current copy as a recovery backup before progress is replaced. */
    internal suspend fun keepRecoveryCopy() { _backup.value = encodeBackup(_state.value, _mock.value); local.keepRecovery(_backup.value!!) }

    internal suspend fun applyContent(pack: ContentPack) {
        local.publish(pack.questions, pack.sources, pack.resources)
        _library.value = Library(local.questions, local.sources, local.resources())
        _state.value = local.readState()
    }
}
