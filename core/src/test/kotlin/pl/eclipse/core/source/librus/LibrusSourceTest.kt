package pl.eclipse.core.source.librus

import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.LessonStatus
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** Zmyślone odpowiedzi o strukturze z rekonesansu (bez prawdziwych danych). */
private val FIXTURES = mapOf(
    "Classes" to """{"Class":{"Id":1,"BeginSchoolYear":"2026-09-01","EndFirstSemester":"2027-01-17","EndSchoolYear":"2027-06-25"}}""",
    "Subjects" to """{"Subjects":[{"Id":10,"Name":"matematyka","Short":"Mat"},{"Id":11,"Name":"informatyka","Short":"Inf"}]}""",
    "Users" to """{"Users":[{"Id":100,"FirstName":"Jan","LastName":"Testowy"},{"Id":101,"FirstName":null,"LastName":"Admin"}]}""",
    "Classrooms" to """{"Classrooms":[{"Id":50,"Name":"Sala","Symbol":"12"}]}""",
    "Timetables?weekStart=2026-09-28" to """{"Timetable":{
        "2026-09-28":[[],[{"Lesson":{"Id":"7"},"Classroom":{"Id":"50"},"LessonNo":"2","HourFrom":"08:25","HourTo":"09:10",
          "Subject":{"Id":"10","Name":"matematyka","Short":"Mat"},"Teacher":{"Id":"100","FirstName":"Jan","LastName":"Testowy"},
          "IsSubstitutionClass":false,"IsCanceled":true,"SubstitutionNote":null}]],
        "2026-09-29":[[{"Lesson":{"Id":"8"},"Classroom":{"Id":"50"},"LessonNo":"1","HourFrom":"07:30","HourTo":"08:15",
          "Subject":{"Id":"11","Name":"informatyka","Short":"Inf"},"Teacher":{"Id":"100","FirstName":"Jan","LastName":"Testowy"},
          "IsSubstitutionClass":true,"IsCanceled":false,"SubstitutionNote":null}]],
        "2026-09-30":[[]]},"Pages":{"Next":"x","Prev":"y"}}""",
    "HomeWorks/Categories" to """{"Categories":[{"Id":1,"Name":"Sprawdzian"},{"Id":2,"Name":"Kartkówka"},{"Id":3,"Name":"Dzień bez zajęć edukacyjnych"},{"Id":4,"Name":"Zebranie rodziców"},{"Id":5,"Name":"ZAJĘCIA ON-LINE"}]}""",
    "HomeWorks" to """{"HomeWorks":[
        {"Id":1,"Content":"Dział 1","Date":"2026-10-05","Category":{"Id":1},"LessonNo":"3","TimeFrom":"09:20:00","TimeTo":"10:05:00","Subject":{"Id":10}},
        {"Id":2,"Content":"","Date":"2026-10-06","Category":{"Id":3},"LessonNo":null,"TimeFrom":"08:00:00","TimeTo":"15:00:00"},
        {"Id":3,"Content":"","Date":"2026-12-01","Category":{"Id":2},"LessonNo":"1","TimeFrom":"07:30:00","TimeTo":"08:15:00","Subject":{"Id":10}}]}""",
    "Grades/Categories" to """{"Categories":[{"Id":1,"Name":"kartkówka","CountToTheAverage":true},{"Id":2,"Name":"Nieprzygotowanie","CountToTheAverage":false}]}""",
    "Grades/Comments" to """{"Comments":[{"Id":1,"Grade":{"Id":20},"Text":"poprawiona"}]}""",
    "Grades" to """{"Grades":[
        {"Id":20,"Subject":{"Id":10},"Category":{"Id":1},"AddedBy":{"Id":100},"Grade":"5","Date":"2026-09-20","Semester":1,
         "IsConstituent":true,"IsSemester":false,"IsSemesterProposition":false,"IsFinal":false,"IsFinalProposition":false},
        {"Id":21,"Subject":{"Id":10},"Category":{"Id":2},"AddedBy":{"Id":100},"Grade":"np","Date":"2026-09-21","Semester":1,
         "IsConstituent":true,"IsSemester":false,"IsSemesterProposition":true,"IsFinal":false,"IsFinalProposition":false}]}""",
    "PointGrades/Categories" to """{"Categories":[{"Id":7,"Name":"Projekt","ValueFrom":0,"ValueTo":20,"CountToTheAverage":true}]}""",
    "PointGrades" to """{"Grades":[{"Id":30,"Subject":{"Id":11},"Category":{"Id":7},"AddedBy":{"Id":100},"Grade":"18.00","GradeValue":"18.00","Date":"2026-09-22","Semester":1}]}""",
    "Attendances/Types" to """{"Types":[{"Id":1,"Name":"Nieobecność","Short":"nb","IsPresenceKind":false},{"Id":2,"Name":"Obecność","Short":"ob","IsPresenceKind":true},{"Id":3,"Name":"uroczystość nieobecność","Short":"un","IsPresenceKind":false}]}""",
    "Lessons" to """{"Lessons":[{"Id":7,"Teacher":{"Id":100},"Subject":{"Id":10}}]}""",
    "Attendances" to """{"Attendances":[{"Id":1,"Lesson":{"Id":7},"Date":"2026-09-28","LessonNo":2,"Semester":1,"Type":{"Id":1}},{"Id":2,"Lesson":{"Id":99},"Date":"2026-09-28","LessonNo":3,"Semester":1,"Type":{"Id":3}}]}""",
    "Notes" to """{"Notes":[{"Id":1,"Teacher":{"Id":100},"Date":"2026-09-25","Positive":1,"Text":"Brawo"}]}""",
    "SchoolNotices" to """{"SchoolNotices":[{"Id":"abc","StartDate":"2026-09-26","EndDate":"2026-10-01","Subject":"Apel","Content":"Treść","AddedBy":{"Id":101}}]}""",
    "LuckyNumbers" to """{"LuckyNumber":{"LuckyNumber":8,"LuckyNumberDay":"2026-09-30"}}""",
)

class LibrusSourceTest {
    private val source = LibrusSource(get = { path -> FIXTURES[path]?.let { 200 to it } ?: (404 to "{}") })

    @Test
    fun studentAndSubjects() {
        assertEquals(LocalDate.of(2027, 1, 17), source.student().firstSemesterEnd)
        assertEquals(listOf("Mat", "Inf"), source.subjects().map { it.short })
    }

    @Test
    fun timetableMapsStatusTeacherAndRoom() {
        val week = source.timetable(LocalDate.of(2026, 9, 28))
        assertEquals(2, week.size)
        val monday = week.first { it.date == LocalDate.of(2026, 9, 28) }
        assertEquals(LessonStatus.CANCELLED, monday.status)
        assertEquals(LocalTime.of(8, 25), monday.start)
        assertEquals("Jan Testowy", monday.teacher)
        assertEquals("12", monday.room)
        assertEquals("subject-10", monday.subjectKey)
        assertEquals(LessonStatus.SUBSTITUTION, week.first { it.lessonNo == 1 }.status)
    }

    @Test
    fun eventsAreFilteredAndTyped() {
        val events = source.events(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(listOf(EventType.TEST, EventType.DAY_OFF), events.map { it.type })
        assertEquals(3, events[0].lessonNo)
        assertEquals(LocalTime.of(8, 0), events[1].time)
        assertEquals(EventType.TRIP, eventType("Zebranie rodziców"))
        assertEquals(EventType.OTHER, eventType("ZAJĘCIA ON-LINE"))
        assertEquals(EventType.QUIZ, eventType("Kartkówka"))
    }

    @Test
    fun gradesUseCategoriesCommentsAndPointMaximum() {
        val grades = source.grades().associateBy { it.sourceKey }
        val five = grades.getValue("grade-20")
        assertEquals(true, five.countsToAverage)
        assertEquals("poprawiona", five.description)
        assertEquals("Jan Testowy", five.teacher)
        val np = grades.getValue("grade-21")
        assertEquals(false, np.countsToAverage)
        assertEquals(GradeKind.PROPOSED, np.kind)
        val points = grades.getValue("point-30")
        assertEquals(GradeKind.POINT, points.kind)
        assertEquals(18.0, points.points)
        assertEquals(20.0, points.maxPoints)
        assertEquals("18", points.symbol)
    }

    @Test
    fun attendanceSubjectComesFromLessons() {
        val attendance = source.attendance()
        assertEquals(AttendanceCategory.ABSENT, attendance[0].category)
        assertEquals("subject-10", attendance[0].subjectKey)
        assertEquals(AttendanceCategory.OTHER, attendance[1].category) // „uroczystość nieobecność” nie liczy się
        assertEquals(null, attendance[1].subjectKey)
    }

    @Test
    fun notesNoticesLuckyNumber() {
        assertEquals("Jan Testowy", source.notes(null).single().teacher)
        assertEquals("Admin", source.announcements(null).single().author)
        assertEquals(8, source.luckyNumber()?.number)
    }
}
