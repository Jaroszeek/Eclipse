package pl.eclipse.core.transit

// Wyszukiwanie połączeń (SPEC 17.2): bezpośrednie i z jedną przesiadką.
// Czyste funkcje — baza tylko podaje kursy, które tego dnia dotykają przystanku początkowego
// albo docelowego, a cała reszta liczy się tutaj.

/**
 * Przystanek na trasie kursu. [node] to węzeł (wszystkie perony pod jedną nazwą), [stop] — konkretny peron.
 * Godziny w minutach od północy; kursy po północy mają wartości powyżej 1440.
 */
data class TripStop(val node: Int, val stop: Int, val arrival: Int, val departure: Int)

/** Kurs jednego pojazdu: linia, kierunek i kolejne przystanki. */
data class TripRun(val id: Int, val line: String, val tram: Boolean, val head: String, val stops: List<TripStop>)

/** Przejazd jednym pojazdem — od wsiadania do wysiadania. */
data class Leg(
    val line: String,
    val tram: Boolean,
    val head: String,
    val from: Int,
    val to: Int,
    val departure: Int,
    val arrival: Int,
    /** Perony: z którego wyjeżdżamy i na którym wysiadamy. */
    val fromStop: Int = 0,
    val toStop: Int = 0,
) {
    val minutes: Int get() = arrival - departure
}

/** Połączenie: jeden przejazd albo dwa z przesiadką. */
data class Journey(val legs: List<Leg>) {
    val departure: Int get() = legs.first().departure
    val arrival: Int get() = legs.last().arrival
    val minutes: Int get() = arrival - departure

    /** Węzeł przesiadkowy albo null, gdy połączenie jest bezpośrednie. */
    val transfer: Int? get() = legs.firstOrNull()?.to?.takeIf { legs.size > 1 }

    /** Ile minut na przesiadkę; null przy połączeniu bezpośrednim. */
    val transferMinutes: Int? get() = if (legs.size > 1) legs[1].departure - legs[0].arrival else null

    /** Czy przesiadka wymaga przejścia na inny peron (np. na drugą stronę ulicy). */
    val changesPlatform: Boolean get() = legs.size > 1 && legs[0].toStop != legs[1].fromStop
}

/**
 * Połączenia z [from] do [to] odjeżdżające nie wcześniej niż [after].
 *
 * Zwracamy tylko połączenia, których nic nie bije: zostaje to, które przy danej godzinie odjazdu
 * dowozi najwcześniej. Dzięki temu lista nie puchnie od wariantów tej samej trasy.
 */
fun findJourneys(
    from: Int,
    to: Int,
    after: Int,
    runs: List<TripRun>,
    limit: Int = 8,
    minTransfer: Int = 2,
    maxTransfer: Int = 45,
    platformChange: Int = 2,
): List<Journey> {
    if (from == to) return emptyList()
    val journeys = mutableListOf<Journey>()
    val first = mutableListOf<Leg>() // dokąd dowiozą kursy z przystanku początkowego
    val second = mutableListOf<Leg>() // skąd przyjeżdżają kursy na przystanek docelowy

    runs.forEach { run ->
        val board = run.stops.indexOfFirst { it.node == from && it.departure >= after }
        if (board >= 0) {
            val departure = run.stops[board].departure
            for (i in board + 1 until run.stops.size) {
                val stop = run.stops[i]
                if (stop.node == from) continue
                val leg = Leg(run.line, run.tram, run.head, from, stop.node, departure, stop.arrival, run.stops[board].stop, stop.stop)
                if (stop.node == to) journeys += Journey(listOf(leg)) else first += leg
            }
        }
        val exit = run.stops.indexOfLast { it.node == to }
        if (exit > 0) {
            for (i in 0 until exit) {
                val stop = run.stops[i]
                // przejazd z przystanku początkowego to połączenie bezpośrednie — mamy je już wyżej
                if (stop.node == to || stop.node == from) continue
                second += Leg(
                    run.line, run.tram, run.head, stop.node, to, stop.departure, run.stops[exit].arrival,
                    stop.stop, run.stops[exit].stop,
                )
            }
        }
    }

    val byTransfer = second.groupBy { it.from }
    first.forEach { leg ->
        byTransfer[leg.to]?.forEach { next ->
            val wait = next.departure - leg.arrival
            // przesiadka z peronu na peron (np. na drugą stronę ulicy) wymaga przejścia, więc i więcej czasu
            val needed = if (leg.toStop == next.fromStop) minTransfer else minTransfer + platformChange
            if (wait in needed..maxTransfer) journeys += Journey(listOf(leg, next))
        }
    }

    // od najpóźniejszego odjazdu: zostaje tylko to, co dowozi wcześniej niż wszystko późniejsze
    val best = mutableListOf<Journey>()
    var earliest = Int.MAX_VALUE
    journeys
        // przy tej samej godzinie odjazdu i przyjazdu wygrywa przesiadka bez przechodzenia na inny peron
        .sortedWith(
            compareByDescending<Journey> { it.departure }
                .thenBy { it.arrival }
                .thenBy { if (it.changesPlatform) 1 else 0 }
                .thenBy { it.legs.size },
        )
        .forEach { journey ->
            if (journey.arrival < earliest) {
                best += journey
                earliest = journey.arrival
            }
        }
    return best.asReversed().take(limit)
}
