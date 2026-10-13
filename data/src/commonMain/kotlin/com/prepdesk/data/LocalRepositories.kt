package com.prepdesk.data

import com.prepdesk.domain.*
import kotlinx.coroutines.flow.StateFlow

// Each feature applies pure domain changes through the shared ProgressStore, which saves them in order.

class LocalDeckRepository(private val store: ProgressStore) : DeckRepository {
    override fun addQuestions(questions: List<Q>) = store.update { it.addQuestions(questions) }
    override fun rate(card: Card, recalled: Boolean) = store.update { it.rate(card.id, recalled) }
    override fun addCard(topic: String, prompt: String, answer: String) = store.update { it.addCard(topic, prompt, answer) }
    override fun deleteCard(card: Card) = store.update { it.copy(cards = it.cards - card) }
    override fun toggleBookmark(questionId: String) = store.update { it.toggleBookmark(questionId) }
    override fun assessPractical(q: Q, solved: Boolean) = store.update { it.assessPractical(q, solved) }
}

class LocalStudyRepository(private val store: ProgressStore) : StudyRepository {
    override suspend fun startStudy(area: String, level: Int, subtopic: String, selectedIds: List<String>?): StudySession? {
        val study = studySessionFor(store.library.value.questions, area, level, subtopic, selectedIds) ?: return null
        return study.takeIf { store.updateAndWait { it.copy(study = study) } }
    }
    override fun reveal(q: Q) = store.update { it.revealStudy(q) }
    override fun review(q: Q, confident: Boolean) = store.update { it.reviewStudy(q, confident) }
    override fun skip(q: Q) = store.update { it.skipStudy(q) }
    override fun updatePlan(change: (StudyPlan) -> StudyPlan) = store.update { it.copy(plan = change(it.plan)) }
}

class LocalMockRepository(private val store: ProgressStore) : MockRepository {
    override val mock: StateFlow<MockRun?> = store.mock
    override fun start(area: String, level: Int) = store.startMock(area, level)
    override fun setDraft(value: String) = store.updateMock { it.copy(draft = value) }
    override fun setFollowups(value: String) = store.updateMock { it.copy(followupNotes = value) }
    override fun applyTranscript(run: MockRun, text: String) = store.updateMock { it.withTranscript(run, text) }
    override fun setChecked(index: Int, checked: Boolean) = store.updateMock { it.copy(checked = if (checked) it.checked + index else it.checked - index) }
    override fun advance(run: MockRun) = store.advanceMock(run.stage, run.index)
    override fun assess(position: Int, solid: Boolean?) = store.assessMock(position, solid)
    override fun cancel() = store.cancelMock()
}

class LocalFocusRepository(private val store: ProgressStore) : FocusRepository {
    override fun toggle() = store.update { it.toggleFocus(nowMs()) }
    override fun reset() = store.update { it.copy(focus = FocusSession()) }
    override fun finishIfDue() = store.update { it.finishFocus(nowMs()) }
}

class LocalRewardsRepository(private val store: ProgressStore) : RewardsRepository {
    override fun recordRush(area: String, score: RushScore) = store.update { it.recordRush(area, score) }
    override fun recordSweep(day: String) = store.update { it.recordSweep(day) }
    override fun acknowledge(badgeIds: List<String>, level: Int) = store.update { it.acknowledgeRewards(badgeIds, level) }
}

class LocalBackupRepository(private val store: ProgressStore) : BackupRepository {
    override val backupDocument: StateFlow<String?> = store.backupDocument
    override suspend fun prepareBackup(): String? = store.prepareBackup()
    override fun recoveryBackup() = store.recoveryBackup()
    override fun restoreBackup(document: String) = store.restoreBackup(document)
}
