package pl.eclipse.core.source.demo

import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.LessonStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSourceTest {
    private val today = LocalDate.of(2026, 10, 7) // środa
    private val demo = DemoSource(today)

    @Test
    fun currentWeekHasSubstitutionAndCancelledLesson() {
        val week = demo.timetable(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        assertEquals(1, week.count { it.status == LessonStatus.SUBSTITUTION })
        assertEquals(1, week.count { it.status == LessonStatus.CANCELLED })
    }

    @Test
    fun dataIsDeterministicAndRelativeToToday() {
        assertEquals(demo.attendance(), DemoSource(today).attendance())
        assertTrue(demo.events(today, today.plusDays(14)).any { it.subjectKey == "fiz" })
        assertTrue(demo.grades().all { it.date <= today })
        val biology = demo.attendance().filter { it.subjectKey == "bio" }
        val absent = biology.count { it.category == AttendanceCategory.ABSENT }
        assertTrue(absent * 100 / biology.size in 35..55, "biologia: $absent/${biology.size}")
    }
}
