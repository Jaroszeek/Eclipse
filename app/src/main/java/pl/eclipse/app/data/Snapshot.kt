package pl.eclipse.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import pl.eclipse.core.model.Announcement
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.Homework
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LuckyNumber
import pl.eclipse.core.model.Message
import pl.eclipse.core.model.Note
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject

/** Wszystkie dane szkolne z bazy naraz — wspólne wejście dla ekranów (ViewModele liczą z nich stan przez `:core`). */
data class SchoolSnapshot(
    val student: StudentInfo? = null,
    val subjects: List<Subject> = emptyList(),
    val lessons: List<Stored<Lesson>> = emptyList(),
    val events: List<Stored<SchoolEvent>> = emptyList(),
    val homework: List<Stored<Homework>> = emptyList(),
    val grades: List<Stored<Grade>> = emptyList(),
    val attendance: List<Attendance> = emptyList(),
    val notes: List<Stored<Note>> = emptyList(),
    val announcements: List<Stored<Announcement>> = emptyList(),
    val messages: List<Stored<Message>> = emptyList(),
    val luckyNumbers: List<LuckyNumber> = emptyList(),
    val lastSync: SyncRunEntity? = null,
    val recentRuns: List<SyncRunEntity> = emptyList(),
    val lastSuccessAt: Long? = null,
) {
    val isEmpty get() = subjects.isEmpty() && lessons.isEmpty()
}

/** Dane użytkownika (osobne tabele — synchronizacja ich nie dotyka). */
data class UserData(
    val prefs: Map<String, SubjectPrefsEntity> = emptyMap(),
    val labels: List<LabelEntity> = emptyList(),
    val assignments: List<LabelAssignmentEntity> = emptyList(),
    val customEvents: List<CustomEventEntity> = emptyList(),
    val templates: List<EventTemplateEntity> = emptyList(),
    val flags: Set<Pair<String, String>> = emptySet(),
    val notifications: List<NotificationEntity> = emptyList(),
) {
    fun flagged(kind: String, key: String) = kind to key in flags
    fun keys(kind: String) = flags.filter { it.first == kind }.map { it.second }.toSet()
}

object Flags {
    const val READ = "READ"
    const val HIDDEN_REASON = "HIDDEN_REASON"
    const val SKIPPED_GRADE = "SKIPPED_GRADE"
}

fun SchoolRepository.snapshot(db: EclipseDatabase, scope: CoroutineScope): SharedFlow<SchoolSnapshot> {
    val flows: List<Flow<List<*>>> = listOf(
        observe(RecordType.STUDENT, StudentInfo.serializer()),
        observe(RecordType.SUBJECT, Subject.serializer()),
        observe(RecordType.LESSON, Lesson.serializer()),
        observe(RecordType.EVENT, SchoolEvent.serializer()),
        observe(RecordType.HOMEWORK, Homework.serializer()),
        observe(RecordType.GRADE, Grade.serializer()),
        observe(RecordType.ATTENDANCE, Attendance.serializer()),
        observe(RecordType.NOTE, Note.serializer()),
        observe(RecordType.ANNOUNCEMENT, Announcement.serializer()),
        observe(RecordType.MESSAGE, Message.serializer()),
        observe(RecordType.LUCKY_NUMBER, LuckyNumber.serializer()),
        db.syncRuns().latest(20),
    )
    return combine(flows) { arrays ->
        @Suppress("UNCHECKED_CAST")
        fun <T> at(i: Int) = arrays[i] as List<Stored<T>>
        fun <T> active(i: Int) = at<T>(i).filter { it.removedAt == null }.map { it.value }
        val runs = arrays[11].filterIsInstance<SyncRunEntity>()
        SchoolSnapshot(
            student = active<StudentInfo>(0).firstOrNull(),
            subjects = active<Subject>(1).sortedBy { it.name.lowercase() },
            lessons = at(2),
            events = at(3),
            homework = at(4),
            grades = at(5),
            attendance = active(6),
            notes = at(7),
            announcements = at(8),
            messages = at(9),
            luckyNumbers = active<LuckyNumber>(10).sortedByDescending { it.date },
            lastSync = runs.firstOrNull(),
            recentRuns = runs,
            lastSuccessAt = runs.firstOrNull { it.success }?.finishedAt,
        )
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}

fun userData(db: EclipseDatabase, scope: CoroutineScope): SharedFlow<UserData> = combine(
    listOf(
        db.user().subjectPrefs(), db.user().labels(), db.user().assignments(), db.user().customEvents(),
        db.user().templates(), db.user().flags(), db.user().notifications(),
    ),
) { a ->
    UserData(
        prefs = a[0].filterIsInstance<SubjectPrefsEntity>().associateBy { it.subjectKey },
        labels = a[1].filterIsInstance<LabelEntity>(),
        assignments = a[2].filterIsInstance<LabelAssignmentEntity>(),
        customEvents = a[3].filterIsInstance<CustomEventEntity>(),
        templates = a[4].filterIsInstance<EventTemplateEntity>(),
        flags = a[5].filterIsInstance<UserFlagEntity>().map { it.kind to it.key }.toSet(),
        notifications = a[6].filterIsInstance<NotificationEntity>(),
    )
}.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

/** Domyślne etykiety i szablony (SPEC 12.8), dodawane raz przy pierwszym uruchomieniu. */
suspend fun ensureUserDefaults(db: EclipseDatabase) {
    val user = db.user()
    if (user.labelCount() == 0) {
        listOf("Ważne" to 0xFFEF4444, "Przynieść" to 0xFF3B82F6, "Powtórzyć" to 0xFFEAB308, "Zapytać nauczyciela" to 0xFF14B8A6)
            .forEach { (name, color) -> user.upsertLabel(LabelEntity(name = name, color = color.toInt())) }
    }
    if (user.templateCount() == 0) {
        listOf("Nauka" to 60, "Korepetycje" to 60, "Trening" to 90)
            .forEach { (name, minutes) -> user.upsertTemplate(EventTemplateEntity(name = name, durationMinutes = minutes)) }
    }
}

private val Context.visitsDataStore by preferencesDataStore("visits")

/** Kiedy ostatnio oglądano dany ekran — do „Nowe od ostatniej wizyty” (SPEC 4.4). */
class VisitStore(private val context: Context) {
    fun observe(screen: String): Flow<Long> = context.visitsDataStore.data.map { it[longPreferencesKey(screen)] ?: 0L }

    /** Zwraca poprzednią wizytę i zapisuje bieżącą. */
    suspend fun visit(screen: String): Long {
        val key = longPreferencesKey(screen)
        val previous = context.visitsDataStore.data.map { it[key] ?: 0L }.first()
        context.visitsDataStore.edit { it[key] = System.currentTimeMillis() }
        return previous
    }
}
