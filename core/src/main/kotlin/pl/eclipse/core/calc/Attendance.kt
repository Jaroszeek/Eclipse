package pl.eclipse.core.calc

import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.AttendanceCategory
import java.time.DayOfWeek
import java.time.LocalDate

// Frekwencja i zapas nieobecności (SPEC 7).

/** Kategorie liczone do limitu nieobecności (edytowalne w ustawieniach). */
val DEFAULT_COUNTED_ABSENCES = setOf(AttendanceCategory.ABSENT, AttendanceCategory.ABSENT_EXCUSED)

/** Frekwencja % = (lekcje z wpisem − nieobecności liczone) ÷ lekcje z wpisem × 100; null bez wpisów. */
fun attendancePercent(
    entries: List<Attendance>,
    counted: Set<AttendanceCategory> = DEFAULT_COUNTED_ABSENCES,
): Double? {
    if (entries.isEmpty()) return null
    return (entries.size - entries.count { it.category in counted }) * 100.0 / entries.size
}

data class AttendanceCounters(val late: Int, val unexcused: List<Attendance>, val excused: Int)

fun attendanceCounters(entries: List<Attendance>) = AttendanceCounters(
    late = entries.count { it.category == AttendanceCategory.LATE },
    unexcused = entries.filter { it.category == AttendanceCategory.ABSENT }.sortedBy { it.date },
    excused = entries.count { it.category == AttendanceCategory.ABSENT_EXCUSED },
)

data class AbsenceReserve(val plannedLessons: Int, val limit: Int, val used: Int) {
    /** Ile lekcji można jeszcze opuścić (szacunek); ujemne = przekroczony limit. */
    val reserve get() = limit - used
}

/**
 * Zapas nieobecności do 50% dla przedmiotu w bieżącym półroczu (SPEC 7).
 * [entries] — wpisy frekwencji przedmiotu z bieżącego półrocza; [weeklyLessons] — ile lekcji przedmiotu
 * jest w typowym tygodniu planu w danym dniu; [daysOff] — znane dni wolne.
 */
fun absenceReserve(
    entries: List<Attendance>,
    weeklyLessons: Map<DayOfWeek, Int>,
    today: LocalDate,
    semesterEnd: LocalDate,
    daysOff: Set<LocalDate> = emptySet(),
    counted: Set<AttendanceCategory> = DEFAULT_COUNTED_ABSENCES,
): AbsenceReserve {
    var future = 0
    var day = today.plusDays(1)
    while (day <= semesterEnd) {
        if (day !in daysOff) future += weeklyLessons[day.dayOfWeek] ?: 0
        day = day.plusDays(1)
    }
    val planned = entries.size + future
    return AbsenceReserve(planned, planned / 2, entries.count { it.category in counted })
}
