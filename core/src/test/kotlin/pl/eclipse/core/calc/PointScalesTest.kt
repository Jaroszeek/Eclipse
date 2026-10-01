package pl.eclipse.core.calc

import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun point(subject: String, category: String?, points: Double, max: Double?, daysAgo: Long = 1) = Grade(
    sourceKey = "$subject-$category-$points-$max-$daysAgo",
    subjectKey = subject,
    semester = 1,
    kind = GradeKind.POINT,
    symbol = points.toString(),
    points = points,
    maxPoints = max,
    category = category,
    date = DAY.minusDays(daysAgo),
)

class PointScalesTest {
    @Test
    fun categoriesOfOneSubjectAreListedFromHighest() {
        val grades = listOf(
            point("che", "Kartkówka", 18.0, 20.0),
            point("che", "Sprawdzian", 27.0, 30.0),
            point("che", "Kartkówka", 15.0, 20.0),
        )
        assertEquals(
            listOf("Sprawdzian" to 30.0, "Kartkówka" to 20.0),
            pointScales(grades).map { it.category to it.maxPoints },
        )
        assertEquals(listOf(1, 2), pointScales(grades).map { it.count })
    }

    @Test
    fun mostCommonMaximumWins() {
        val grades = listOf(
            point("fiz", "Sprawdzian", 20.0, 25.0, 1),
            point("fiz", "Sprawdzian", 22.0, 25.0, 2),
            point("fiz", "Sprawdzian", 30.0, 40.0, 3),
        )
        val scale = pointScales(grades).single()
        assertEquals(25.0, scale.maxPoints)
        assertEquals(3, scale.count)
        assertTrue(scale.varied, "różne maksima powinny być oznaczone")
    }

    @Test
    fun oneMaximumIsNotVaried() {
        assertFalse(pointScales(listOf(point("fiz", "Sprawdzian", 20.0, 25.0))).single().varied)
    }

    @Test
    fun subjectsWithoutPointGradesAreAbsent() {
        val grades = listOf(
            g("5"), // zwykła ocena z „mat”
            point("inf", "Projekt", 9.0, 10.0),
            point("inf", "Projekt", 8.0, null), // bez maksimum — pomijamy
        )
        val scales = pointScales(grades)
        assertEquals(listOf("inf"), scales.map { it.subjectKey })
        assertEquals(1, scales.single().count)
    }

    @Test
    fun zeroOrNegativeMaximumIsIgnored() {
        assertEquals(emptyList(), pointScales(listOf(point("inf", "Projekt", 0.0, 0.0))))
    }

    @Test
    fun missingCategoryGivesEmptyName() {
        assertEquals("", pointScales(listOf(point("inf", null, 5.0, 10.0))).single().category)
    }

    @Test
    fun subjectsAreGroupedSeparately() {
        val grades = listOf(point("che", "Sprawdzian", 27.0, 30.0), point("fiz", "Kartkówka", 8.0, 10.0))
        assertEquals(listOf("che" to 30.0, "fiz" to 10.0), pointScales(grades).map { it.subjectKey to it.maxPoints })
    }
}
