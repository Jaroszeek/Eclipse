package pl.eclipse.core.transit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyTest {
    // węzły: 1 = start, 2 = przesiadka, 3 = cel, 4 = przystanek po drodze
    private fun run(id: Int, line: String, vararg stops: Triple<Int, Int, Int>) =
        TripRun(id, line, true, "kierunek", stops.map { (node, arrival, departure) -> TripStop(node, arrival, departure) })

    private val direct = run(1, "52", Triple(1, 100, 100), Triple(4, 110, 110), Triple(3, 125, 125))
    private val toTransfer = run(2, "8", Triple(1, 100, 100), Triple(2, 110, 110))
    private val fromTransfer = run(3, "20", Triple(2, 115, 115), Triple(3, 130, 130))

    @Test
    fun directJourneyIsFound() {
        val found = findJourneys(from = 1, to = 3, after = 0, runs = listOf(direct))
        assertEquals(1, found.size)
        assertEquals(100, found[0].departure)
        assertEquals(125, found[0].arrival)
        assertEquals(25, found[0].minutes)
        assertEquals(listOf("52"), found[0].legs.map { it.line })
        assertNull(found[0].transfer)
    }

    @Test
    fun transferJourneyIsFound() {
        val found = findJourneys(from = 1, to = 3, after = 0, runs = listOf(toTransfer, fromTransfer))
        assertEquals(1, found.size)
        assertEquals(listOf("8", "20"), found[0].legs.map { it.line })
        assertEquals(2, found[0].transfer)
        assertEquals(5, found[0].transferMinutes)
        assertEquals(100, found[0].departure)
        assertEquals(130, found[0].arrival)
    }

    @Test
    fun tooShortTransferIsSkipped() {
        // pociąg odjeżdża w tej samej minucie, w której przyjeżdża pierwszy — nie zdążymy
        val tight = run(3, "20", Triple(2, 110, 110), Triple(3, 130, 130))
        assertTrue(findJourneys(from = 1, to = 3, after = 0, runs = listOf(toTransfer, tight)).isEmpty())
    }

    @Test
    fun tooLongTransferIsSkipped() {
        val late = run(3, "20", Triple(2, 200, 200), Triple(3, 220, 220))
        assertTrue(findJourneys(from = 1, to = 3, after = 0, runs = listOf(toTransfer, late)).isEmpty())
    }

    @Test
    fun departuresBeforeTheChosenTimeAreSkipped() {
        assertTrue(findJourneys(from = 1, to = 3, after = 101, runs = listOf(direct)).isEmpty())
        assertEquals(1, findJourneys(from = 1, to = 3, after = 100, runs = listOf(direct)).size)
    }

    @Test
    fun worseJourneyIsDropped() {
        // przesiadka wyjeżdża później, a dowozi wcześniej — bezpośredni kurs odpada
        val slow = run(1, "52", Triple(1, 90, 90), Triple(3, 140, 140))
        val found = findJourneys(from = 1, to = 3, after = 0, runs = listOf(slow, toTransfer, fromTransfer))
        assertEquals(1, found.size)
        assertEquals(listOf("8", "20"), found[0].legs.map { it.line })
    }

    @Test
    fun bothJourneysStayWhenNeitherIsWorse() {
        // wcześniejszy kurs wyjeżdża wcześniej i dowozi wcześniej — oba są sensowne
        val early = run(1, "52", Triple(1, 60, 60), Triple(3, 80, 80))
        val found = findJourneys(from = 1, to = 3, after = 0, runs = listOf(early, toTransfer, fromTransfer))
        assertEquals(listOf(60, 100), found.map { it.departure })
    }

    @Test
    fun sameStopGivesNothing() {
        assertTrue(findJourneys(from = 1, to = 1, after = 0, runs = listOf(direct)).isEmpty())
    }

    @Test
    fun ridesAfterMidnightKeepTheirOrder() {
        // kurs 23:50 → 00:20 ma w GTFS godziny 1430 i 1460
        val night = run(1, "62", Triple(1, 1430, 1430), Triple(3, 1460, 1460))
        val found = findJourneys(from = 1, to = 3, after = 1400, runs = listOf(night))
        assertEquals(30, found.single().minutes)
    }
}
