package pl.eclipse.core.source.demo

import pl.eclipse.core.model.Announcement
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.Homework
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LessonStatus
import pl.eclipse.core.model.LuckyNumber
import pl.eclipse.core.model.Message
import pl.eclipse.core.model.Note
import pl.eclipse.core.model.NoteKind
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import pl.eclipse.core.source.DataSource
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Dane przykładowe (SPEC 14, Etap 1): uczeń technikum, daty liczone od `today`, stałe ziarno.
 * Scenariusze do Important: chemia < 40%, fizyka 40–50%, historia ze spadkiem średniej,
 * biologia z frekwencją blisko 50%, świeża jedynka z matematyki, geografia tuż nad progiem 3.
 */
class DemoSource(private val today: LocalDate = LocalDate.now(WARSAW)) : DataSource {
    // Rok szkolny demo zaczyna się 10 tygodni przed dziś, żeby zawsze była historia ocen i frekwencji.
    private val yearStart = today.minusWeeks(10).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val student = StudentInfo(yearStart, yearStart.plusWeeks(20), yearStart.plusWeeks(42))
    private val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    override fun login(email: String, password: String) = Unit
    override fun student() = student
    override fun subjects() = SUBJECTS.map { Subject(it.key, it.name, it.short) }

    override fun timetable(weekStart: LocalDate): List<Lesson> = (0L..4L).flatMap { day ->
        val date = weekStart.plusDays(day)
        PLAN.getValue(date.dayOfWeek).mapIndexed { index, key ->
            val no = index + 1
            val subject = SUBJECTS.first { it.key == key }
            val (status, note) = when {
                weekStart == thisMonday && date.dayOfWeek == DayOfWeek.WEDNESDAY && no == 3 ->
                    LessonStatus.SUBSTITUTION to "Zastępstwo: mgr Kamińska"
                weekStart == thisMonday && date.dayOfWeek == DayOfWeek.THURSDAY && no == 5 ->
                    LessonStatus.CANCELLED to "Lekcja odwołana"
                else -> LessonStatus.NORMAL to null
            }
            Lesson(
                sourceKey = "demo-lesson-$date-$no",
                date = date,
                lessonNo = no,
                start = BELLS[index].first,
                end = BELLS[index].second,
                subjectKey = key,
                teacher = if (status == LessonStatus.SUBSTITUTION) "Anna Kamińska" else subject.teacher,
                room = subject.room,
                status = status,
                changeNote = note,
            )
        }
    }

    override fun events(from: LocalDate, to: LocalDate) = EVENTS.map { e ->
        val date = weekday(today.plusDays(e.inDays))
        SchoolEvent(
            sourceKey = "demo-event-${e.inDays}",
            date = date,
            lessonNo = e.subjectKey?.let { lessonNoOf(it, date) },
            time = null,
            subjectKey = e.subjectKey,
            category = e.category,
            type = e.type,
            description = e.description,
        )
    }.filter { it.date in from..to }

    override fun homework(from: LocalDate, to: LocalDate) = listOf(
        Homework("demo-hw-1", "mat", today.minusDays(1), weekday(today.plusDays(1)), "Zadania 4.12–4.18", "Zbiór zadań, str. 87."),
        Homework("demo-hw-2", "pol", today.minusDays(2), weekday(today.plusDays(2)), "Notatka o „Lalce”", "Charakterystyka Wokulskiego, 1 strona."),
        Homework("demo-hw-3", "ang", today, weekday(today.plusDays(6)), "Essay: My dream job", "150–200 słów."),
    ).filter { it.dueDate in from..to }

    override fun grades(): List<Grade> = GRADES.mapIndexed { i, g ->
        val date = today.minusDays(g.daysAgo)
        Grade(
            sourceKey = "demo-grade-$i",
            subjectKey = g.subjectKey,
            semester = if (date <= student.firstSemesterEnd) 1 else 2,
            kind = g.kind,
            symbol = g.symbol,
            points = g.points,
            maxPoints = g.max,
            countsToAverage = g.counts,
            category = g.category,
            description = null,
            teacher = SUBJECTS.first { it.key == g.subjectKey }.teacher,
            date = date,
        )
    }

    override fun attendance(): List<Attendance> {
        val random = Random(SEED)
        val result = mutableListOf<Attendance>()
        var biologyLessons = 0
        var date = yearStart
        while (date < today) {
            PLAN[date.dayOfWeek]?.forEachIndexed { index, key ->
                val roll = random.nextDouble()
                // biologia: 7 nieobecności na 15 lekcji, rozłożone równo — frekwencja ok. 53%
                val biologyAbsent = key == "bio" && biologyLessons++ * 7 % 15 < 7
                val category = when {
                    biologyAbsent -> AttendanceCategory.ABSENT
                    roll < 0.03 -> AttendanceCategory.ABSENT
                    roll < 0.05 -> AttendanceCategory.ABSENT_EXCUSED
                    roll < 0.07 -> AttendanceCategory.LATE
                    else -> AttendanceCategory.PRESENT
                }
                val short = when (category) {
                    AttendanceCategory.ABSENT -> "nb"
                    AttendanceCategory.ABSENT_EXCUSED -> "u"
                    AttendanceCategory.LATE -> "sp"
                    else -> "ob"
                }
                result += Attendance(
                    sourceKey = "demo-att-$date-${index + 1}",
                    date = date,
                    lessonNo = index + 1,
                    subjectKey = key,
                    typeShort = short,
                    category = category,
                    semester = if (date <= student.firstSemesterEnd) 1 else 2,
                )
            }
            date = date.plusDays(1)
        }
        return result
    }

    override fun notes(since: Instant?) = listOf(
        Note("demo-note-1", today.minusDays(12), "Tomasz Wiśniewski", "Aktywność", NoteKind.POSITIVE, "Wzorowa praca w grupie podczas projektu."),
        Note("demo-note-2", today.minusDays(4), "Ewa Zielińska", "Zachowanie", NoteKind.NEGATIVE, "Używanie telefonu na lekcji."),
    )

    override fun announcements(since: Instant?) = listOf(
        Announcement("demo-ann-1", today.minusDays(1), "Sekretariat", "Zdjęcia klasowe", "W przyszły wtorek fotograf odwiedzi szkołę."),
        Announcement("demo-ann-2", today.minusDays(6), "Dyrekcja", "Dzień otwarty", "Zapraszamy na dzień otwarty szkoły."),
    )

    override fun messages(since: Instant?): List<Message> {
        val at = { days: Long -> today.minusDays(days).atTime(15, 30).atZone(WARSAW).toInstant() }
        return listOf(
            Message("demo-msg-1", at(0), "Katarzyna Nowak", "Poprawa kartkówki", "Poprawa kartkówki z funkcji w czwartek po 7. lekcji."),
            Message("demo-msg-2", at(3), "Wychowawca", "Wycieczka", "Proszę o zgody rodziców do piątku."),
            Message("demo-msg-3", at(9), "Piotr Lewandowski", "Materiały", "Prezentacja z ostatniej lekcji w załączniku."),
        )
    }

    override fun luckyNumber() = LuckyNumber(today, Random(today.toEpochDay()).nextInt(1, 31))

    private fun weekday(date: LocalDate): LocalDate = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> date.plusDays(2)
        DayOfWeek.SUNDAY -> date.plusDays(1)
        else -> date
    }

    private fun lessonNoOf(subjectKey: String, date: LocalDate): Int? =
        PLAN[date.dayOfWeek]?.indexOf(subjectKey)?.takeIf { it >= 0 }?.plus(1)

    private data class DemoSubject(val key: String, val name: String, val short: String, val teacher: String, val room: String)
    private data class DemoEvent(val inDays: Long, val subjectKey: String?, val type: EventType, val category: String, val description: String)
    private data class DemoGrade(
        val subjectKey: String,
        val symbol: String,
        val daysAgo: Long,
        val category: String,
        val kind: GradeKind = GradeKind.REGULAR,
        val points: Double? = null,
        val max: Double? = null,
        val counts: Boolean = true,
    )

    private companion object {
        val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        const val SEED = 42

        val SUBJECTS = listOf(
            DemoSubject("mat", "matematyka", "Mat", "Katarzyna Nowak", "12"),
            DemoSubject("pol", "język polski", "Pol", "Ewa Zielińska", "21"),
            DemoSubject("ang", "język angielski", "Ang", "John Smith", "33"),
            DemoSubject("fiz", "fizyka", "Fiz", "Piotr Lewandowski", "14"),
            DemoSubject("che", "chemia", "Che", "Marta Wójcik", "15"),
            DemoSubject("bio", "biologia", "Bio", "Tomasz Wiśniewski", "16"),
            DemoSubject("his", "historia", "His", "Adam Kowalczyk", "22"),
            DemoSubject("geo", "geografia", "Geo", "Barbara Mazur", "23"),
            DemoSubject("inf", "informatyka", "Inf", "Michał Krawczyk", "105"),
            DemoSubject("wf", "wychowanie fizyczne", "W-F", "Robert Kaczmarek", "sala gim."),
        )

        // Dzwonki jak w szkole użytkownika (rekonesans 30.09.2026).
        val BELLS = listOf(
            "07:30" to "08:15", "08:25" to "09:10", "09:20" to "10:05", "10:15" to "11:00",
            "11:15" to "12:00", "12:10" to "12:55", "13:05" to "13:50", "13:55" to "14:40",
        ).map { (from, to) -> LocalTime.parse(from) to LocalTime.parse(to) }

        val PLAN = mapOf(
            DayOfWeek.MONDAY to listOf("mat", "pol", "ang", "fiz", "his", "wf"),
            DayOfWeek.TUESDAY to listOf("pol", "mat", "che", "bio", "inf", "inf", "geo"),
            DayOfWeek.WEDNESDAY to listOf("ang", "mat", "fiz", "pol", "his", "wf"),
            DayOfWeek.THURSDAY to listOf("bio", "che", "mat", "geo", "pol", "ang", "inf"),
            DayOfWeek.FRIDAY to listOf("his", "fiz", "mat", "ang", "wf"),
        )

        val EVENTS = listOf(
            DemoEvent(-6, "bio", EventType.TEST, "Sprawdzian", "Genetyka — dział 3."),
            DemoEvent(2, "fiz", EventType.TEST, "Sprawdzian", "Kinematyka i dynamika."),
            DemoEvent(5, "mat", EventType.QUIZ, "Kartkówka", "Funkcja liniowa."),
            DemoEvent(7, null, EventType.TRIP, "Warsztaty integracyjne", "Warsztaty w auli, lekcje 3–5."),
            DemoEvent(9, "che", EventType.TEST, "Sprawdzian", "Wiązania chemiczne."),
            DemoEvent(12, "pol", EventType.TEST, "Sprawdzian", "Pozytywizm."),
            DemoEvent(16, "his", EventType.QUIZ, "Kartkówka", "Wojny napoleońskie."),
            DemoEvent(20, null, EventType.DAY_OFF, "Dzień bez zajęć edukacyjnych", "Dzień wolny od zajęć."),
            DemoEvent(26, "geo", EventType.TEST, "Sprawdzian", "Klimat Polski."),
        )

        val GRADES = listOf(
            // chemia: średnia < 40%
            DemoGrade("che", "1", 50, "Kartkówka"), DemoGrade("che", "2", 35, "Sprawdzian"),
            DemoGrade("che", "1", 20, "Kartkówka"), DemoGrade("che", "3", 8, "Odpowiedź ustna"),
            // fizyka: 40–50%
            DemoGrade("fiz", "2", 55, "Sprawdzian"), DemoGrade("fiz", "3", 40, "Kartkówka"),
            DemoGrade("fiz", "2", 25, "Sprawdzian"), DemoGrade("fiz", "3", 10, "Praca na lekcji"),
            // historia: spadek średniej o ≥ 5 pp w 28 dni
            DemoGrade("his", "5", 60, "Sprawdzian"), DemoGrade("his", "5", 50, "Kartkówka"),
            DemoGrade("his", "6", 40, "Odpowiedź ustna"), DemoGrade("his", "2", 20, "Sprawdzian"),
            DemoGrade("his", "1", 6, "Kartkówka"),
            // matematyka: świeża jedynka
            DemoGrade("mat", "4", 45, "Sprawdzian"), DemoGrade("mat", "5+", 30, "Kartkówka"),
            DemoGrade("mat", "4-", 18, "Zadanie domowe"), DemoGrade("mat", "1", 3, "Kartkówka"),
            DemoGrade("mat", "np", 12, "Nieprzygotowanie", counts = false),
            // geografia: tuż nad progiem 3 (53%)
            DemoGrade("geo", "3", 48, "Sprawdzian"), DemoGrade("geo", "3", 33, "Kartkówka"),
            DemoGrade("geo", "4", 21, "Prezentacja"), DemoGrade("geo", "2", 14, "Kartkówka"),
            DemoGrade("geo", "3", 5, "Odpowiedź ustna"),
            // informatyka: oceny punktowe
            DemoGrade("inf", "17", 42, "Projekt", GradeKind.POINT, 17.0, 20.0),
            DemoGrade("inf", "9", 26, "Kartkówka", GradeKind.POINT, 9.0, 10.0),
            DemoGrade("inf", "45", 9, "Sprawdzian", GradeKind.POINT, 45.0, 50.0),
            // pozostałe
            DemoGrade("pol", "4", 52, "Wypracowanie"), DemoGrade("pol", "5", 31, "Sprawdzian"),
            DemoGrade("pol", "4+", 11, "Odpowiedź ustna"), DemoGrade("pol", "+", 7, "Aktywność"),
            DemoGrade("ang", "5", 44, "Test"), DemoGrade("ang", "6", 29, "Prezentacja"), DemoGrade("ang", "5-", 13, "Kartkówka"),
            DemoGrade("bio", "4", 38, "Kartkówka"), DemoGrade("bio", "3", 6, "Sprawdzian"),
            DemoGrade("wf", "5", 36, "Aktywność"), DemoGrade("wf", "6", 15, "Sprawdzian"),
            DemoGrade("mat", "4", 1, "Propozycja", GradeKind.PROPOSED, counts = false),
        )
    }
}
