package com.prepdesk.domain

import kotlinx.coroutines.flow.StateFlow

// Contracts the UI depends on. The :data module implements them (LocalRepositories.kt), writing through
// one ProgressStore, so a change spanning features (finishing a mock answer updates the deck, history,
// and the round) is still saved in a single transaction. Tests substitute fakes for any of these.

/** Read-only progress and question bank that every feature renders from. */
interface ProgressSource {
    /** False until saved progress has loaded, or after loading failed. */
    val ready: StateFlow<Boolean>
    val state: StateFlow<AppState>
    val library: StateFlow<Library>
    fun question(id: String?): Q?
    /** Loads saved progress; failures are reported through [AppStatus.error]. */
    fun load()
}

/** Save status and user-visible notices for queued work. */
interface AppStatus {
    /** Number of queued or running operations; screens disable conflicting actions while it is above zero. */
    val pending: StateFlow<Int>
    val error: StateFlow<String?>
    /** The latest informational notice, such as "Progress synced." */
    val message: StateFlow<String?>
    fun reportError(message: String)
    fun dismissError()
}

/** Revision cards, spaced-repetition ratings, and bookmarks. */
interface DeckRepository {
    fun addQuestions(questions: List<Q>)
    fun rate(card: Card, recalled: Boolean)
    fun addCard(topic: String, prompt: String, answer: String)
    fun deleteCard(card: Card)
    fun toggleBookmark(questionId: String)
    fun assessPractical(q: Q, solved: Boolean)
}

/** Study quests and the preparation plan. */
interface StudyRepository {
    /** Starts a quest and returns it once saved, or null when nothing matches or saving failed. */
    suspend fun startStudy(area: String, level: Int, subtopic: String, selectedIds: List<String>? = null): StudySession?
    fun reveal(q: Q)
    fun review(q: Q, confident: Boolean)
    fun skip(q: Q)
    fun updatePlan(change: (StudyPlan) -> StudyPlan)
}

/** The unfinished mock interview round, if any, and its stages. */
interface MockRepository {
    val mock: StateFlow<MockRun?>
    fun start(area: String, level: Int)
    fun setDraft(value: String)
    fun setFollowups(value: String)
    /** Applies a transcript only if the learner is still on the same question and stage as [run]. */
    fun applyTranscript(run: MockRun, text: String)
    fun setChecked(index: Int, checked: Boolean)
    fun advance(run: MockRun)
    fun assess(position: Int, solid: Boolean? = null)
    fun cancel()
}

/** The Pomodoro focus timer. */
interface FocusRepository {
    fun toggle()
    fun reset()
    /** Counts a focus session whose deadline has passed. */
    fun finishIfDue()
}

/** Game results, daily challenge sweeps, and acknowledged rewards. */
interface RewardsRepository {
    fun recordRush(area: String, score: RushScore)
    /** Records that every daily challenge was completed on [day]; repeated calls have no effect. */
    fun recordSweep(day: String)
    fun acknowledge(badgeIds: List<String>, level: Int)
}

/** Portable backups and the recovery copy kept before progress is replaced. */
interface BackupRepository {
    val backupDocument: StateFlow<String?>
    /** Prepares a backup document and returns it once ready, or null if that failed. */
    suspend fun prepareBackup(): String?
    fun recoveryBackup()
    fun restoreBackup(document: String)
}

/** Sign-in, cloud sync with conflict resolution, account deletion, and remote content updates. */
interface AccountRepository {
    val session: StateFlow<AuthSession?>
    val capabilities: StateFlow<ServiceCapabilities>
    val conflict: StateFlow<CloudProgress?>
    val otpSent: StateFlow<Boolean>
    val onlineConfigured: Boolean
    fun restoreSession()
    fun sendCode(email: String)
    fun verifyCode(email: String, code: String)
    fun signOut()
    fun refreshCapabilities()
    fun synchronize()
    fun resolveConflict(useCloud: Boolean)
    fun deleteAccount()
    fun clearDevice()
    fun updateContent()
}

/** Optional online tools: AI answer coaching, speech transcription, and code execution. */
interface ToolsRepository {
    val codeResult: StateFlow<CodeResult?>
    val languages: StateFlow<List<CodeLanguage>>
    /** The practical exercise whose tests produced [codeResult], or null for the free workbench. */
    val practicalRun: StateFlow<String?>
    fun requestFeedback()
    fun transcribe(audio: ByteArray, apply: (String) -> Unit)
    fun loadLanguages()
    fun runCode(language: Int, code: String, stdin: String)
    fun runPractical(q: Q, code: String)
    fun pollCode()
}
