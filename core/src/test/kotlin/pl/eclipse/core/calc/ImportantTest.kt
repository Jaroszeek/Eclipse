package pl.eclipse.core.calc

import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.source.demo.DemoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportantTest {
    private fun rules(status: SubjectStatus) = importantWarnings(listOf(status), DAY).singleOrNull()

    private fun rulesOf(status: SubjectStatus) = rules(status)?.reasons?.map { it.rule }.orEmpty()

    @Test
    fun averageBelow2IsCritical() {
        val w = assertNotNull(rules(SubjectStatus("che", listOf(g("1", 40), g("2", 30)))))
        assertEquals(WarningLevel.CRITICAL, w.level)
        assertTrue(WarningRule.AVERAGE_BELOW_2 in w.reasons.map { it.rule })
        assertEquals(2, w.hintGrade)
    }

    @Test
    fun proposedOneIsCritical() {
        val w = rules(SubjectStatus("mat", listOf(g("5", 40), g("1", 2, kind = GradeKind.PROPOSED))))
        assertEquals(WarningLevel.CRITICAL, w?.level)
        assertEquals(listOf(WarningRule.PROPOSED_ONE), w?.reasons?.map { it.rule })
    }

    @Test
    fun attendanceRules() {
        val good = listOf(g("5", 40))
        assertEquals(listOf(WarningRule.ATTENDANCE_CRITICAL), rulesOf(SubjectStatus("bio", good, attendancePercent = 48.0)))
        assertEquals(
            listOf(WarningRule.ATTENDANCE_CRITICAL),
            rulesOf(SubjectStatus("bio", good, attendancePercent = 80.0, reserve = AbsenceReserve(20, 10, 10))),
        )
        assertEquals(listOf(WarningRule.ATTENDANCE_NEAR), rulesOf(SubjectStatus("bio", good, attendancePercent = 58.0)))
        assertEquals(
            listOf(WarningRule.ATTENDANCE_NEAR),
            rulesOf(SubjectStatus("bio", good, attendancePercent = 90.0, reserve = AbsenceReserve(20, 10, 7))),
        )
    }

    @Test
    fun averageIn2IsWarningWithHint() {
        val w = rules(SubjectStatus("fiz", listOf(g("2", 40), g("3", 30))))
        assertEquals(WarningLevel.WARNING, w?.level)
        assertEquals(listOf(WarningRule.AVERAGE_IN_2), w?.reasons?.map { it.rule })
        assertEquals(3, w?.hintGrade)
        assertEquals(GoalResult.Required(60.0, null, 4), w?.hint)
    }

    @Test
    fun freshLowGradeIsWarningUnlessSkipped() {
        val grades = listOf(g("6", 40), g("6", 30), g("1", 3))
        assertTrue(WarningRule.FRESH_LOW_GRADE in rulesOf(SubjectStatus("mat", grades)))
        assertTrue(WarningRule.FRESH_LOW_GRADE !in rulesOf(SubjectStatus("mat", grades, skippedGradeKeys = setOf(grades[2].sourceKey))))
        assertTrue(WarningRule.FRESH_LOW_GRADE !in rulesOf(SubjectStatus("mat", listOf(g("6", 40), g("1", 20)))))
    }

    @Test
    fun averageDropIsWatch() {
        // 100, 100, 90 sprzed 28 dni → dziś z 75: spadek ≥ 5 pp
        val w = rules(SubjectStatus("his", listOf(g("6", 40), g("6", 35), g("5", 30), g("4", 3))))
        assertEquals(WarningLevel.WATCH, w?.level)
        assertEquals(listOf(WarningRule.AVERAGE_DROP), w?.reasons?.map { it.rule })
    }

    @Test
    fun justAbove3AndDifficultAreWatch() {
        assertEquals(listOf(WarningRule.JUST_ABOVE_3), rulesOf(SubjectStatus("geo", listOf(g("3", 30), g("3", 20)))))
        assertEquals(listOf(WarningRule.MARKED_DIFFICULT), rulesOf(SubjectStatus("pol", listOf(g("5", 30)), isDifficult = true)))
        assertNull(rules(SubjectStatus("pol", listOf(g("5", 30)))))
    }

    @Test
    fun upcomingTestRaisesWatchToWarning() {
        val test = SchoolEvent("e1", DAY.plusDays(3), 2, null, "geo", "Sprawdzian", EventType.TEST, "")
        val far = test.copy(sourceKey = "e2", date = DAY.plusDays(10))
        val grades = listOf(g("3", 30), g("3", 20))
        assertEquals(WarningLevel.WARNING, rules(SubjectStatus("geo", grades, events = listOf(test)))?.level)
        assertEquals(WarningLevel.WATCH, rules(SubjectStatus("geo", grades, events = listOf(far)))?.level)
        // sam sprawdzian bez innego ostrzeżenia nie tworzy karty
        assertNull(rules(SubjectStatus("pol", listOf(g("5", 30)), events = listOf(test))))
    }

    @Test
    fun disabledRuleAndSorting() {
        val settings = ImportantSettings(disabled = setOf(WarningRule.JUST_ABOVE_3))
        assertNull(importantWarnings(listOf(SubjectStatus("geo", listOf(g("3", 30)))), DAY, settings = settings).singleOrNull())
        val sorted = importantWarnings(
            listOf(
                SubjectStatus("geo", listOf(g("3", 30))),
                SubjectStatus("fiz", listOf(g("2", 30), g("3", 20))),
                SubjectStatus("che", listOf(g("2", 30))),
                SubjectStatus("his", listOf(g("1", 30))),
            ),
            DAY,
        )
        assertEquals(listOf("his", "che", "fiz", "geo"), sorted.map { it.subjectKey })
    }

    @Test
    fun demoScenariosTriggerTheirRules() {
        val demo = DemoSource(DAY)
        val grades = demo.grades().groupBy { it.subjectKey }
        val warnings = importantWarnings(
            demo.subjects().map { s ->
                val att = demo.attendance().filter { it.subjectKey == s.sourceKey }
                SubjectStatus(s.sourceKey, grades[s.sourceKey].orEmpty(), attendancePercent = attendancePercent(att))
            },
            DAY,
        ).associateBy { it.subjectKey }
        assertTrue(WarningRule.AVERAGE_BELOW_2 in warnings.getValue("che").reasons.map { it.rule })
        assertTrue(WarningRule.AVERAGE_IN_2 in warnings.getValue("fiz").reasons.map { it.rule })
        assertTrue(WarningRule.AVERAGE_DROP in warnings.getValue("his").reasons.map { it.rule })
        assertTrue(WarningRule.FRESH_LOW_GRADE in warnings.getValue("mat").reasons.map { it.rule })
        assertTrue(WarningRule.JUST_ABOVE_3 in warnings.getValue("geo").reasons.map { it.rule })
        assertTrue(WarningRule.ATTENDANCE_NEAR in warnings.getValue("bio").reasons.map { it.rule })
    }
}
