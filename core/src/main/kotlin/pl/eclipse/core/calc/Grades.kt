package pl.eclipse.core.calc

import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

// Oceny i średnie procentowe (SPEC 6). Szkoła liczy zwykłą średnią procentów, bez wag.

enum class PercentSource { POINTS, TABLE, NONE }

enum class AverageMethod { AUTO, MEAN_PERCENT, POINTS_SUM }

enum class Period { FIRST, SECOND, YEAR }

data class GradingRules(
    /** Tabela przeliczeń: ocena → % (SPEC 6.2). */
    val table: Map<Int, Double> = mapOf(6 to 100.0, 5 to 90.0, 4 to 75.0, 3 to 50.0, 2 to 40.0, 1 to 0.0),
    /** Własne wartości symboli, np. "4+" → 80. Samodzielne "+" i "−" liczą się tylko, gdy mają tu wartość. */
    val symbolOverrides: Map<String, Double> = emptyMap(),
    /** Progi prognozy: minimalny % → ocena (SPEC 6.4). */
    val thresholds: Map<Int, Double> = mapOf(6 to 100.0, 5 to 90.0, 4 to 75.0, 3 to 50.0, 2 to 40.0),
    val roundBeforeThreshold: Boolean = false,
)

data class GradePercent(val percent: Double?, val source: PercentSource)

fun gradePercent(grade: Grade, rules: GradingRules = GradingRules()): GradePercent {
    val points = grade.points
    val max = grade.maxPoints
    if (grade.kind == GradeKind.POINT) {
        return if (points != null && max != null && max > 0) GradePercent(points / max * 100, PercentSource.POINTS)
        else GradePercent(null, PercentSource.NONE)
    }
    val symbol = normalize(grade.symbol)
    rules.symbolOverrides.entries.firstOrNull { normalize(it.key) == symbol }?.let {
        return GradePercent(it.value, PercentSource.TABLE)
    }
    val value = symbol.firstOrNull()?.digitToIntOrNull()?.takeIf { it in 1..6 && symbol.drop(1).all { c -> c == '+' || c == '-' } }
    return value?.let { rules.table[it] }?.let { GradePercent(it, PercentSource.TABLE) }
        ?: GradePercent(null, PercentSource.NONE)
}

/** Czy ocena wchodzi do średniej (SPEC 6.1). */
fun countsToAverage(grade: Grade, rules: GradingRules = GradingRules()): Boolean =
    grade.countsToAverage &&
        (grade.kind == GradeKind.REGULAR || grade.kind == GradeKind.POINT) &&
        gradePercent(grade, rules).percent != null

fun List<Grade>.inPeriod(period: Period): List<Grade> = when (period) {
    Period.FIRST -> filter { it.semester == 1 }
    Period.SECOND -> filter { it.semester == 2 }
    Period.YEAR -> this
}

/** Średnia przedmiotu w % albo null, gdy brak liczonych ocen („Brak ocen”, nigdy 0%). */
fun subjectAverage(
    grades: List<Grade>,
    method: AverageMethod = AverageMethod.AUTO,
    rules: GradingRules = GradingRules(),
): Double? {
    val counted = grades.filter { countsToAverage(it, rules) }
    if (counted.isEmpty()) return null
    if (method == AverageMethod.POINTS_SUM && counted.all { it.kind == GradeKind.POINT }) {
        return counted.sumOf { it.points ?: 0.0 } / counted.sumOf { it.maxPoints ?: 0.0 } * 100
    }
    return counted.mapNotNull { gradePercent(it, rules).percent }.average()
}

/** Średnia ogólna: średnia ze średnich przedmiotów, które mają oceny. */
fun overallAverage(subjectAverages: List<Double?>): Double? =
    subjectAverages.filterNotNull().takeIf { it.isNotEmpty() }?.average()

fun predictedGrade(average: Double, rules: GradingRules = GradingRules()): Int {
    val value = if (rules.roundBeforeThreshold) average.roundToInt().toDouble() else average
    return rules.thresholds.entries.sortedByDescending { it.value }.firstOrNull { value >= it.value }?.key ?: 1
}

/** Odległość do następnego progu i zapas nad obecnym, w punktach procentowych (SPEC 6.4). */
data class ThresholdDistance(val nextGrade: Int?, val toNext: Double?, val margin: Double)

fun thresholdDistance(average: Double, rules: GradingRules = GradingRules()): ThresholdDistance {
    val current = predictedGrade(average, rules)
    val currentThreshold = rules.thresholds[current] ?: 0.0
    val next = rules.thresholds.entries.filter { it.key > current }.minByOrNull { it.key }
    return ThresholdDistance(next?.key, next?.let { it.value - average }, average - currentThreshold)
}

/** Najniższa zwykła ocena z tabeli, której procent spełnia wynik (np. 68% → 4). */
fun lowestGradeFor(percent: Double, rules: GradingRules = GradingRules()): Int? =
    rules.table.entries.filter { it.value >= percent }.minByOrNull { it.value }?.key

sealed interface GoalResult {
    /** Nawet 0% nie obniży średniej poniżej celu. */
    data object AlreadySafe : GoalResult

    /** Wymagany wynik jednej kolejnej oceny. */
    data class Required(val percent: Double, val points: Double?, val lowestGrade: Int?) : GoalResult

    /** Jedna ocena nie wystarczy — potrzeba tylu ocen po 100%. */
    data class NeedsMore(val gradesAt100: Int) : GoalResult

    data object Impossible : GoalResult
}

/**
 * Kalkulator „co jeśli”, tryb „Cel” (SPEC 6.5): co trzeba dostać, żeby średnia wynosiła co najmniej [target] %.
 * Dla POINTS_SUM podaj [testMaxPoints] — maksimum punktów najbliższego sprawdzianu.
 */
fun goal(
    grades: List<Grade>,
    target: Double,
    method: AverageMethod = AverageMethod.AUTO,
    rules: GradingRules = GradingRules(),
    testMaxPoints: Double? = null,
): GoalResult {
    val counted = grades.filter { countsToAverage(it, rules) }
    if (method == AverageMethod.POINTS_SUM && testMaxPoints != null && counted.all { it.kind == GradeKind.POINT }) {
        val required = target / 100 * (counted.sumOf { it.maxPoints ?: 0.0 } + testMaxPoints) - counted.sumOf { it.points ?: 0.0 }
        return when {
            required <= 0 -> GoalResult.AlreadySafe
            required > testMaxPoints -> GoalResult.Impossible
            else -> (required / testMaxPoints * 100).let { GoalResult.Required(it, required, lowestGradeFor(it, rules)) }
        }
    }
    val percents = counted.mapNotNull { gradePercent(it, rules).percent }
    val n = percents.size
    val sum = percents.sum()
    val required = target * (n + 1) - sum
    return when {
        required <= 0 -> GoalResult.AlreadySafe
        required <= 100 -> GoalResult.Required(required, null, lowestGradeFor(required, rules))
        target >= 100 -> GoalResult.Impossible
        else -> GoalResult.NeedsMore(ceil((target * n - sum) / (100 - target)).toInt())
    }
}

/** Seria do wykresu: dla każdego dnia z nową oceną — średnia wszystkich ocen do tego dnia (SPEC 6.6). */
fun averageSeries(
    grades: List<Grade>,
    method: AverageMethod = AverageMethod.AUTO,
    rules: GradingRules = GradingRules(),
): List<Pair<LocalDate, Double>> {
    val counted = grades.filter { countsToAverage(it, rules) }
    return counted.map { it.date }.distinct().sorted().mapNotNull { day ->
        subjectAverage(counted.filter { it.date <= day }, method, rules)?.let { day to it }
    }
}

/** Trend: średnia dziś minus średnia sprzed 28 dni, o ile wtedy były co najmniej 3 liczone oceny. */
fun trend(
    grades: List<Grade>,
    today: LocalDate,
    method: AverageMethod = AverageMethod.AUTO,
    rules: GradingRules = GradingRules(),
): Double? {
    val counted = grades.filter { countsToAverage(it, rules) && it.date <= today }
    val before = counted.filter { it.date <= today.minusDays(TREND_DAYS) }
    if (before.size < 3) return null
    val now = subjectAverage(counted, method, rules) ?: return null
    val then = subjectAverage(before, method, rules) ?: return null
    return now - then
}

const val TREND_DAYS = 28L

private fun normalize(symbol: String) = symbol.trim().replace('−', '-').replace('–', '-')
