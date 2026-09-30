package pl.eclipse.app.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Rodzaj rekordu z Librusa (jedna wspólna tabela `records`, dane jako JSON modelu z `:core`). */
enum class RecordType { STUDENT, SUBJECT, LESSON, EVENT, HOMEWORK, GRADE, ATTENDANCE, NOTE, ANNOUNCEMENT, MESSAGE, LUCKY_NUMBER }

/**
 * Rekord z Librusa z polami wykrywania zmian (SPEC 4.4). Czasy w milisekundach od 1970 r.
 * `removedAt` — zniknął z Librusa (pokazujemy przekreślony przez 14 dni); `previousJson` — wersja sprzed zmiany.
 */
@Entity(tableName = "records", primaryKeys = ["type", "sourceKey"])
data class RecordEntity(
    val type: RecordType,
    val sourceKey: String,
    val json: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val changedAt: Long? = null,
    val previousJson: String? = null,
    val removedAt: Long? = null,
)

@Entity(tableName = "sync_runs")
data class SyncRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long,
    val success: Boolean,
    val demo: Boolean,
    /** Np. "GRADE: 42 (nowe 3), LESSON: 90". */
    val summary: String,
    /** Błędy bez danych wrażliwych, po jednym w linii. */
    val errors: String = "",
)

// Dane użytkownika — osobne tabele, synchronizacja ich nie dotyka.

@Entity(tableName = "subject_prefs")
data class SubjectPrefsEntity(
    @PrimaryKey val subjectKey: String,
    val color: Int? = null,
    val short: String? = null,
    val isDifficult: Boolean = false,
    val averageMethod: String = "AUTO",
    val hidden: Boolean = false,
)

@Entity(tableName = "custom_events")
data class CustomEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** ISO, np. "2026-10-05T16:00". */
    val start: String,
    val end: String,
    val allDay: Boolean = false,
    val color: Int? = null,
    val note: String = "",
    val subjectKey: String? = null,
    val templateId: Long? = null,
)

@Entity(tableName = "labels")
data class LabelEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val color: Int)

@Entity(tableName = "label_assignments")
data class LabelAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val labelId: Long,
    /** LESSON | SCHOOL_EVENT | CUSTOM_EVENT | SUBJECT_ALL_LESSONS */
    val target: String,
    /** Lekcja: "data|numer|przedmiot"; wydarzenie: sourceKey lub id; przedmiot: subjectKey. */
    val targetKey: String,
    val note: String = "",
)

@Entity(tableName = "event_templates")
data class EventTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val durationMinutes: Int,
    val color: Int? = null,
    val labelId: Long? = null,
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String,
    /** Trasa nawigacji do otwarcia po kliknięciu. */
    val route: String? = null,
    val postedAt: Long,
    val readAt: Long? = null,
    /** Powstało w godzinach ciszy — trafi do porannego podsumowania zamiast od razu na ekran. */
    val deferred: Boolean = false,
)

/** Proste znaczniki użytkownika: przeczytane (READ), ukryte powody Important (HIDDEN_REASON), oceny „pomiń” (SKIPPED_GRADE). */
@Entity(tableName = "user_flags", primaryKeys = ["kind", "key"])
data class UserFlagEntity(val kind: String, val key: String, val createdAt: Long = System.currentTimeMillis())

data class TypeCount(val type: RecordType, val count: Int)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE type = :type")
    fun observe(type: RecordType): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE type = :type")
    suspend fun all(type: RecordType): List<RecordEntity>

    @Upsert
    suspend fun upsert(records: List<RecordEntity>)

    @Query("DELETE FROM records WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun purgeRemovedBefore(before: Long)

    @Query("SELECT type, COUNT(*) AS count FROM records WHERE removedAt IS NULL GROUP BY type")
    fun counts(): Flow<List<TypeCount>>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun countAll(): Int

    @Query("DELETE FROM records")
    suspend fun clear()

    @Query("DELETE FROM sync_runs")
    suspend fun clearSyncRuns()
}

@Dao
interface SyncRunDao {
    @Insert
    suspend fun insert(run: SyncRunEntity)

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC LIMIT :limit")
    fun latest(limit: Int = 20): Flow<List<SyncRunEntity>>

    @Query("SELECT * FROM sync_runs ORDER BY startedAt DESC LIMIT :limit")
    suspend fun latestOnce(limit: Int): List<SyncRunEntity>
}

@Dao
interface UserDao {
    @Query("SELECT * FROM subject_prefs")
    fun subjectPrefs(): Flow<List<SubjectPrefsEntity>>

    @Upsert
    suspend fun upsertSubjectPrefs(prefs: SubjectPrefsEntity)

    @Insert
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("SELECT * FROM notifications ORDER BY postedAt DESC LIMIT 100")
    fun notifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE deferred = 1")
    suspend fun deferredNotifications(): List<NotificationEntity>

    @Query("UPDATE notifications SET deferred = 0 WHERE deferred = 1")
    suspend fun clearDeferred()

    @Query("SELECT * FROM subject_prefs")
    suspend fun subjectPrefsOnce(): List<SubjectPrefsEntity>

    @Query("UPDATE notifications SET readAt = :now WHERE readAt IS NULL")
    suspend fun markNotificationsRead(now: Long)

    @Query("SELECT * FROM labels ORDER BY id")
    fun labels(): Flow<List<LabelEntity>>

    @Upsert
    suspend fun upsertLabel(label: LabelEntity): Long

    @Query("SELECT COUNT(*) FROM labels")
    suspend fun labelCount(): Int

    @Query("SELECT * FROM label_assignments")
    fun assignments(): Flow<List<LabelAssignmentEntity>>

    @Insert
    suspend fun insertAssignment(assignment: LabelAssignmentEntity): Long

    @Delete
    suspend fun deleteAssignment(assignment: LabelAssignmentEntity)

    @Query("SELECT * FROM custom_events ORDER BY start")
    fun customEvents(): Flow<List<CustomEventEntity>>

    @Upsert
    suspend fun upsertCustomEvent(event: CustomEventEntity): Long

    @Delete
    suspend fun deleteCustomEvent(event: CustomEventEntity)

    @Query("SELECT * FROM event_templates ORDER BY id")
    fun templates(): Flow<List<EventTemplateEntity>>

    @Upsert
    suspend fun upsertTemplate(template: EventTemplateEntity): Long

    @Query("SELECT COUNT(*) FROM event_templates")
    suspend fun templateCount(): Int

    @Query("SELECT * FROM user_flags")
    fun flags(): Flow<List<UserFlagEntity>>

    @Upsert
    suspend fun setFlag(flag: UserFlagEntity)

    @Query("DELETE FROM user_flags WHERE kind = :kind AND `key` = :key")
    suspend fun clearFlag(kind: String, key: String)
}

@Database(
    entities = [
        RecordEntity::class, SyncRunEntity::class, SubjectPrefsEntity::class, CustomEventEntity::class,
        LabelEntity::class, LabelAssignmentEntity::class, EventTemplateEntity::class, NotificationEntity::class,
        UserFlagEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class EclipseDatabase : RoomDatabase() {
    abstract fun records(): RecordDao
    abstract fun syncRuns(): SyncRunDao
    abstract fun user(): UserDao
}
