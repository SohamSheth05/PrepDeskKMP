package com.prepdesk.data

import androidx.room.*

@Entity(tableName = "questions", indices = [Index(value = ["area", "level", "round"])])
data class QuestionRow(@PrimaryKey val id: String, val area: String, val level: Int, val round: String, val version: Int, val document: String)

@Entity(tableName = "sources")
data class SourceRow(@PrimaryKey val id: String, val name: String, val author: String, val url: String, val license: String, val kind: String)

@Entity(tableName = "question_sources", primaryKeys = ["questionId", "sourceId"], indices = [Index("sourceId")], foreignKeys = [
    ForeignKey(entity = QuestionRow::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SourceRow::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.RESTRICT),
])
data class QuestionSourceRow(val questionId: String, val sourceId: String)

@Entity(tableName = "deck_cards", indices = [Index("questionId"), Index("due")], foreignKeys = [
    ForeignKey(entity = QuestionRow::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.RESTRICT),
])
data class CardRow(@PrimaryKey val id: Long, val questionId: String?, val topic: String, val prompt: String, val answer: String, val box: Int, val due: Long, val position: Int)

@Entity(tableName = "bookmarks", foreignKeys = [
    ForeignKey(entity = QuestionRow::class, parentColumns = ["id"], childColumns = ["questionId"], onDelete = ForeignKey.CASCADE),
])
data class BookmarkRow(@PrimaryKey val questionId: String)

@Entity(tableName = "activity")
data class ActivityRow(@PrimaryKey val date: String, val count: Int)

@Entity(tableName = "metadata")
data class MetadataRow(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "mock_sessions", indices = [Index("completedAt")])
data class SessionRow(@PrimaryKey val id: Long, val startedAt: Long, val completedAt: Long?, val area: String, val activeDocument: String?)

@Entity(tableName = "mock_attempts", primaryKeys = ["sessionId", "position"], indices = [Index("questionId")], foreignKeys = [
    ForeignKey(entity = SessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
])
data class AttemptRow(val sessionId: Long, val position: Int, val questionId: String?, val document: String)

/** A question's identity without its document, so version checks never read or decode content. */
data class QuestionVersion(val id: String, val version: Int)

@Dao
abstract class PrepDao {
    @Query("SELECT * FROM questions ORDER BY area, id") abstract suspend fun questions(): List<QuestionRow>
    @Query("SELECT id, version FROM questions") abstract suspend fun questionVersions(): List<QuestionVersion>
    @Query("SELECT * FROM questions WHERE id IN (:ids)") abstract suspend fun questionsById(ids: List<String>): List<QuestionRow>
    @Query("SELECT * FROM sources ORDER BY id") abstract suspend fun sources(): List<SourceRow>
    @Query("SELECT * FROM deck_cards ORDER BY position") abstract suspend fun cards(): List<CardRow>
    @Query("SELECT * FROM bookmarks") abstract suspend fun bookmarks(): List<BookmarkRow>
    @Query("SELECT * FROM activity") abstract suspend fun activity(): List<ActivityRow>
    @Query("SELECT value FROM metadata WHERE `key` = :key") abstract suspend fun metadata(key: String): String?
    @Query("SELECT * FROM mock_sessions ORDER BY startedAt DESC") abstract suspend fun sessions(): List<SessionRow>
    @Query("SELECT * FROM mock_attempts ORDER BY sessionId, position") abstract suspend fun attempts(): List<AttemptRow>
    @Upsert abstract suspend fun putQuestions(rows: List<QuestionRow>)
    @Upsert abstract suspend fun putSources(rows: List<SourceRow>)
    @Upsert abstract suspend fun putLinks(rows: List<QuestionSourceRow>)
    @Upsert abstract suspend fun putCards(rows: List<CardRow>)
    @Upsert abstract suspend fun putBookmarks(rows: List<BookmarkRow>)
    @Upsert abstract suspend fun putActivity(rows: List<ActivityRow>)
    @Upsert abstract suspend fun putMetadata(rows: List<MetadataRow>)
    @Upsert abstract suspend fun putSession(row: SessionRow)
    @Upsert abstract suspend fun putAttempt(row: AttemptRow)
    @Query("DELETE FROM deck_cards") abstract suspend fun clearCards()
    @Query("DELETE FROM bookmarks") abstract suspend fun clearBookmarks()
    @Query("DELETE FROM activity") abstract suspend fun clearActivity()
    @Query("DELETE FROM mock_attempts") abstract suspend fun clearAttempts()
    @Query("DELETE FROM mock_sessions") abstract suspend fun clearSessions()
    @Query("DELETE FROM question_sources WHERE questionId = :id") abstract suspend fun clearLinks(id: String)

    // One content transaction; stable question IDs never replace deck progress or attempt snapshots.
    @Transaction
    open suspend fun publish(questions: List<QuestionRow>, sources: List<SourceRow>, links: List<QuestionSourceRow>, metadata: List<MetadataRow> = emptyList()) {
        putSources(sources)
        val versions = questionVersions().associate { it.id to it.version }
        val changed = questions.filter { it.version > (versions[it.id] ?: 0) }
        putQuestions(changed)
        changed.forEach { clearLinks(it.id) }
        val ids = changed.map { it.id }.toSet()
        putLinks(links.filter { it.questionId in ids })
        putMetadata(metadata)
    }

    @Transaction
    open suspend fun saveUser(cards: List<CardRow>, bookmarks: List<BookmarkRow>, activity: List<ActivityRow>, metadata: List<MetadataRow>) {
        clearCards(); putCards(cards)
        clearBookmarks(); putBookmarks(bookmarks)
        clearActivity(); putActivity(activity)
        putMetadata(metadata)
    }

    @Transaction
    open suspend fun initializeUser(cards: List<CardRow>, bookmarks: List<BookmarkRow>, activity: List<ActivityRow>, metadata: List<MetadataRow>) {
        if (this.metadata("user_initialized") == null) {
            saveUser(cards, bookmarks, activity, metadata)
            putMetadata(listOf(MetadataRow("user_initialized", "1")))
        }
    }

    @Transaction
    open suspend fun saveMock(cards: List<CardRow>, bookmarks: List<BookmarkRow>, activity: List<ActivityRow>, metadata: List<MetadataRow>, session: SessionRow, attempt: AttemptRow?) {
        saveUser(cards, bookmarks, activity, metadata)
        putSession(session)
        if (attempt != null) putAttempt(attempt)
    }

    @Transaction
    open suspend fun restore(cards: List<CardRow>, bookmarks: List<BookmarkRow>, activity: List<ActivityRow>, metadata: List<MetadataRow>, sessions: List<SessionRow>, attempts: List<AttemptRow>) {
        saveUser(cards, bookmarks, activity, metadata)
        clearAttempts(); clearSessions()
        sessions.forEach { putSession(it) }; attempts.forEach { putAttempt(it) }
    }
}

@Database(entities = [QuestionRow::class, SourceRow::class, QuestionSourceRow::class, CardRow::class, BookmarkRow::class,
    ActivityRow::class, MetadataRow::class, SessionRow::class, AttemptRow::class], version = 1, exportSchema = true)
@ConstructedBy(PrepDatabaseConstructor::class)
abstract class PrepDatabase : RoomDatabase() { abstract fun dao(): PrepDao }

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object PrepDatabaseConstructor : RoomDatabaseConstructor<PrepDatabase> { override fun initialize(): PrepDatabase }
