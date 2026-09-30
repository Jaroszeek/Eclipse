package pl.eclipse.core.calc

import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.AttendanceCategory.ABSENT
import pl.eclipse.core.model.AttendanceCategory.ABSENT_EXCUSED
import pl.eclipse.core.model.AttendanceCategory.LATE
import pl.eclipse.core.model.AttendanceCategory.PRESENT
import pl.eclipse.core.model.AttendanceCategory.RELEASED
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal fun a(category: AttendanceCategory, daysAgo: Long = 1) =
    Attendance("a$category$daysAgo", DAY.minusDays(daysAgo), 1, "bio", "", category, 1)

class AttendanceTest {
    @Test
    fun attendancePercentCountsOnlyAbsences() {
        val entries = listOf(a(PRESENT), a(LATE), a(RELEASED), a(ABSENT), a(ABSENT_EXCUSED, 2))
        assertEquals(60.0, attendancePercent(entries)) // spóźnienie i zwolnienie to nie nieobecność
        assertNull(attendancePercent(emptyList()))
        val counters = attendanceCounters(entries)
        assertEquals(1, counters.late)
        assertEquals(1, counters.unexcused.size)
        assertEquals(1, counters.excused)
    }

    @Test
    fun absenceReserve() {
        val today = LocalDate.of(2026, 10, 14) // środa
        val entries = List(6) { a(PRESENT, it.toLong()) } + List(2) { a(ABSENT, 10L + it) }
        // od czwartku 15.10 do piątku 30.10: wtorki 20 i 27 (2) + piątki 16, 23, 30 po 2 lekcje (6), bez 23.10 → 6
        val reserve = absenceReserve(
            entries = entries,
            weeklyLessons = mapOf(DayOfWeek.TUESDAY to 1, DayOfWeek.FRIDAY to 2),
            today = today,
            semesterEnd = LocalDate.of(2026, 10, 30),
            daysOff = setOf(LocalDate.of(2026, 10, 23)),
        )
        assertEquals(14, reserve.plannedLessons) // 8 odbytych + 6 przyszłych
        assertEquals(7, reserve.limit)
        assertEquals(2, reserve.used)
        assertEquals(5, reserve.reserve)
    }
}
