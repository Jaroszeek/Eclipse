package pl.eclipse.app.ui.calendar

import androidx.compose.ui.graphics.Color
import pl.eclipse.app.data.CustomEventEntity
import pl.eclipse.app.data.LabelAssignmentEntity
import pl.eclipse.app.data.LabelEntity
import pl.eclipse.app.data.Stored
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.components.BlockKind
import pl.eclipse.app.ui.components.BlockStatus
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Homework
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LessonStatus
import pl.eclipse.core.model.SchoolEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Etykieta przypięta do elementu kalendarza, z notatką (np. „cyrkiel”). */
data class AttachedLabel(val label: LabelEntity, val assignment: LabelAssignmentEntity)

/** Źródło elementu kalendarza — do arkusza szczegółów. */
sealed interface CalendarRef {
    data class OfLesson(val lesson: Lesson, val tests: List<SchoolEvent>) : CalendarRef
    data class OfEvent(val event: Stored<SchoolEvent>) : CalendarRef
    data class OfHomework(val homework: Homework) : CalendarRef
    data class OfCustom(val event: CustomEventEntity) : CalendarRef
}

/** Wspólny element kalendarza (SPEC 10.2). */
data class CalendarItem(
    val id: String,
    val kind: BlockKind,
    val title: String,
    val short: String,
    val date: LocalDate,
    /** null = cały dzień (pasek nad siatką). */
    val start: LocalTime?,
    val end: LocalTime?,
    val lessonNo: Int?,
    val subjectKey: String?,
    val typeColor: Color,
    val subjectColor: Color,
    val status: BlockStatus,
    val labels: List<AttachedLabel>,
    val isImportant: Boolean,
    val daysUntil: Long,
    val isNew: Boolean,
    val movedFrom: LocalDate?,
    val ref: CalendarRef,
) {
    val allDay get() = start == null
}

/** Dzwonki: numer lekcji → godziny, z planu (najczęstsze wartości). */
fun bells(lessons: List<Lesson>): Map<Int, Pair<LocalTime, LocalTime>> =
    lessons.groupBy { it.lessonNo }.mapValues { (_, list) ->
        list.groupingBy { it.start to it.end }.eachCount().maxBy { it.value }.key
    }.toSortedMap()

/** Klucz lekcji do etykiet: (data, numer lekcji, przedmiot) — ID z Librusa bywa niestabilne (SPEC 5). */
fun lessonKey(lesson: Lesson) = "${lesson.date}|${lesson.lessonNo}|${lesson.subjectKey}"

/**
 * Buduje elementy kalendarza z danych szkolnych i danych użytkownika dla dni [from]..[to].
 * Sprawdzian z numerem lekcji trafia na blok tej lekcji (znacznik i poświata), a nie jako osobny blok (SPEC 13).
 */
fun calendarItems(i: Insights, from: LocalDate, to: LocalDate, since: Long): List<CalendarItem> {
    val today = i.today
    val user = i.user
    val labels = user.labels.associateBy { it.id }
    fun attached(target: String, key: String) = user.assignments
        .filter { it.target == target && it.targetKey == key }
        .mapNotNull { a -> labels[a.labelId]?.let { AttachedLabel(it, a) } }

    val lessons = i.snapshot.lessons.filter { it.value.date in from..to && it.removedAt == null }.map { it.value }
    val bellTable = bells(i.snapshot.lessons.map { it.value })
    val events = i.snapshot.events.filter { it.value.date in from..to && (it.removedAt == null || it.removedAt.toEpochMilli() > System.currentTimeMillis() - 14 * 86_400_000L) }
    val tests = events.filter { it.removedAt == null && (it.value.type == EventType.TEST || it.value.type == EventType.QUIZ) }.map { it.value }

    val result = mutableListOf<CalendarItem>()
    val mergedTests = mutableSetOf<String>()

    lessons.forEach { l ->
        val onLesson = tests.filter { t ->
            t.date == l.date && t.subjectKey == l.subjectKey && (t.lessonNo == null || t.lessonNo == l.lessonNo)
        }
        onLesson.filter { it.lessonNo == l.lessonNo }.forEach { mergedTests += it.sourceKey }
        val test = onLesson.minByOrNull { if (it.type == EventType.TEST) 0 else 1 }
        val own = attached("LESSON", lessonKey(l)) + attached("SUBJECT_ALL_LESSONS", l.subjectKey.orEmpty())
        val kind = when (test?.type) {
            EventType.TEST -> BlockKind.TEST
            EventType.QUIZ -> BlockKind.QUIZ
            else -> BlockKind.LESSON
        }
        result += CalendarItem(
            id = "lesson|${l.sourceKey}",
            kind = kind,
            title = i.name(l.subjectKey),
            short = i.short(l.subjectKey),
            date = l.date,
            start = l.start,
            end = l.end,
            lessonNo = l.lessonNo,
            subjectKey = l.subjectKey,
            typeColor = test?.let { i.typeColor(it.type) } ?: own.firstOrNull()?.let { Color(it.label.color) } ?: i.typeColor("TEST"),
            subjectColor = i.color(l.subjectKey),
            status = when (l.status) {
                LessonStatus.NORMAL -> BlockStatus.NORMAL
                LessonStatus.SUBSTITUTION -> BlockStatus.SUBSTITUTION
                LessonStatus.CANCELLED -> BlockStatus.CANCELLED
            },
            labels = own,
            isImportant = test != null || own.isNotEmpty(),
            daysUntil = l.date.toEpochDay() - today.toEpochDay(),
            isNew = false,
            movedFrom = null,
            ref = CalendarRef.OfLesson(l, onLesson),
        )
    }

    events.filter { it.value.sourceKey !in mergedTests }.forEach { stored ->
        val e = stored.value
        val bell = e.lessonNo?.let(bellTable::get)
        val start = e.time ?: bell?.first
        val end = when {
            bell != null && e.time == null -> bell.second
            start != null -> start.plusMinutes(45)
            else -> null
        }
        val kind = when (e.type) {
            EventType.TEST -> BlockKind.TEST
            EventType.QUIZ -> BlockKind.QUIZ
            EventType.DAY_OFF -> BlockKind.DAY_OFF
            EventType.TRIP, EventType.OTHER -> BlockKind.EVENT
        }
        val moved = stored.previous?.date?.takeIf { it != e.date }
        result += CalendarItem(
            id = "event|${e.sourceKey}",
            kind = kind,
            title = listOf(i.name(e.subjectKey), e.category).filter { it.isNotBlank() }.joinToString(" — "),
            short = i.short(e.subjectKey).ifBlank { e.category.take(4) },
            date = e.date,
            start = if (e.type == EventType.DAY_OFF) null else start,
            end = if (e.type == EventType.DAY_OFF) null else end,
            lessonNo = e.lessonNo,
            subjectKey = e.subjectKey,
            typeColor = i.typeColor(e.type),
            subjectColor = e.subjectKey?.let(i::color) ?: i.typeColor(e.type),
            status = when {
                stored.removedAt != null -> BlockStatus.REMOVED
                moved != null -> BlockStatus.CHANGED
                else -> BlockStatus.NORMAL
            },
            labels = attached("SCHOOL_EVENT", e.sourceKey),
            isImportant = e.type == EventType.TEST || e.type == EventType.QUIZ,
            daysUntil = e.date.toEpochDay() - today.toEpochDay(),
            isNew = stored.firstSeenAt.toEpochMilli() > since,
            movedFrom = moved,
            ref = CalendarRef.OfEvent(stored),
        )
    }

    i.snapshot.homework.filter { it.removedAt == null && it.value.dueDate in from..to }.forEach { stored ->
        val h = stored.value
        result += CalendarItem(
            id = "homework|${h.sourceKey}",
            kind = BlockKind.HOMEWORK,
            title = listOf(i.name(h.subjectKey), h.title).filter { it.isNotBlank() }.joinToString(" — "),
            short = i.short(h.subjectKey).ifBlank { h.title.take(4) },
            date = h.dueDate,
            start = null,
            end = null,
            lessonNo = null,
            subjectKey = h.subjectKey,
            typeColor = i.typeColor("HOMEWORK"),
            subjectColor = i.color(h.subjectKey),
            status = BlockStatus.NORMAL,
            labels = emptyList(),
            isImportant = false,
            daysUntil = h.dueDate.toEpochDay() - today.toEpochDay(),
            isNew = stored.firstSeenAt.toEpochMilli() > since,
            movedFrom = null,
            ref = CalendarRef.OfHomework(h),
        )
    }

    user.customEvents.forEach { ce ->
        val start = runCatching { LocalDateTime.parse(ce.start) }.getOrNull() ?: return@forEach
        val end = runCatching { LocalDateTime.parse(ce.end) }.getOrNull() ?: start.plusHours(1)
        if (start.toLocalDate() !in from..to) return@forEach
        val color = ce.color?.let(::Color) ?: i.typeColor("CUSTOM")
        result += CalendarItem(
            id = "custom|${ce.id}",
            kind = BlockKind.CUSTOM,
            title = ce.title,
            short = ce.title.take(5),
            date = start.toLocalDate(),
            start = if (ce.allDay) null else start.toLocalTime(),
            end = if (ce.allDay) null else end.toLocalTime(),
            lessonNo = null,
            subjectKey = ce.subjectKey,
            typeColor = i.typeColor("CUSTOM"),
            subjectColor = ce.subjectKey?.let(i::color) ?: color,
            status = BlockStatus.NORMAL,
            labels = attached("CUSTOM_EVENT", ce.id.toString()),
            isImportant = false,
            daysUntil = start.toLocalDate().toEpochDay() - today.toEpochDay(),
            isNew = false,
            movedFrom = null,
            ref = CalendarRef.OfCustom(ce),
        )
    }
    return result.sortedWith(compareBy({ it.date }, { it.start ?: LocalTime.MIN }, { it.lessonNo ?: 0 }))
}
