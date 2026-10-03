package pl.eclipse.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// SPEC 17.3. Nazwy miejsc i ulic Krakowa z OpenStreetMap — żeby dało się szukać „Galeria Krakowska”
// albo „Karmelicka”, a nie tylko po nazwie przystanku.
//
// Dane leżą w pliku w aplikacji (`assets/places.tsv`; w gotowym .apk zajmuje ok. 0,35 MB, bo pakiet
// sam go kompresuje), a nie są pobierane: jest ich mało,
// nazwy ulic się nie zmieniają, a dzięki temu żadne wyszukiwanie nie opuszcza telefonu (CLAUDE.md).
// Plik powstaje skryptem `tools/osm_places.py` i odświeża się razem z wydaniem aplikacji.

/** Miejsce na mapie: nazwa, rodzaj z OpenStreetMap i jeden z jego punktów. */
data class TransitPlace(val name: String, val kind: String, val lat: Double, val lon: Double)

/**
 * Wyszukiwarka miejsc. Cały zbiór (ok. 26 tysięcy punktów) trzymamy w pamięci — przy tej wielkości
 * przejrzenie wszystkiego jest szybsze niż utrzymywanie kolejnej bazy i jej aktualizacji.
 */
class PlaceIndex(private val context: Context) {
    private class Entry(val name: String, val norm: String, val kind: String, val lat: Double, val lon: Double)

    private val loading = Mutex()
    @Volatile private var entries: List<Entry>? = null

    private suspend fun load(): List<Entry> {
        entries?.let { return it }
        return loading.withLock {
            entries ?: withContext(Dispatchers.IO) {
                val kinds = HashMap<String, String>()
                val read = mutableListOf<Entry>()
                runCatching {
                    context.assets.open(FILE).bufferedReader().forEachLine { line ->
                        val parts = line.split('\t')
                        if (parts.size == 4) {
                            val lat = parts[2].toDoubleOrNull()
                            val lon = parts[3].toDoubleOrNull()
                            if (lat != null && lon != null) {
                                // rodzajów jest kilkadziesiąt na 26 tysięcy wierszy — jeden napis na rodzaj wystarczy
                                val kind = kinds.getOrPut(parts[1]) { parts[1] }
                                read += Entry(parts[0], normalizeName(parts[0]), kind, lat, lon)
                            }
                        }
                    }
                }
                read.also { entries = it }
            }
        }
    }

    /** Miejsca pasujące do wpisanego tekstu, po jednym wierszu na nazwę; najpierw te zaczynające się od niej. */
    suspend fun search(query: String, limit: Int = 20): List<TransitPlace> {
        val needle = normalizeName(query.trim())
        if (needle.length < 2) return emptyList()
        val all = load()
        val byName = LinkedHashMap<String, Entry>()
        all.forEach { entry ->
            if (entry.norm.contains(needle)) byName.putIfAbsent(entry.norm, entry)
        }
        return byName.values
            .sortedWith(compareByDescending<Entry> { it.norm.startsWith(needle) }.thenBy { it.norm.length }.thenBy { it.norm })
            .take(limit)
            .map { TransitPlace(it.name, it.kind, it.lat, it.lon) }
    }

    /**
     * Wszystkie punkty o tej nazwie. Ulica jest w OpenStreetMap pocięta na odcinki, więc „Karmelicka”
     * to kilkanaście punktów — przystanku szukamy od najbliższego z nich, nie od środka ulicy.
     */
    suspend fun points(name: String): List<Pair<Double, Double>> {
        val needle = normalizeName(name)
        return load().filter { it.norm == needle }.map { it.lat to it.lon }
    }

    private companion object {
        const val FILE = "places.tsv"
    }
}
