package pl.eclipse.core.calc

import kotlinx.serialization.Serializable
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LessonStatus
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import java.time.LocalDate

// Zakładka Important — reguły ostrzeżeń (SPEC 8). Kolejność poziomów: WATCH < WARNING < CRITICAL.

enum class WarningLevel { WATCH, WARNING, CRITICAL }

enum class WarningRule(val level: WarningLevel) {
    AVERAGE_BELOW_2(WarningLevel.CRITICAL),
    PROPOSED_ONE(WarningLevel.CRITICAL),
    ATTENDANCE_CRITICAL(WarningLevel.CRITICAL),
    AVERAGE_IN_2(WarningLevel.WARNING),
    FRESH_LOW_GRADE(WarningLevel.WARNING),
    ATTENDANCE_NEAR(WarningLevel.WARNING),
    AVERAGE_DROP(WarningLevel.WATCH),
    JUST_ABOVE_3(WarningLevel.WATCH),
    MARKED_DIFFICULT(WarningLevel.WATCH),
    UPCOMING_TEST(WarningLevel.WARNING),
}

@Serializable
data class ImportantSettings(
    val justAbove3Max: Double = 53.0,
    val freshDays: Long = 14,
    val freshBelowPercent: Double = 50.0,
    val attendanceCritical: Double = 50.0,
    val attendanceNear: Double = 60.0,
    val reserveNear: Int = 3,
    val dropPp: Double = 5.0,
    val testDays: Long = 7,
    val disabled: Set<WarningRule> = emptySet(),
)

/** Wszystko, co reguły muszą wiedzieć o przedmiocie w bieżącym okresie. */
data class SubjectStatus(
    val subjectKey: String,
    val grades: List<Grade>,
    val method: AverageMethod = AverageMethod.AUTO,
    val attendancePercent: Double? = null,
    val reserve: AbsenceReserve? = null,
    val isDifficult: Boolean = false,
    val events: List<SchoolEvent> = emptyList(),
    /** Oceny oznaczone ręcznie jako „poprawiona / pomiń”. */
    val skippedGradeKeys: Set<String> = emptySet(),
)

data class Reason(
    val rule: WarningRule,
    val value: Double? = null,
    val grade: Grade? = null,
    val event: SchoolEvent? = null,
)

data class SubjectWarning(
    val subjectKey: String,
    val level: WarningLevel,
    val average: Double?,
    val reasons: List<Reason>,
    /** Podpowiedź z kalkulatora: co trzeba dostać, żeby wejść na [hintGrade]. */
    val hintGrade: Int?,
    val hint: GoalResult?,
)

fun importantWarnings(
    subjects: List<SubjectStatus>,
    today: LocalDate,
    rules: GradingRules = GradingRules(),
    settings: ImportantSettings = ImportantSettings(),
): List<SubjectWarning> = subjects.mapNotNull { warningFor(it, today, rules, settings) }
    .sortedWith(compareByDescending<SubjectWarning> { it.level }.thenBy { it.average ?: Double.MAX_VALUE })

private fun warningFor(s: SubjectStatus, today: LocalDate, rules: GradingRules, settings: ImportantSettings): SubjectWarning? {
    val reasons = mutableListOf<Reason>()
    fun add(reason: Reason) {
        if (reason.rule !in settings.disabled) reasons += reason
    }
    val average = subjectAverage(s.grades, s.method, rules)
    val t2 = rules.thresholds[2] ?: 40.0
    val t3 = rules.thresholds[3] ?: 50.0

    if (average != null) {
        when {
            average < t2 -> add(Reason(WarningRule.AVERAGE_BELOW_2, average))
            average < t3 -> add(Reason(WarningRule.AVERAGE_IN_2, average))
            average <= settings.justAbove3Max -> add(Reason(WarningRule.JUST_ABOVE_3, average))
        }
    }
    s.grades.filter { it.kind == GradeKind.PROPOSED && it.symbol.trim().startsWith("1") }
        .forEach { add(Reason(WarningRule.PROPOSED_ONE, grade = it)) }

    val attendance = s.attendancePercent
    val reserve = s.reserve?.reserve
    when {
        (attendance != null && attendance < settings.attendanceCritical) || (reserve != null && reserve <= 0) ->
            add(Reason(WarningRule.ATTENDANCE_CRITICAL, attendance))
        (attendance != null && attendance < settings.attendanceNear) || (reserve != null && reserve <= settings.reserveNear) ->
            add(Reason(WarningRule.ATTENDANCE_NEAR, attendance))
    }

    s.grades.filter { g ->
        countsToAverage(g, rules) && g.sourceKey !in s.skippedGradeKeys &&
            g.date > today.minusDays(settings.freshDays) && g.date <= today &&
            (gradePercent(g, rules).percent ?: 100.0) < settings.freshBelowPercent
    }.forEach { add(Reason(WarningRule.FRESH_LOW_GRADE, gradePercent(it, rules).percent, grade = it)) }

    trend(s.grades, today, s.method, rules)?.takeIf { it <= -settings.dropPp }
        ?.let { add(Reason(WarningRule.AVERAGE_DROP, it)) }
    if (s.isDifficult) add(Reason(WarningRule.MARKED_DIFFICULT))

    if (reasons.isEmpty()) return null
    s.events.filter { (it.type == EventType.TEST || it.type == EventType.QUIZ) && it.date in today..today.plusDays(settings.testDays) }
        .minByOrNull { it.date }
        ?.let { add(Reason(WarningRule.UPCOMING_TEST, event = it)) }
    if (reasons.isEmpty()) return null

    val next = average?.let { thresholdDistance(it, rules).nextGrade }
    return SubjectWarning(
        subjectKey = s.subjectKey,
        level = reasons.maxOf { it.rule.level },
        average = average,
        reasons = reasons,
        hintGrade = next,
        hint = next?.let { rules.thresholds[it] }?.let { goal(s.grades, it, s.method, rules) },
    )
}

/**
 * Składa stan przedmiotów w bieżącym półroczu z surowych danych — wejście dla [importantWarnings].
 * [currentWeek] — lekcje z bieżącego tygodnia (do szacunku zapasu nieobecności).
 */
fun subjectStatuses(
    subjects: List<Subject>,
    grades: List<Grade>,
    attendance: List<Attendance>,
    events: List<SchoolEvent>,
    currentWeek: List<Lesson>,
    student: StudentInfo?,
    today: LocalDate,
    difficult: Set<String> = emptySet(),
    methods: Map<String, AverageMethod> = emptyMap(),
    skippedGradeKeys: Set<String> = emptySet(),
    counted: Set<AttendanceCategory> = DEFAULT_COUNTED_ABSENCES,
): List<SubjectStatus> {
    val secondSemester = student != null && today > student.firstSemesterEnd
    val semester = if (secondSemester) 2 else 1
    val semesterEnd = student?.let { if (secondSemester) it.schoolYearEnd else it.firstSemesterEnd }
    val daysOff = events.filter { it.type == EventType.DAY_OFF }.map { it.date }.toSet()
    val weekly = currentWeek.filter { it.status != LessonStatus.CANCELLED }.groupBy { it.subjectKey }
    return subjects.map { subject ->
        val key = subject.sourceKey
        val entries = attendance.filter { it.subjectKey == key && it.semester == semester }
        SubjectStatus(
            subjectKey = key,
            grades = grades.filter { it.subjectKey == key && it.semester == semester },
            method = methods[key] ?: AverageMethod.AUTO,
            attendancePercent = attendancePercent(entries, counted),
            reserve = semesterEnd?.takeIf { entries.isNotEmpty() }?.let { end ->
                val perDay = weekly[key].orEmpty().groupingBy { it.date.dayOfWeek }.eachCount()
                absenceReserve(entries, perDay, today, end, daysOff, counted)
            },
            isDifficult = key in difficult,
            events = events.filter { it.subjectKey == key },
            skippedGradeKeys = skippedGradeKeys,
        )
    }
}
