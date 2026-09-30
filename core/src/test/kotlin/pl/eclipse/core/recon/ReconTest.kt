package pl.eclipse.core.recon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ReconTest {
    @Test
    fun reportHidesPersonalDataAndShowsGradeSymbols() {
        val body = """
            {"Grades":[{"Id":1,"Grade":"5","AddedBy":{"FirstName":"Anna","LastName":"Kowalska"},
            "Comment":"sprawdzian 85%","Date":"2026-09-12"}],"Url":"https://x"}
        """.trimIndent()

        val report = describe("Grades", 200, body)

        assertFalse("Anna" in report || "Kowalska" in report || "sprawdzian" in report, report)
        assertContains(report, "Grades[].Grade: tekst (1/1) — 5 ×1")
        assertContains(report, "Grades[].Comment: tekst (1/1) — 1 z „%”")
        assertContains(report, "Grades[].Date: data (1/1)")
    }
}
