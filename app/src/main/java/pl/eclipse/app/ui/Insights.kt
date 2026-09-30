package pl.eclipse.app.ui

import androidx.compose.ui.graphics.Color
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.Flags
import pl.eclipse.app.data.SchoolSnapshot
import pl.eclipse.app.data.UserData
import pl.eclipse.app.ui.theme.subjectColors
import pl.eclipse.core.calc.AverageMethod
import pl.eclipse.core.calc.SubjectStatus
import pl.eclipse.core.calc.SubjectWarning
import pl.eclipse.core.calc.importantWarnings
import pl.eclipse.core.calc.subjectStatuses
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.SchoolEvent
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")

fun today(): LocalDate = LocalDate.now(WARSAW)

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Wspólne wyliczenia dla wielu ekranów — liczone w ViewModelach z danych i funkcji z `:core`. */
class Insights(val snapshot: SchoolSnapshot, val user: UserData, val settings: AppSettings, val today: LocalDate = today()) {
    val subjectNames: Map<String, String> = snapshot.subjects.associate { it.sourceKey to it.name }
    val subjectShorts: Map<String, String> = snapshot.subjects.associate { s ->
        s.sourceKey to (user.prefs[s.sourceKey]?.short?.takeIf { it.isNotBlank() } ?: s.short)
    }
    val colors: Map<String, Color> = subjectColors(subjectNames, user.prefs.mapNotNull { (k, v) -> v.color?.let { k to it } }.toMap())
    val methods: Map<String, AverageMethod> = user.prefs.mapValues { runCatching { AverageMethod.valueOf(it.value.averageMethod) }.getOrDefault(AverageMethod.AUTO) }

    /** Kolor typu wydarzenia: własny z ustawień albo domyślny (SPEC 11.3). */
    fun typeColor(key: String): Color = settings.typeColors[key]?.let { Color(it) } ?: defaultTypeColor(key)

    fun typeColor(type: EventType): Color = typeColor(typeKey(type))

    fun name(key: String?) = key?.let(subjectNames::get).orEmpty()
    fun short(key: String?) = key?.let(subjectShorts::get).orEmpty()
    fun color(key: String?): Color = key?.let(colors::get) ?: Color.Gray

    val activeEvents: List<SchoolEvent> by lazy { snapshot.events.filter { it.removedAt == null }.map { it.value } }

    val statuses: List<SubjectStatus> by lazy {
        val monday = mondayOf(today)
        subjectStatuses(
            subjects = snapshot.subjects.filter { user.prefs[it.sourceKey]?.hidden != true },
            grades = snapshot.grades.filter { it.removedAt == null }.map { it.value },
            attendance = snapshot.attendance,
            events = activeEvents,
            currentWeek = snapshot.lessons.filter { it.removedAt == null && it.value.date in monday..monday.plusDays(6) }.map { it.value },
            student = snapshot.student,
            today = today,
            difficult = user.prefs.filterValues { it.isDifficult }.keys,
            methods = methods,
            skippedGradeKeys = user.keys(Flags.SKIPPED_GRADE),
            counted = settings.countedAbsences,
        )
    }

    /** Ostrzeżenia Important bez powodów ukrytych przez użytkownika („Ukryj ten powód” — wraca, gdy dane się zmienią). */
    val warnings: List<SubjectWarning> by lazy {
        val hidden = user.keys(Flags.HIDDEN_REASON)
        importantWarnings(statuses, today, settings.grading, settings.important).mapNotNull { w ->
            val reasons = w.reasons.filter { reasonKey(w.subjectKey, it) !in hidden }
            if (reasons.isEmpty()) null else w.copy(reasons = reasons, level = reasons.maxOf { it.rule.level })
        }
    }

    /** Najbliższy sprawdzian lub kartkówka w ciągu 14 dni — do tarczy zaćmienia (SPEC 11.7). */
    val nextTest: SchoolEvent? by lazy {
        activeEvents.filter { (it.type == EventType.TEST || it.type == EventType.QUIZ) && it.date in today..today.plusDays(14) }
            .minWithOrNull(compareBy({ it.date }, { it.lessonNo ?: 0 }))
    }

    /** Początek lekcji z numerem [lessonNo] w dniu [date] (z planu), albo 8:00. */
    fun startOf(date: LocalDate, lessonNo: Int?): LocalTime =
        snapshot.lessons.firstOrNull { it.value.date == date && it.value.lessonNo == lessonNo }?.value?.start
            ?: snapshot.lessons.firstOrNull { it.value.lessonNo == lessonNo }?.value?.start
            ?: LocalTime.of(8, 0)

    fun hoursUntil(event: SchoolEvent): Long =
        Duration.between(LocalDateTime.now(WARSAW), event.date.atTime(event.time ?: startOf(event.date, event.lessonNo))).toHours()
}

val TYPE_KEYS = listOf("TEST", "QUIZ", "HOMEWORK", "EVENT", "DAY_OFF", "CUSTOM")

fun typeKey(type: EventType) = when (type) {
    EventType.TEST -> "TEST"
    EventType.QUIZ -> "QUIZ"
    EventType.DAY_OFF -> "DAY_OFF"
    EventType.TRIP, EventType.OTHER -> "EVENT"
}

fun defaultTypeColor(key: String): Color = when (key) {
    "TEST" -> pl.eclipse.app.ui.theme.Palette.Test
    "QUIZ" -> pl.eclipse.app.ui.theme.Palette.Quiz
    "HOMEWORK" -> pl.eclipse.app.ui.theme.Palette.Homework
    "EVENT" -> pl.eclipse.app.ui.theme.Palette.SchoolEvent
    "DAY_OFF" -> pl.eclipse.app.ui.theme.Palette.DayOff
    else -> pl.eclipse.app.ui.theme.Palette.Custom
}

/** Klucz powodu ostrzeżenia do „Ukryj ten powód”: zawiera wartość, więc powód wraca, gdy dane się zmienią. */
fun reasonKey(subjectKey: String, reason: pl.eclipse.core.calc.Reason): String =
    listOf(subjectKey, reason.rule.name, reason.value?.let { "%.1f".format(it) }, reason.grade?.sourceKey, reason.event?.sourceKey)
        .joinToString("|")
