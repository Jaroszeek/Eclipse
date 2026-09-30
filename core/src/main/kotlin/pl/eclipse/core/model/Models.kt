@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class, InstantSerializer::class)

package pl.eclipse.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// Modele domenowe (SPEC 5). `sourceKey` to stabilny klucz rekordu z Librusa (SPEC 4.4).

@Serializable
data class StudentInfo(
    val schoolYearStart: LocalDate,
    val firstSemesterEnd: LocalDate,
    val schoolYearEnd: LocalDate,
)

@Serializable
data class Subject(val sourceKey: String, val name: String, val short: String)

enum class LessonStatus { NORMAL, SUBSTITUTION, CANCELLED }

@Serializable
data class Lesson(
    val sourceKey: String,
    val date: LocalDate,
    val lessonNo: Int,
    val start: LocalTime,
    val end: LocalTime,
    val subjectKey: String?,
    val teacher: String?,
    val room: String?,
    val status: LessonStatus,
    val changeNote: String? = null,
)

enum class EventType { TEST, QUIZ, TRIP, DAY_OFF, OTHER }

@Serializable
data class SchoolEvent(
    val sourceKey: String,
    val date: LocalDate,
    val lessonNo: Int?,
    val time: LocalTime?,
    val subjectKey: String?,
    val category: String,
    val type: EventType,
    val description: String,
)

@Serializable
data class Homework(
    val sourceKey: String,
    val subjectKey: String?,
    val assignedDate: LocalDate,
    val dueDate: LocalDate,
    val title: String,
    val description: String,
)

enum class GradeKind { REGULAR, POINT, PROPOSED, SEMESTER, FINAL, DESCRIPTIVE }

@Serializable
data class Grade(
    val sourceKey: String,
    val subjectKey: String,
    val semester: Int,
    val kind: GradeKind,
    /** Symbol z Librusa, np. "4+", "np"; przy ocenie punktowej np. "17". */
    val symbol: String,
    val points: Double? = null,
    val maxPoints: Double? = null,
    val countsToAverage: Boolean = true,
    val category: String? = null,
    val description: String? = null,
    val teacher: String? = null,
    val date: LocalDate,
)

enum class AttendanceCategory { PRESENT, ABSENT, ABSENT_EXCUSED, LATE, RELEASED, SCHOOL_DUTY, OTHER }

@Serializable
data class Attendance(
    val sourceKey: String,
    val date: LocalDate,
    val lessonNo: Int,
    val subjectKey: String?,
    val typeShort: String,
    val category: AttendanceCategory,
    val semester: Int,
)

enum class NoteKind { POSITIVE, NEGATIVE, NEUTRAL }

@Serializable
data class Note(
    val sourceKey: String,
    val date: LocalDate,
    val teacher: String?,
    val category: String?,
    val kind: NoteKind,
    val text: String,
)

@Serializable
data class Announcement(
    val sourceKey: String,
    val date: LocalDate,
    val author: String?,
    val title: String,
    val content: String,
)

@Serializable
data class Message(
    val sourceKey: String,
    val sentAt: Instant,
    val sender: String,
    val title: String,
    val content: String?,
    /** Kiedy wiadomość przeczytano w Librusie (null — nieprzeczytana). */
    val readAt: Instant? = null,
    val hasAttachment: Boolean = false,
)

@Serializable
data class LuckyNumber(val date: LocalDate, val number: Int)
