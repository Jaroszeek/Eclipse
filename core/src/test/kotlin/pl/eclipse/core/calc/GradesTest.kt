package pl.eclipse.core.calc

import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal val DAY: LocalDate = LocalDate.of(2026, 10, 15)

internal fun g(symbol: String, daysAgo: Long = 1, kind: GradeKind = GradeKind.REGULAR, counts: Boolean = true, semester: Int = 1) =
    Grade("k$symbol$daysAgo$kind", "mat", semester, kind, symbol, countsToAverage = counts, date = DAY.minusDays(daysAgo))

internal fun p(points: Double, max: Double, daysAgo: Long = 1) =
    Grade("p$points/$max$daysAgo", "inf", 1, GradeKind.POINT, points.toString(), points, max, date = DAY.minusDays(daysAgo))

class GradesTest {
    @Test
    fun conversionTable() {
        val expected = mapOf("6" to 100.0, "5" to 90.0, "4" to 75.0, "3" to 50.0, "2" to 40.0, "1" to 0.0)
        expected.forEach { (symbol, percent) -> assertEquals(percent, gradePercent(g(symbol)).percent, symbol) }
        assertEquals(75.0, gradePercent(g("4+")).percent)
        assertEquals(75.0, gradePercent(g("4−")).percent)
        assertEquals(PercentSource.TABLE, gradePercent(g("4")).source)
        assertEquals(85.0, gradePercent(p(17.0, 20.0)).percent)
        assertEquals(PercentSource.POINTS, gradePercent(p(17.0, 20.0)).source)
        assertNull(gradePercent(g("np")).percent)
        assertNull(gradePercent(g("+")).percent)
        assertEquals(80.0, gradePercent(g("4+"), GradingRules(symbolOverrides = mapOf("4+" to 80.0))).percent)
    }

    @Test
    fun whatCountsToAverage() {
        val grades = listOf(g("5"), g("np"), g("+"), g("3", counts = false), g("4", kind = GradeKind.PROPOSED), g("2"))
        assertEquals(65.0, subjectAverage(grades)) // (90 + 40) / 2
        assertEquals(
            (90.0 + 40 + 60) / 3,
            subjectAverage(grades, rules = GradingRules(symbolOverrides = mapOf("+" to 60.0))),
        )
        assertNull(subjectAverage(listOf(g("np"))))
    }

    @Test
    fun bothAverageMethods() {
        val points = listOf(p(18.0, 20.0), p(5.0, 10.0))
        assertEquals(70.0, subjectAverage(points, AverageMethod.MEAN_PERCENT)) // (90 + 50) / 2
        assertEquals(23.0 / 30 * 100, assertNotNull(subjectAverage(points, AverageMethod.POINTS_SUM)), 1e-9)
        // POINTS_SUM tylko, gdy wszystkie oceny są punktowe — inaczej zwykła średnia.
        assertEquals((90.0 + 50 + 75) / 3, assertNotNull(subjectAverage(points + g("4"), AverageMethod.POINTS_SUM)), 1e-9)
        assertEquals(overallAverage(listOf(70.0, null, 50.0)), 60.0)
        assertEquals(listOf(g("5", semester = 2)), listOf(g("4"), g("5", semester = 2)).inPeriod(Period.SECOND))
    }

    @Test
    fun thresholds() {
        assertEquals(6, predictedGrade(100.0))
        assertEquals(5, predictedGrade(90.0))
        assertEquals(4, predictedGrade(89.9))
        assertEquals(4, predictedGrade(75.0))
        assertEquals(3, predictedGrade(74.6))
        assertEquals(4, predictedGrade(74.6, GradingRules(roundBeforeThreshold = true)))
        assertEquals(2, predictedGrade(40.0))
        assertEquals(1, predictedGrade(39.9))
        val d = thresholdDistance(71.8)
        assertEquals(4, d.nextGrade)
        assertEquals(3.2, assertNotNull(d.toNext), 1e-9)
        assertEquals(21.8, d.margin, 1e-9)
        assertNull(thresholdDistance(100.0).nextGrade)
        assertEquals(4, lowestGradeFor(68.0))
    }

    @Test
    fun goalOneGradeIsEnough() {
        // 40 + 50 → cel 50%: potrzeba 50·3 − 90 = 60% → najniższa ocena 4 (75%)
        val result = goal(listOf(g("2"), g("3")), 50.0)
        assertIs<GoalResult.Required>(result)
        assertEquals(60.0, result.percent, 1e-9)
        assertEquals(4, result.lowestGrade)
    }

    @Test
    fun goalOneGradeIsNotEnough() {
        // 0 + 0 + 40 → cel 75%: p = 75·4 − 40 = 260 > 100; k = ⌈(225 − 40) / 25⌉ = 8
        assertEquals(GoalResult.NeedsMore(8), goal(listOf(g("1"), g("1"), g("2")), 75.0))
        assertEquals(GoalResult.Impossible, goal(listOf(g("5")), 100.0))
        assertIs<GoalResult.Required>(goal(listOf(g("6")), 100.0))
    }

    @Test
    fun goalAlreadySafe() {
        // 100 + 100 → cel 50%: 50·3 − 200 < 0
        assertEquals(GoalResult.AlreadySafe, goal(listOf(g("6"), g("6")), 50.0))
    }

    @Test
    fun goalPointsSum() {
        // 15/20 + 10/20 → cel 75% ze sprawdzianu za 40 pkt: 0,75·80 − 25 = 35 pkt
        val result = goal(listOf(p(15.0, 20.0), p(10.0, 20.0)), 75.0, AverageMethod.POINTS_SUM, testMaxPoints = 40.0)
        assertIs<GoalResult.Required>(result)
        assertEquals(35.0, assertNotNull(result.points), 1e-9)
        assertEquals(GoalResult.Impossible, goal(listOf(p(0.0, 20.0)), 90.0, AverageMethod.POINTS_SUM, testMaxPoints = 10.0))
    }

    @Test
    fun averageSeriesAndTrend() {
        val grades = listOf(g("6", 40), g("5", 35), g("5", 30), g("1", 5))
        assertEquals(listOf(100.0, 95.0, 280.0 / 3, 70.0), averageSeries(grades).map { it.second })
        assertEquals(70.0 - 280.0 / 3, assertNotNull(trend(grades, DAY)), 1e-9)
        assertNull(trend(listOf(g("6", 40), g("1", 2)), DAY)) // za mało ocen sprzed 28 dni
    }
}
