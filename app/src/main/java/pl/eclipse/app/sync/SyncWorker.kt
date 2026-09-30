package pl.eclipse.app.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import pl.eclipse.app.container
import pl.eclipse.app.data.JSON
import pl.eclipse.app.data.RecordEntity
import pl.eclipse.app.data.RecordType
import pl.eclipse.app.data.SyncRunEntity
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
import pl.eclipse.core.source.DataSource
import pl.eclipse.core.source.librus.LibrusException
import pl.eclipse.core.sync.Changes
import pl.eclipse.core.sync.detectChanges
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Wynik zapisu jednego rodzaju danych: zmiany i nowe wartości (do powiadomień). */
class TypeResult<T>(val changes: Changes, val items: Map<String, T>, val previous: Map<String, T>)

/** Wyniki całej synchronizacji — z nich powstają powiadomienia. */
class SyncOutcome(
    val firstSync: Boolean,
    val grades: TypeResult<Grade>?,
    val events: TypeResult<SchoolEvent>?,
    val lessons: TypeResult<Lesson>?,
    val notes: TypeResult<Note>?,
    val announcements: TypeResult<Announcement>?,
    val messages: TypeResult<Message>?,
    val luckyNumber: LuckyNumber?,
)

/**
 * Synchronizacja (SPEC 4.3): pobierz przez [DataSource] → zapisz (upsert) → wykryj zmiany → powiadomienia.
 * Błąd jednego zbioru nie przerywa reszty; każde uruchomienie trafia do `sync_runs`.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container = context.container
    private val db = container.database

    override suspend fun doWork(): Result = SYNC_LOCK.withLock {
        val settings = container.settings.current()
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val hour = LocalTime.now(WARSAW).hour
        if (!manual && hour !in settings.syncFromHour until settings.syncToHour) return Result.success()

        val started = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        val summary = mutableListOf<String>()
        val source = container.dataSource(settings.demoMode)
        if (!settings.demoMode) {
            val credentials = container.credentials.read()
            if (credentials == null) {
                record(started, false, settings.demoMode, "", "Brak danych logowania — zaloguj się w aplikacji.")
                return Result.failure()
            }
            try {
                source.login(credentials.email, credentials.password)
            } catch (e: LibrusException) {
                record(started, false, false, "", e.message.orEmpty())
                return afterFailure()
            } catch (e: IOException) {
                record(started, false, false, "", "Brak połączenia z Librusem (${e.javaClass.simpleName}).")
                return afterFailure()
            }
        }

        val firstSync = db.records().countAll() == 0
        val today = LocalDate.now(WARSAW)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeksFrom = monday.minusWeeks(1)
        val weeksTo = monday.plusWeeks(2).minusDays(1)
        val eventsFrom = today.minusMonths(1).withDayOfMonth(1)
        val eventsTo = today.plusMonths(3).withDayOfMonth(1).minusDays(1)

        suspend fun <T> sync(
            type: RecordType,
            serializer: KSerializer<T>,
            key: (T) -> String,
            inScope: (T) -> Boolean = { true },
            fetch: () -> List<T>,
        ): TypeResult<T>? = try {
            store(type, fetch(), serializer, key, inScope).also { r ->
                summary += "$type: ${r.items.size}" + if (r.changes.isEmpty) "" else
                    " (nowe ${r.changes.added.size}, zmienione ${r.changes.changed.size}, usunięte ${r.changes.removed.size})"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Komunikaty spoza LibrusException mogą zawierać fragment odpowiedzi — zapisujemy tylko nazwę błędu.
            errors += "$type: " + if (e is LibrusException) e.message else e.javaClass.simpleName
            null
        }

        sync(RecordType.STUDENT, StudentInfo.serializer(), { "student" }) { listOf(source.student()) }
        sync(RecordType.SUBJECT, Subject.serializer(), Subject::sourceKey) { source.subjects() }
        val lessons = sync(RecordType.LESSON, Lesson.serializer(), Lesson::sourceKey, { it.date in weeksFrom..weeksTo }) {
            (0L..2L).flatMap { source.timetable(weeksFrom.plusWeeks(it)) }
        }
        val events = sync(RecordType.EVENT, SchoolEvent.serializer(), SchoolEvent::sourceKey, { it.date in eventsFrom..eventsTo }) {
            source.events(eventsFrom, eventsTo)
        }
        sync(RecordType.HOMEWORK, Homework.serializer(), Homework::sourceKey, { it.dueDate >= today.minusDays(7) }) {
            source.homework(today.minusDays(7), today.plusDays(30))
        }
        val grades = sync(RecordType.GRADE, Grade.serializer(), Grade::sourceKey) { source.grades() }
        sync(RecordType.ATTENDANCE, Attendance.serializer(), Attendance::sourceKey) { source.attendance() }
        val lastSuccess = db.syncRuns().latestOnce(20).firstOrNull { it.success }?.startedAt?.let(java.time.Instant::ofEpochMilli)
        val notes = sync(RecordType.NOTE, Note.serializer(), Note::sourceKey) { source.notes(lastSuccess) }
        val announcements = sync(RecordType.ANNOUNCEMENT, Announcement.serializer(), Announcement::sourceKey) {
            source.announcements(lastSuccess)
        }
        // Wiadomości przychodzą tylko nowe, więc brak na liście nie oznacza usunięcia.
        val messages = sync(RecordType.MESSAGE, Message.serializer(), Message::sourceKey, { false }) { source.messages(lastSuccess) }
        val lucky = sync(RecordType.LUCKY_NUMBER, LuckyNumber.serializer(), { it.date.toString() }, { false }) {
            listOfNotNull(source.luckyNumber())
        }

        db.records().purgeRemovedBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(REMOVED_KEEP_DAYS))
        val success = errors.isEmpty()
        record(started, success, settings.demoMode, summary.joinToString(", "), errors.joinToString("\n"))

        val outcome = SyncOutcome(firstSync, grades, events, lessons, notes, announcements, messages, lucky?.items?.values?.firstOrNull())
        container.notifier.afterSync(outcome, settings)
        if (success) Result.success() else afterFailure()
    }

    /** Zapis jednego rodzaju danych w transakcji, z wykrywaniem zmian (SPEC 4.4). */
    private suspend fun <T> store(
        type: RecordType,
        items: List<T>,
        serializer: KSerializer<T>,
        key: (T) -> String,
        inScope: (T) -> Boolean,
    ): TypeResult<T> = db.withTransaction {
        val now = System.currentTimeMillis()
        val rows = db.records().all(type).associateBy { it.sourceKey }
        val incoming = items.associateBy(key)
        val incomingJson = incoming.mapValues { JSON.encodeToString(serializer, it.value) }
        val changes = detectChanges(
            old = rows.mapValues { it.value.json },
            new = incomingJson,
            alreadyRemoved = rows.filterValues { it.removedAt != null }.keys,
            inScope = { k -> rows[k]?.let { inScope(JSON.decodeFromString(serializer, it.json)) } ?: false },
        )
        val updates = incomingJson.map { (k, json) ->
            val old = rows[k]
            when {
                old == null -> RecordEntity(type, k, json, firstSeenAt = now, lastSeenAt = now)
                k in changes.changed -> old.copy(json = json, lastSeenAt = now, changedAt = now, previousJson = old.json, removedAt = null)
                else -> old.copy(lastSeenAt = now)
            }
        } + changes.removed.mapNotNull { rows[it]?.copy(removedAt = now) }
        db.records().upsert(updates)
        val previous = changes.changed.mapNotNull { k -> rows[k]?.let { k to JSON.decodeFromString(serializer, it.json) } }.toMap()
        TypeResult(changes, incoming, previous)
    }

    private suspend fun record(started: Long, success: Boolean, demo: Boolean, summary: String, errors: String) {
        db.syncRuns().insert(SyncRunEntity(0, started, System.currentTimeMillis(), success, demo, summary, errors))
    }

    /** Po 3 nieudanych próbach z rzędu: jedno powiadomienie o problemie; WorkManager wydłuża odstępy (SPEC 4.3). */
    private suspend fun afterFailure(): Result {
        val recent = db.syncRuns().latestOnce(FAILURES_BEFORE_ALERT)
        if (recent.size == FAILURES_BEFORE_ALERT && recent.none { it.success }) {
            container.notifier.syncProblem(recent.first().errors)
        }
        return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    companion object {
        private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        private val SYNC_LOCK = Mutex() // jedna synchronizacja naraz, także gdy ręczna nałoży się na okresową
        private const val KEY_MANUAL = "manual"
        const val PERIODIC = "sync-periodic"
        const val MANUAL = "sync-now"
        private const val REMOVED_KEEP_DAYS = 14L
        private const val FAILURES_BEFORE_ALERT = 3
        private const val MAX_RETRIES = 3

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context, hours: Int) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.coerceIn(1, 12).toLong(), TimeUnit.HOURS)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setInputData(workDataOf(KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
        }
    }
}
