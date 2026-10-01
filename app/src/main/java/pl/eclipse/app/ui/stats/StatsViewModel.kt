package pl.eclipse.app.ui.stats

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.eclipse.app.container
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.mondayOf
import pl.eclipse.core.calc.AverageMethod
import pl.eclipse.core.calc.Period
import pl.eclipse.core.calc.attendanceCounters
import pl.eclipse.core.calc.attendancePercent
import pl.eclipse.core.calc.averageSeries
import pl.eclipse.core.calc.countsToAverage
import pl.eclipse.core.calc.overallAverage
import pl.eclipse.core.calc.subjectAverage
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.EventType
import java.time.LocalDate

data class SubjectAttendance(
    val key: String,
    val name: String,
    val color: Color,
    val percent: Double,
    val entries: Int,
    /** Dokładne liczby wpisów po rozwinięciu przedmiotu (SPEC 7). */
    val present: Int = 0,
    val late: Int = 0,
    val absent: Int = 0,
    val excused: Int = 0,
    val released: Int = 0,
)

data class AbsenceRow(val date: LocalDate, val lessonNo: Int, val subject: String, val short: String)

data class ReserveRow(val name: String, val color: Color, val reserve: Int, val planned: Int)

data class StatsState(
    val loading: Boolean = true,
    val period: Period = Period.FIRST,
    val attendance: Double? = null,
    val present: Int = 0,
    val late: Int = 0,
    val excused: Int = 0,
    val unexcused: Int = 0,
    val bySubject: List<SubjectAttendance> = emptyList(),
    val unexcusedList: List<AbsenceRow> = emptyList(),
    val lateList: List<AbsenceRow> = emptyList(),
    val subjects: List<Triple<String, String, Color>> = emptyList(),
    val selected: Set<String> = emptySet(),
    val averageLines: List<Pair<Color, List<Pair<LocalDate, Double>>>> = emptyList(),
    val thresholds: Map<Int, Double> = emptyMap(),
    val loadLabels: List<String> = emptyList(),
    val loadValues: List<Int> = emptyList(),
    val loadHeavy: Set<Int> = emptySet(),
    val reserves: List<ReserveRow> = emptyList(),
    val criticalAttendance: Double = 50.0,
    val nearAttendance: Double = 60.0,
    val reserveNear: Int = 3,
)

private data class Ui(val period: Period? = null, val selected: Set<String> = emptySet())

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<StatsState> = combine(container.snapshot, container.user, container.settings.settings, ui) { snapshot, user, settings, ui ->
        val i = Insights(snapshot, user, settings)
        val today = i.today
        val period = ui.period ?: if (snapshot.student != null && today > snapshot.student.firstSemesterEnd) Period.SECOND else Period.FIRST
        fun inPeriod(semester: Int) = when (period) {
            Period.FIRST -> semester == 1
            Period.SECOND -> semester == 2
            Period.YEAR -> true
        }
        val entries = snapshot.attendance.filter { inPeriod(it.semester) }
        val counted = settings.countedAbsences
        val counters = attendanceCounters(entries)
        val bySubject = snapshot.subjects.mapNotNull { s ->
            val list = entries.filter { it.subjectKey == s.sourceKey }
            attendancePercent(list, counted)?.let {
                SubjectAttendance(
                    key = s.sourceKey,
                    name = s.name,
                    color = i.color(s.sourceKey),
                    percent = it,
                    entries = list.size,
                    present = list.count { e -> e.category == AttendanceCategory.PRESENT },
                    late = list.count { e -> e.category == AttendanceCategory.LATE },
                    absent = list.count { e -> e.category == AttendanceCategory.ABSENT },
                    excused = list.count { e -> e.category == AttendanceCategory.ABSENT_EXCUSED },
                    released = list.count { e -> e.category == AttendanceCategory.RELEASED },
                )
            }
        }.sortedBy { it.percent }

        // średnia w czasie: ogólna (średnia ze średnich przedmiotów do danego dnia) + wybrane przedmioty
        val grades = snapshot.grades.filter { it.removedAt == null && inPeriod(it.value.semester) }.map { it.value }
        val countedGrades = grades.filter { countsToAverage(it, settings.grading) }
        val days = countedGrades.map { it.date }.distinct().sorted()
        val overallSeries = days.mapNotNull { day ->
            val upTo = countedGrades.filter { it.date <= day }
            overallAverage(upTo.groupBy { it.subjectKey }.map { (key, list) -> subjectAverage(list, i.methods[key] ?: AverageMethod.AUTO, settings.grading) })?.let { day to it }
        }
        val lines = listOf(Color(settings.accent) to overallSeries) + ui.selected.map { key ->
            i.color(key) to averageSeries(grades.filter { it.subjectKey == key }, i.methods[key] ?: AverageMethod.AUTO, settings.grading)
        }

        // obciążenie: 4 tygodnie wstecz, 8 naprzód
        val monday = mondayOf(today)
        val weeks = (-4..8).map { monday.plusWeeks(it.toLong()) }
        val tests = i.activeEvents.filter { it.type == EventType.TEST || it.type == EventType.QUIZ }
        val load = weeks.map { w -> tests.count { it.date >= w && it.date < w.plusWeeks(1) } }
        val heavy = weeks.indices.filter { idx -> tests.count { it.type == EventType.TEST && it.date >= weeks[idx] && it.date < weeks[idx].plusWeeks(1) } >= 3 }.toSet()

        val reserves = i.statuses.mapNotNull { s ->
            s.reserve?.let { ReserveRow(i.name(s.subjectKey), i.color(s.subjectKey), it.reserve, it.plannedLessons) }
        }.sortedBy { it.reserve }

        StatsState(
            loading = false,
            period = period,
            attendance = attendancePercent(entries, counted),
            present = entries.count { it.category == AttendanceCategory.PRESENT || it.category == AttendanceCategory.LATE },
            late = counters.late,
            excused = counters.excused,
            unexcused = counters.unexcused.size,
            bySubject = bySubject,
            unexcusedList = counters.unexcused.sortedByDescending { it.date }
                .map { AbsenceRow(it.date, it.lessonNo, i.name(it.subjectKey), i.short(it.subjectKey)) },
            lateList = entries.filter { it.category == AttendanceCategory.LATE }.sortedByDescending { it.date }
                .map { AbsenceRow(it.date, it.lessonNo, i.name(it.subjectKey), i.short(it.subjectKey)) },
            subjects = snapshot.subjects.map { Triple(it.sourceKey, it.name, i.color(it.sourceKey)) },
            selected = ui.selected,
            averageLines = lines,
            thresholds = settings.grading.thresholds,
            loadLabels = weeks.map { "%d.%02d".format(it.dayOfMonth, it.monthValue) },
            loadValues = load,
            loadHeavy = heavy,
            reserves = reserves,
            criticalAttendance = settings.important.attendanceCritical,
            nearAttendance = settings.important.attendanceNear,
            reserveNear = settings.important.reserveNear,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setPeriod(period: Period) = ui.update { it.copy(period = period) }

    fun toggleSubject(key: String) = ui.update { it.copy(selected = if (key in it.selected) it.selected - key else it.selected + key) }
}
