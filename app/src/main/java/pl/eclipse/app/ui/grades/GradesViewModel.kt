package pl.eclipse.app.ui.grades

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
import kotlinx.coroutines.launch
import pl.eclipse.app.container
import pl.eclipse.app.data.Flags
import pl.eclipse.app.data.SubjectPrefsEntity
import pl.eclipse.app.data.UserFlagEntity
import pl.eclipse.app.ui.Insights
import pl.eclipse.core.calc.AverageMethod
import pl.eclipse.core.calc.GoalResult
import pl.eclipse.core.calc.PercentSource
import pl.eclipse.core.calc.Period
import pl.eclipse.core.calc.ThresholdDistance
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.calc.averageSeries
import pl.eclipse.core.calc.countsToAverage
import pl.eclipse.core.calc.goal
import pl.eclipse.core.calc.gradePercent
import pl.eclipse.core.calc.lowestGradeFor
import pl.eclipse.core.calc.overallAverage
import pl.eclipse.core.calc.predictedGrade
import pl.eclipse.core.calc.subjectAverage
import pl.eclipse.core.calc.thresholdDistance
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import java.time.LocalDate

enum class GradeSort { NAME, LOWEST, RECENT }

data class Tile(val key: String, val text: String, val grade: Int?, val isNew: Boolean)

data class SubjectCardState(
    val key: String,
    val name: String,
    val color: Color,
    val average: Double?,
    val predicted: Int?,
    val distance: ThresholdDistance?,
    val tiles: List<Tile>,
    val warning: WarningLevel?,
    val lastChange: LocalDate?,
)

data class GradesState(
    val loading: Boolean = true,
    val period: Period = Period.FIRST,
    val sort: GradeSort = GradeSort.NAME,
    val overall: Double? = null,
    val cards: List<SubjectCardState> = emptyList(),
    val thresholds: List<Double> = emptyList(),
)

data class GradeRow(
    val key: String,
    val date: LocalDate,
    val symbol: String,
    val percent: Double?,
    val grade: Int?,
    val source: PercentSource,
    val category: String?,
    val description: String?,
    val teacher: String?,
    val counts: Boolean,
    val kind: GradeKind,
    val skipped: Boolean,
)

/** Kalkulator „co jeśli” (SPEC 6.5) — nic nie zapisuje w danych. */
data class CalculatorState(
    val goalMode: Boolean = false,
    /** Hipotetyczne oceny: (punkty, maksimum). */
    val extra: List<Pair<Double, Double>> = emptyList(),
    val extraAverage: Double? = null,
    val extraPredicted: Int? = null,
    val targetPercent: Double = 75.0,
    val testMaxPoints: Double? = null,
    val result: GoalResult? = null,
)

data class SubjectState(
    val loading: Boolean = true,
    val key: String = "",
    val name: String = "",
    val color: Color = Color.Gray,
    val period: Period = Period.FIRST,
    val average: Double? = null,
    val predicted: Int? = null,
    val distance: ThresholdDistance? = null,
    val rows: List<GradeRow> = emptyList(),
    val special: List<GradeRow> = emptyList(),
    val series: List<Pair<LocalDate, Double>> = emptyList(),
    val thresholds: Map<Int, Double> = emptyMap(),
    /** Tabela przeliczeń użytkownika — do przycisków ocen w kalkulatorze. */
    val table: Map<Int, Double> = emptyMap(),
    val prefs: SubjectPrefsEntity = SubjectPrefsEntity(""),
    val short: String = "",
    val allPoints: Boolean = false,
    val calculator: CalculatorState = CalculatorState(),
)

private data class Ui(
    val period: Period? = null,
    val sort: GradeSort = GradeSort.NAME,
    val goalMode: Boolean = false,
    val extra: List<Pair<Double, Double>> = emptyList(),
    val target: Double = 75.0,
    val testMax: Double? = null,
)

class GradesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val ui = MutableStateFlow(Ui())
    private val lastVisit = MutableStateFlow(Long.MAX_VALUE)
    private val subjectKey = MutableStateFlow("")

    init {
        viewModelScope.launch { lastVisit.value = container.visits.visit("grades") }
    }

    private fun defaultPeriod(i: Insights) =
        if (i.snapshot.student != null && i.today > i.snapshot.student.firstSemesterEnd) Period.SECOND else Period.FIRST

    val state: StateFlow<GradesState> = combine(container.snapshot, container.user, container.settings.settings, ui, lastVisit) { snapshot, user, settings, ui, since ->
        val i = Insights(snapshot, user, settings)
        val period = ui.period ?: defaultPeriod(i)
        val all = snapshot.grades.filter { it.removedAt == null }
        val warnings = i.warnings.associateBy { it.subjectKey }
        val cards = snapshot.subjects.filter { user.prefs[it.sourceKey]?.hidden != true }.map { s ->
            val grades = all.filter { it.value.subjectKey == s.sourceKey && it.value.isIn(period) }
            val method = i.methods[s.sourceKey] ?: AverageMethod.AUTO
            val avg = subjectAverage(grades.map { it.value }, method, settings.grading)
            SubjectCardState(
                key = s.sourceKey,
                name = s.name,
                color = i.color(s.sourceKey),
                average = avg,
                predicted = avg?.let { predictedGrade(it, settings.grading) },
                distance = avg?.let { thresholdDistance(it, settings.grading) },
                tiles = grades.filter { it.value.kind == GradeKind.REGULAR || it.value.kind == GradeKind.POINT }
                    .sortedByDescending { it.value.date }.take(8).map { g -> tile(g.value, settings.grading, g.firstSeenAt.toEpochMilli() > since) },
                warning = warnings[s.sourceKey]?.level,
                lastChange = grades.maxOfOrNull { it.value.date },
            )
        }
        val sorted = when (ui.sort) {
            GradeSort.NAME -> cards.sortedBy { it.name.lowercase() }
            GradeSort.LOWEST -> cards.sortedBy { it.average ?: Double.MAX_VALUE }
            GradeSort.RECENT -> cards.sortedByDescending { it.lastChange ?: LocalDate.MIN }
        }
        GradesState(
            loading = false,
            period = period,
            sort = ui.sort,
            overall = overallAverage(cards.map { it.average }),
            cards = sorted,
            thresholds = settings.grading.thresholds.values.sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesState())

    val subject: StateFlow<SubjectState> = combine(container.snapshot, container.user, container.settings.settings, ui, subjectKey) { snapshot, user, settings, ui, key ->
        val i = Insights(snapshot, user, settings)
        val period = ui.period ?: defaultPeriod(i)
        val method = i.methods[key] ?: AverageMethod.AUTO
        val skipped = user.keys(Flags.SKIPPED_GRADE)
        val grades = snapshot.grades.filter { it.removedAt == null && it.value.subjectKey == key && it.value.isIn(period) }.map { it.value }
        val avg = subjectAverage(grades, method, settings.grading)
        val rows = grades.sortedByDescending { it.date }.map { row(it, settings.grading, it.sourceKey in skipped) }
        val counted = grades.filter { countsToAverage(it, settings.grading) }
        val allPoints = counted.isNotEmpty() && counted.all { it.kind == GradeKind.POINT }

        // kalkulator, tryb „Dodaj oceny”: hipotetyczne oceny jako punkty z maksimum (zwykła ocena i % to punkty na 100)
        val extraAvg = if (ui.extra.isEmpty()) null else if (method == AverageMethod.POINTS_SUM && allPoints) {
            (counted.sumOf { it.points ?: 0.0 } + ui.extra.sumOf { it.first }) / (counted.sumOf { it.maxPoints ?: 0.0 } + ui.extra.sumOf { it.second }) * 100
        } else {
            (counted.mapNotNull { gradePercent(it, settings.grading).percent } + ui.extra.map { it.first / it.second * 100 }).average()
        }
        val result = goal(grades, ui.target, method, settings.grading, testMaxPoints = ui.testMax)
        SubjectState(
            loading = false,
            key = key,
            name = i.name(key),
            color = i.color(key),
            period = period,
            average = avg,
            predicted = avg?.let { predictedGrade(it, settings.grading) },
            distance = avg?.let { thresholdDistance(it, settings.grading) },
            rows = rows.filter { it.kind == GradeKind.REGULAR || it.kind == GradeKind.POINT },
            special = rows.filter { it.kind != GradeKind.REGULAR && it.kind != GradeKind.POINT },
            series = averageSeries(grades, method, settings.grading),
            thresholds = settings.grading.thresholds,
            table = settings.grading.table,
            prefs = user.prefs[key] ?: SubjectPrefsEntity(key),
            short = i.short(key),
            allPoints = allPoints,
            calculator = CalculatorState(
                goalMode = ui.goalMode,
                extra = ui.extra,
                extraAverage = extraAvg,
                extraPredicted = extraAvg?.let { predictedGrade(it, settings.grading) },
                targetPercent = ui.target,
                testMaxPoints = ui.testMax,
                result = result,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectState())

    fun openSubject(key: String) {
        if (subjectKey.value != key) {
            subjectKey.value = key
            ui.update { it.copy(extra = emptyList(), goalMode = false) }
        }
    }

    fun setPeriod(period: Period) = ui.update { it.copy(period = period) }
    fun setSort(sort: GradeSort) = ui.update { it.copy(sort = sort) }
    fun setGoalMode(goal: Boolean) = ui.update { it.copy(goalMode = goal) }
    fun addHypothetical(points: Double, max: Double = 100.0) {
        if (max > 0 && points >= 0 && points <= max) ui.update { it.copy(extra = it.extra + (points to max)) }
    }
    fun removeHypothetical(index: Int) = ui.update { it.copy(extra = it.extra.filterIndexed { n, _ -> n != index }) }
    fun setTarget(percent: Double) = ui.update { it.copy(target = percent.coerceIn(0.0, 100.0)) }
    fun setTestMax(points: Double?) = ui.update { it.copy(testMax = points?.takeIf { p -> p > 0 }) }

    fun savePrefs(prefs: SubjectPrefsEntity) {
        viewModelScope.launch { container.database.user().upsertSubjectPrefs(prefs) }
    }

    /** „Poprawiona / pomiń” — ocena nie uruchamia reguły „świeża ocena do poprawy” (SPEC 8). */
    fun toggleSkipped(gradeKey: String, skipped: Boolean) {
        viewModelScope.launch {
            if (skipped) container.database.user().setFlag(UserFlagEntity(Flags.SKIPPED_GRADE, gradeKey))
            else container.database.user().clearFlag(Flags.SKIPPED_GRADE, gradeKey)
        }
    }

    fun gradeFor(percent: Double): Int? = lowestGradeFor(percent)
}

private fun Grade.isIn(period: Period) = when (period) {
    Period.FIRST -> semester == 1
    Period.SECOND -> semester == 2
    Period.YEAR -> true
}

private fun tile(g: Grade, rules: pl.eclipse.core.calc.GradingRules, isNew: Boolean): Tile {
    val percent = gradePercent(g, rules).percent
    val text = if (g.kind == GradeKind.POINT) "${g.symbol}/${g.maxPoints?.let { it.toBigDecimal().stripTrailingZeros().toPlainString() } ?: "?"}" else g.symbol
    return Tile(g.sourceKey, text, percent?.let { predictedGrade(it, rules) }, isNew)
}

private fun row(g: Grade, rules: pl.eclipse.core.calc.GradingRules, skipped: Boolean): GradeRow {
    val p = gradePercent(g, rules)
    return GradeRow(
        key = g.sourceKey,
        date = g.date,
        symbol = if (g.kind == GradeKind.POINT) "${g.symbol}/${g.maxPoints?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: "?"}" else g.symbol,
        percent = p.percent,
        grade = p.percent?.let { predictedGrade(it, rules) },
        source = p.source,
        category = g.category,
        description = g.description,
        teacher = g.teacher,
        counts = countsToAverage(g, rules),
        kind = g.kind,
        skipped = skipped,
    )
}
