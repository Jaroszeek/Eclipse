package pl.eclipse.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import pl.eclipse.app.MainActivity
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.EclipseDatabase
import pl.eclipse.app.data.JSON
import pl.eclipse.app.data.NotificationEntity
import pl.eclipse.app.data.NotificationType
import pl.eclipse.app.data.RecordType
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.sync.SyncOutcome
import pl.eclipse.core.calc.AverageMethod
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.calc.gradePercent
import pl.eclipse.core.calc.importantWarnings
import pl.eclipse.core.calc.subjectStatuses
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LessonStatus
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Powiadomienia lokalne (SPEC 9): kanały, typy, godziny ciszy, przypomnienia, centrum powiadomień. */
class Notifier(private val context: Context, private val db: EclipseDatabase) {
    private val state = context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        NotificationType.entries.forEach { type ->
            manager.createNotificationChannel(
                NotificationChannel(type.name, context.getString(type.channelName), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    suspend fun afterSync(outcome: SyncOutcome, settings: AppSettings) {
        val subjects = load(RecordType.SUBJECT, Subject.serializer()).associate { it.sourceKey to it.name }
        fun subject(key: String?) = key?.let(subjects::get) ?: context.getString(R.string.notif_unknown_subject)
        val today = LocalDate.now(WARSAW)

        outcome.events?.let { scheduleReminders(it.items.values, it.changes.removed, settings, ::subject) }
        val critical = criticalSubjects(settings, today)
        val previousCritical = state.getStringSet(KEY_CRITICAL, null)
        state.edit().putStringSet(KEY_CRITICAL, critical).apply()
        if (outcome.firstSync) return // pierwsza synchronizacja tylko zapisuje stan (SPEC 4.3)

        outcome.grades?.let { r ->
            val fresh = r.changes.added.mapNotNull(r.items::get)
            val lines = fresh.map { g -> context.getString(R.string.notif_grade_line, subject(g.subjectKey), gradeText(g, settings)) }
            when {
                lines.size > GRADES_SUMMARY_FROM -> post(
                    NotificationType.GRADE, context.getString(R.string.notif_grades_summary, lines.size),
                    lines.joinToString("\n"), ROUTE_GRADES, settings,
                )
                else -> lines.forEach { post(NotificationType.GRADE, context.getString(R.string.notif_grade_title), it, ROUTE_GRADES, settings) }
            }
        }

        outcome.events?.let { r ->
            r.changes.added.mapNotNull(r.items::get).filter { it.isTest }.forEach { e ->
                post(
                    NotificationType.TEST, context.getString(R.string.notif_test_new, typeWord(e), subject(e.subjectKey)),
                    formatDate(e.date), ROUTE_TESTS, settings,
                )
            }
            r.changes.changed.forEach { key ->
                val now = r.items[key] ?: return@forEach
                val before = r.previous[key] ?: return@forEach
                if (now.isTest && now.date != before.date) post(
                    NotificationType.TEST, context.getString(R.string.notif_test_moved, typeWord(now), subject(now.subjectKey)),
                    context.getString(R.string.notif_test_moved_body, formatDate(before.date), formatDate(now.date)),
                    ROUTE_TESTS, settings,
                )
            }
        }

        outcome.lessons?.let { r ->
            (r.changes.added + r.changes.changed).mapNotNull(r.items::get)
                .filter { it.status != LessonStatus.NORMAL && (it.date == today || it.date == today.plusDays(1)) }
                .forEach { l ->
                    val what = context.getString(if (l.status == LessonStatus.CANCELLED) R.string.notif_plan_cancelled else R.string.notif_plan_substitution)
                    post(
                        NotificationType.PLAN_CHANGE, context.getString(R.string.notif_plan_title, subject(l.subjectKey)),
                        context.getString(R.string.notif_plan_body, formatDate(l.date), l.lessonNo, what), ROUTE_CALENDAR, settings,
                    )
                }
        }

        (critical - previousCritical.orEmpty()).forEach {
            post(NotificationType.IMPORTANT, context.getString(R.string.notif_important, subject(it)), "", ROUTE_IMPORTANT, settings)
        }

        outcome.luckyNumber?.let { lucky ->
            if (lucky.date == today && lucky.number == settings.myDiaryNumber && state.getString(KEY_LUCKY, null) != today.toString()) {
                state.edit().putString(KEY_LUCKY, today.toString()).apply()
                post(NotificationType.LUCKY_NUMBER, context.getString(R.string.notif_lucky, lucky.number), "", ROUTE_HOME, settings)
            }
        }

        outcome.messages?.let { r ->
            r.changes.added.mapNotNull(r.items::get).forEach {
                post(NotificationType.INBOX, context.getString(R.string.notif_message, it.sender), it.title, ROUTE_INBOX, settings)
            }
        }
        outcome.announcements?.let { r ->
            r.changes.added.mapNotNull(r.items::get).forEach {
                post(NotificationType.INBOX, context.getString(R.string.notif_announcement), it.title, ROUTE_INBOX, settings)
            }
        }
        outcome.notes?.let { r ->
            r.changes.added.mapNotNull(r.items::get).forEach {
                post(NotificationType.INBOX, context.getString(R.string.notif_note), it.teacher.orEmpty(), ROUTE_INBOX, settings)
            }
        }
    }

    suspend fun syncProblem(errors: String) {
        val settings = context.container.settings.current()
        post(NotificationType.SYNC_PROBLEM, context.getString(R.string.notif_sync_problem), errors.lineSequence().firstOrNull().orEmpty(), ROUTE_SETTINGS, settings)
    }

    suspend fun test() {
        post(
            NotificationType.REMINDER, context.getString(R.string.notif_test_title), context.getString(R.string.notif_test_body),
            ROUTE_HOME, context.container.settings.current().copy(quietFromHour = 0, quietToHour = 0),
        )
    }

    /** Zapisuje w centrum powiadomień; na ekran trafia od razu albo po godzinach ciszy (SPEC 9). */
    suspend fun post(type: NotificationType, title: String, body: String, route: String, settings: AppSettings) {
        val quiet = isQuiet(LocalTime.now(WARSAW).hour, settings)
        val id = db.user().insertNotification(
            NotificationEntity(type = type.name, title = title, body = body, route = route, postedAt = System.currentTimeMillis(), deferred = quiet),
        )
        if (type in settings.disabledNotifications) return
        if (quiet) scheduleDigest(settings) else show(id.toInt(), type, title, body, route)
    }

    /** Poranne podsumowanie powiadomień z godzin ciszy. */
    suspend fun postDigest() {
        val deferred = db.user().deferredNotifications()
        db.user().clearDeferred()
        if (deferred.isEmpty()) return
        show(
            DIGEST_ID, NotificationType.REMINDER, context.getString(R.string.notif_digest, deferred.size),
            deferred.take(5).joinToString("\n") { it.title }, ROUTE_NOTIFICATIONS,
        )
    }

    fun show(id: Int, type: NotificationType, title: String, body: String, route: String) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, type.name)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun scheduleDigest(settings: AppSettings) {
        val now = LocalDateTime.now(WARSAW)
        var at = now.toLocalDate().atTime(settings.quietToHour, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val request = OneTimeWorkRequestBuilder<DigestWorker>().setInitialDelay(Duration.between(now, at)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("quiet-digest", ExistingWorkPolicy.KEEP, request)
    }

    /** Przypomnienie dzień przed sprawdzianem o [AppSettings.reminderHour] — jednorazowe zadania WorkManagera. */
    private fun scheduleReminders(events: Collection<SchoolEvent>, removed: Set<String>, settings: AppSettings, subject: (String?) -> String) {
        val work = WorkManager.getInstance(context)
        removed.forEach { work.cancelUniqueWork("reminder-$it") }
        val now = LocalDateTime.now(WARSAW)
        events.filter { it.isTest }.forEach { e ->
            val at = e.date.minusDays(1).atTime(settings.reminderHour, 0)
            if (!at.isAfter(now)) return@forEach
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, at))
                .setInputData(
                    workDataOf(
                        ReminderWorker.TITLE to context.getString(R.string.notif_reminder, typeWord(e), subject(e.subjectKey)),
                        ReminderWorker.BODY to e.description,
                    ),
                )
                .build()
            work.enqueueUniqueWork("reminder-${e.sourceKey}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Przedmioty z ostrzeżeniem „Krytyczne” wg reguł z sekcji 8, liczone na danych z bazy. */
    private suspend fun criticalSubjects(settings: AppSettings, today: LocalDate): Set<String> {
        val prefs = db.user().subjectPrefsOnce()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val statuses = subjectStatuses(
            subjects = load(RecordType.SUBJECT, Subject.serializer()),
            grades = load(RecordType.GRADE, Grade.serializer()),
            attendance = load(RecordType.ATTENDANCE, Attendance.serializer()),
            events = load(RecordType.EVENT, SchoolEvent.serializer()),
            currentWeek = load(RecordType.LESSON, Lesson.serializer()).filter { it.date in monday..monday.plusDays(6) },
            student = load(RecordType.STUDENT, StudentInfo.serializer()).firstOrNull(),
            today = today,
            difficult = prefs.filter { it.isDifficult }.map { it.subjectKey }.toSet(),
            methods = prefs.associate { it.subjectKey to AverageMethod.valueOf(it.averageMethod) },
            counted = settings.countedAbsences,
        )
        return importantWarnings(statuses, today, settings.grading, settings.important)
            .filter { it.level == WarningLevel.CRITICAL }.map { it.subjectKey }.toSet()
    }

    private suspend fun <T> load(type: RecordType, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
        db.records().all(type).filter { it.removedAt == null }.map { JSON.decodeFromString(serializer, it.json) }

    private fun gradeText(g: Grade, settings: AppSettings): String {
        val percent = gradePercent(g, settings.grading).percent
        val symbol = if (g.kind == GradeKind.POINT) "${g.symbol}/${g.maxPoints?.let(::formatNumber) ?: "?"}" else g.symbol
        return percent?.let { "$symbol (${formatPercent(it)})" } ?: symbol
    }

    private fun typeWord(e: SchoolEvent) = context.getString(if (e.type == EventType.QUIZ) R.string.word_quiz else R.string.word_test)

    private val SchoolEvent.isTest get() = type == EventType.TEST || type == EventType.QUIZ

    companion object {
        private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        const val EXTRA_ROUTE = "route"
        const val ROUTE_HOME = "home"
        const val ROUTE_GRADES = "grades"
        const val ROUTE_TESTS = "tests"
        const val ROUTE_CALENDAR = "calendar"
        const val ROUTE_IMPORTANT = "important"
        const val ROUTE_INBOX = "inbox"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_NOTIFICATIONS = "notifications"
        private const val KEY_CRITICAL = "critical_subjects"
        private const val KEY_LUCKY = "lucky_notified_on"
        private const val GRADES_SUMMARY_FROM = 3
        private const val DIGEST_ID = 1

        fun isQuiet(hour: Int, settings: AppSettings): Boolean {
            val from = settings.quietFromHour
            val to = settings.quietToHour
            return when {
                from == to -> false
                from < to -> hour in from until to
                else -> hour >= from || hour < to
            }
        }
    }
}

private val NotificationType.channelName
    get() = when (this) {
        NotificationType.GRADE -> R.string.channel_grades
        NotificationType.TEST -> R.string.channel_tests
        NotificationType.REMINDER -> R.string.channel_reminders
        NotificationType.PLAN_CHANGE -> R.string.channel_plan
        NotificationType.IMPORTANT -> R.string.channel_important
        NotificationType.LUCKY_NUMBER -> R.string.channel_lucky
        NotificationType.INBOX -> R.string.channel_inbox
        NotificationType.SYNC_PROBLEM -> R.string.channel_sync
    }

private fun formatNumber(value: Double) = value.toBigDecimal().stripTrailingZeros().toPlainString()

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val notifier = applicationContext.container.notifier
        val settings = applicationContext.container.settings.settings.first()
        notifier.post(
            NotificationType.REMINDER, inputData.getString(TITLE).orEmpty(), inputData.getString(BODY).orEmpty(),
            Notifier.ROUTE_TESTS, settings,
        )
        return Result.success()
    }

    companion object {
        const val TITLE = "title"
        const val BODY = "body"
    }
}

class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.notifier.postDigest()
        return Result.success()
    }
}
