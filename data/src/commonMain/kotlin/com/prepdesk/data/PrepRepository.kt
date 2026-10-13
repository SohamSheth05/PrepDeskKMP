package com.prepdesk.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import com.prepdesk.domain.*

class PrepRepository(private val database: PrepDatabase, private val settings: Settings) {
    private val dao = database.dao()
    var questions: List<Q> = emptyList(); private set
    var sources: List<Source> = emptyList(); private set

    suspend fun initialize(): AppState {
        val remoteSources = dao.metadata("remote_sources")?.let { CONTENT_JSON.decodeFromString<List<Source>>(it) }.orEmpty()
        publish(QUESTIONS, (SOURCES + remoteSources).associateBy { it.id }.values.toList())
        if (dao.metadata("user_initialized") == null) {
            // The legacy value remains untouched as a recovery copy. Invalid data fails visibly.
            val old = settings.getStringOrNull("state")
            val legacy = if (old.isNullOrBlank()) AppState() else decodeLegacyState(old)
            val normalized = migrateLegacy(legacy, questions)
            dao.initializeUser(cardRows(normalized), bookmarkRows(normalized), activityRows(normalized), metadataRows(normalized))
        }
        return readState()
    }

    suspend fun publish(pack: List<Q>, sourcePack: List<Source>, resources: List<ResourceRecord>? = null) {
        require(pack.map { it.id }.distinct().size == pack.size) { "Duplicate content IDs" }
        require(sourcePack.map { it.id }.distinct().size == sourcePack.size) { "Duplicate source IDs" }
        val sourceIds = sourcePack.map { it.id }.toSet()
        pack.forEach { q ->
            require(q.id.isNotBlank() && q.q.isNotBlank() && q.a.isNotBlank() && q.level in 1..4 && q.version > 0)
            require(q.sourceIds.all { it in sourceIds }) { "Unknown source for ${q.id}" }
        }
        // Encode only questions newer than the stored copy; on an ordinary launch nothing has changed.
        // The transaction checks versions again, so a downgrade is still never written.
        val stored = dao.questionVersions().associate { it.id to it.version }
        val changed = pack.filter { it.version > (stored[it.id] ?: 0) }
        val sourceRows = sourcePack.map { SourceRow(it.id, it.name, it.author, it.url, it.license, it.kind) }
        val metadata = resources?.let { listOf(MetadataRow("resources", CONTENT_JSON.encodeToString(it)), MetadataRow("remote_sources", CONTENT_JSON.encodeToString(sourcePack))) }.orEmpty()
        if (changed.isNotEmpty() || metadata.isNotEmpty() || !dao.sources().containsAll(sourceRows)) {
            dao.publish(changed.map { QuestionRow(it.id, it.area, it.level, it.round, it.version, CONTENT_JSON.encodeToString(it)) },
                sourceRows, changed.flatMap { q -> q.sourceIds.map { QuestionSourceRow(q.id, it) } }, metadata)
        }
        questions = loadQuestions()
        sources = dao.sources().map { Source(it.id, it.name, it.author, it.url, it.license, it.kind) }
    }

    /**
     * The stored questions, in the order of [PrepDao.questions]. A stored question whose version matches the
     * bundled one has the same content, because content changes always increase the version, so the bundled
     * object is used and only questions replaced by a content update are read and decoded.
     */
    private suspend fun loadQuestions(): List<Q> {
        val bundled = QUESTIONS.associateBy { it.id }
        val stored = dao.questionVersions()
        val replaced = stored.filter { bundled[it.id]?.version != it.version }.map { it.id }
        // SQLite limits the number of bound parameters in one query.
        val updates = replaced.chunked(500).flatMap { dao.questionsById(it) }.associate { it.id to CONTENT_JSON.decodeFromString<Q>(it.document) }
        return stored.mapNotNull { updates[it.id] ?: bundled[it.id] }.sortedWith(compareBy<Q> { it.area }.thenBy { it.id })
    }

    suspend fun readState(): AppState {
        val bank = questions.associateBy { it.id }
        val history = dao.attempts().groupBy { it.sessionId }
        return AppState(
            cards = dao.cards().map { row ->
                val q = bank[row.questionId]
                Card(row.id, q?.area ?: row.topic, q?.q ?: row.prompt, q?.a ?: row.answer, row.box, row.due, row.questionId)
            },
            days = dao.activity().associate { it.date to it.count },
            pomos = dao.metadata("pomos")?.toInt() ?: 0,
            plan = dao.metadata("plan")?.let { CONTENT_JSON.decodeFromString<StudyPlan>(it) } ?: StudyPlan(),
            bookmarks = dao.bookmarks().map { it.questionId }.toSet(),
            study = dao.metadata("study")?.let { CONTENT_JSON.decodeFromString<StudySession?>(it) },
            focus = dao.metadata("focus")?.let { CONTENT_JSON.decodeFromString<FocusSession>(it) } ?: FocusSession(),
            sync = dao.metadata("sync")?.let { CONTENT_JSON.decodeFromString<SyncMeta>(it) } ?: SyncMeta(),
            arcade = dao.metadata("arcade")?.let { CONTENT_JSON.decodeFromString<Arcade>(it) } ?: Arcade(),
            history = dao.sessions().filter { it.completedAt != null }.map { s ->
                MockResult(s.id, s.startedAt, s.completedAt!!, s.area, history[s.id].orEmpty().map { CONTENT_JSON.decodeFromString<MockAttempt>(it.document) })
            },
        )
    }

    suspend fun activeMock(): MockRun? = dao.sessions().firstOrNull { it.completedAt == null && it.activeDocument != null }
        ?.activeDocument?.let { CONTENT_JSON.decodeFromString<MockRun>(it) }

    suspend fun save(state: AppState) = dao.saveUser(cardRows(state), bookmarkRows(state), activityRows(state), metadataRows(state))
    suspend fun keepRecovery(document: String) = dao.putMetadata(listOf(MetadataRow("recovery", document)))
    suspend fun recovery(): String? = dao.metadata("recovery")?.takeIf { it.isNotBlank() }
    suspend fun clearRecovery() = dao.putMetadata(listOf(MetadataRow("recovery", "")))
    suspend fun resources(): List<StudyResource> = dao.metadata("resources")?.let { CONTENT_JSON.decodeFromString<List<ResourceRecord>>(it).map { it.resource() } } ?: STUDY_RESOURCES
    suspend fun saveResources(resources: List<ResourceRecord>) = dao.putMetadata(listOf(MetadataRow("resources", CONTENT_JSON.encodeToString(resources))))

    suspend fun saveRound(run: MockRun) = dao.putSession(SessionRow(run.id, run.startedAt, null, run.area, CONTENT_JSON.encodeToString(run)))

    suspend fun restore(backup: ProgressBackup, sync: SyncMeta = SyncMeta()) {
        val state = backup.state.copy(sync = sync)
        val sessions = state.history.map { SessionRow(it.id, it.startedAt, it.completedAt, it.area, null) } +
            listOfNotNull(backup.activeMock?.let { SessionRow(it.id, it.startedAt, null, it.area, CONTENT_JSON.encodeToString(it)) })
        require(sessions.map { it.id }.distinct().size == sessions.size) { "Duplicate mock session IDs" }
        val attempts = state.history.flatMap { result -> result.attempts.map { AttemptRow(result.id, it.position, it.questionId, CONTENT_JSON.encodeToString(it)) } } +
            backup.activeMock?.let { run -> run.attempts.map { AttemptRow(run.id, it.position, it.questionId, CONTENT_JSON.encodeToString(it)) } }.orEmpty()
        dao.restore(cardRows(state), bookmarkRows(state), activityRows(state), metadataRows(state), sessions, attempts)
    }

    suspend fun saveMock(state: AppState, run: MockRun, attempt: MockAttempt? = null, completedAt: Long? = null) {
        val session = SessionRow(run.id, run.startedAt, completedAt, run.area, if (completedAt == null) CONTENT_JSON.encodeToString(run) else null)
        dao.saveMock(cardRows(state), bookmarkRows(state), activityRows(state), metadataRows(state), session,
            attempt?.let { AttemptRow(run.id, it.position, it.questionId, CONTENT_JSON.encodeToString(it)) })
    }

    private fun cardRows(s: AppState) = s.cards.mapIndexed { index, c ->
        // Linked cards resolve their text from the content table; custom cards own their text.
        CardRow(c.id, c.questionId, if (c.questionId == null) c.topic else "", if (c.questionId == null) c.q else "",
            if (c.questionId == null) c.a else "", c.box, c.due, index)
    }
    private fun bookmarkRows(s: AppState) = s.bookmarks.map(::BookmarkRow)
    private fun activityRows(s: AppState) = s.days.map { ActivityRow(it.key, it.value) }
    private fun metadataRows(s: AppState) = listOf(MetadataRow("pomos", s.pomos.toString()), MetadataRow("plan", CONTENT_JSON.encodeToString(s.plan)), MetadataRow("study", CONTENT_JSON.encodeToString(s.study)), MetadataRow("focus", CONTENT_JSON.encodeToString(s.focus)), MetadataRow("sync", CONTENT_JSON.encodeToString(s.sync)), MetadataRow("arcade", CONTENT_JSON.encodeToString(s.arcade)))
}

fun migrateLegacy(state: AppState, bank: List<Q>): AppState {
    require(state.cards.map { it.id }.distinct().size == state.cards.size) { "Duplicate legacy card IDs; existing data retained" }
    val byPrompt = bank.associateBy { it.q }
    val byId = bank.associateBy { it.id }
    return state.copy(
        cards = state.cards.map { c ->
            val q = c.questionId?.let { byId[it] } ?: byPrompt[c.q]?.takeIf { it.a == c.a }
            c.copy(questionId = q?.id, box = c.box.coerceIn(0, 4))
        },
        plan = state.plan.copy(lv = state.plan.lv.coerceIn(1, 4), days = state.plan.days.coerceIn(1, 120)),
        bookmarks = state.bookmarks.filter { it in byId }.toSet(),
    )
}

// Old JSON omitted default values. Preserve its original implicit area (All).
@kotlinx.serialization.Serializable
private data class LegacyPlan(val lv: Int = 2, val area: String = "All", val days: Int = 14)
@kotlinx.serialization.Serializable
private data class LegacyState(val cards: List<Card> = seed(), val days: Map<String, Int> = emptyMap(), val pomos: Int = 0, val plan: LegacyPlan = LegacyPlan())
fun decodeLegacyState(document: String): AppState {
    val old = CONTENT_JSON.decodeFromString<LegacyState>(document)
    return AppState(old.cards, old.days, old.pomos, StudyPlan(old.plan.lv, old.plan.area, old.plan.days))
}
