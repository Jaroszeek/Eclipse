package pl.eclipse.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.eclipse.core.transit.GtfsArchive
import pl.eclipse.core.transit.TransitFeed
import pl.eclipse.core.transit.Journey
import pl.eclipse.core.transit.TripRun
import pl.eclipse.core.transit.TripStop
import pl.eclipse.core.transit.downloadFeed
import pl.eclipse.core.transit.findJourneys
import pl.eclipse.core.transit.gtfsDate
import pl.eclipse.core.transit.gtfsMinutes
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.time.LocalDate

// SPEC 17.1. Rozkłady trzymamy w osobnej bazie `transit.db`, żeby dało się je skasować i pobrać
// od nowa bez ruszania danych szkolnych i własnych.
//
// Zwykłe SQLite zamiast Room: bazę wgrywamy zawsze w całości od nowa, więc migracje są niepotrzebne,
// a wstawianie kilkuset tysięcy wierszy gotowym zapytaniem jest dużo szybsze niż przez Room.

/** Węzeł przystankowy: wszystkie perony o tej samej nazwie to jeden punkt (SPEC 17.1). */
data class TransitNode(val id: Int, val name: String)

/** Połączenie gotowe do pokazania: przystanek przesiadkowy z nazwą zamiast numeru. */
data class TransitJourney(val journey: Journey, val transferName: String?)

/** Co wiemy o wgranym rozkładzie. */
data class TransitInfo(val version: String, val nodes: Int, val trips: Int, val importedAt: Long)

/** Postęp pobierania i wgrywania rozkładów. */
data class TransitProgress(val feed: TransitFeed?, val phase: Phase, val done: Long = 0, val total: Long = 0) {
    enum class Phase { DOWNLOAD, IMPORT, INDEX }

    /** Ułamek do paska postępu albo null, gdy nie znamy całości. */
    val fraction: Float? get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
}

class TransitStore(private val context: Context) {
    private val file: File get() = context.getDatabasePath(DB_NAME)
    private var cached: SQLiteDatabase? = null

    /** Baza do czytania albo null, gdy rozkładów jeszcze nie ma (albo są w starym układzie tabel). */
    @Synchronized
    private fun open(): SQLiteDatabase? {
        cached?.let { if (it.isOpen) return it }
        cached = null
        if (!file.exists()) return null
        val db = runCatching { SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) }.getOrNull() ?: return null
        if (db.meta("schema")?.toIntOrNull() != SCHEMA) {
            db.close()
            return null
        }
        cached = db
        return db
    }

    @Synchronized
    private fun closeDatabase() {
        cached?.close()
        cached = null
    }

    suspend fun info(): TransitInfo? = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext null
        TransitInfo(
            version = db.meta("version").orEmpty(),
            nodes = db.meta("nodes")?.toIntOrNull() ?: 0,
            trips = db.meta("trips")?.toIntOrNull() ?: 0,
            importedAt = db.meta("importedAt")?.toLongOrNull() ?: 0,
        )
    }

    /** Przystanki pasujące do wpisanego tekstu; bez ogonków i wielkości liter (wpisane „swietokrzyska” znajdzie „Świętokrzyska”). */
    suspend fun nodes(query: String, limit: Int = 40): List<TransitNode> = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext emptyList()
        val needle = normalize(query.trim())
        val cursor = if (needle.isEmpty()) {
            db.rawQuery("SELECT id, name FROM node ORDER BY name LIMIT ?", arrayOf(limit.toString()))
        } else {
            // najpierw przystanki zaczynające się od wpisanego tekstu, potem reszta
            db.rawQuery(
                "SELECT id, name FROM node WHERE norm LIKE ? ORDER BY (norm LIKE ?) DESC, name LIMIT ?",
                arrayOf("%$needle%", "$needle%", limit.toString()),
            )
        }
        cursor.use { buildList { while (it.moveToNext()) add(TransitNode(it.getInt(0), it.getString(1))) } }
    }

    /** Połączenia z [from] do [to] odjeżdżające nie wcześniej niż [minute] minut po północy dnia [date] (SPEC 17.2). */
    suspend fun journeys(from: Int, to: Int, date: LocalDate, minute: Int): List<TransitJourney> = withContext(Dispatchers.IO) {
        val db = open() ?: return@withContext emptyList()
        // kursy po północy mają w rozkładzie godziny powyżej 24, więc dokładamy wczorajsze i przesuwamy je o dobę
        val runs = loadRuns(db, from, to, date, minute, 0) + loadRuns(db, from, to, date.minusDays(1), minute, -DAY)
        val found = findJourneys(from, to, minute, runs)
        val names = nodeNames(db, found.mapNotNull { it.transfer }.toSet())
        found.map { TransitJourney(it, it.transfer?.let(names::get)) }
    }

    /** Kursy, które danego dnia zatrzymują się na przystanku początkowym albo docelowym w oknie wyszukiwania. */
    private fun loadRuns(db: SQLiteDatabase, from: Int, to: Int, date: LocalDate, minute: Int, offset: Int): List<TripRun> {
        val start = minute - offset
        val args = arrayOf(from, to, start, start + WINDOW, dayNumber(date)).map(Int::toString).toTypedArray()
        val runs = mutableListOf<TripRun>()
        db.rawQuery(RUNS, args).use { cursor ->
            var trip = -1
            var line = ""
            var tram = false
            var head = ""
            var stops = mutableListOf<TripStop>()
            while (cursor.moveToNext()) {
                if (cursor.getInt(0) != trip) {
                    if (trip != -1) runs += TripRun(trip, line, tram, head, stops)
                    trip = cursor.getInt(0)
                    stops = mutableListOf()
                    line = cursor.getString(4)
                    tram = cursor.getInt(5) == 1
                    head = cursor.getString(6)
                }
                stops += TripStop(cursor.getInt(1), cursor.getInt(2) + offset, cursor.getInt(3) + offset)
            }
            if (trip != -1) runs += TripRun(trip, line, tram, head, stops)
        }
        return runs
    }

    /** Nazwy węzłów po numerach; numery pochodzą z bazy, więc można je wstawić wprost do zapytania. */
    private fun nodeNames(db: SQLiteDatabase, ids: Set<Int>): Map<Int, String> {
        if (ids.isEmpty()) return emptyMap()
        return db.rawQuery("SELECT id, name FROM node WHERE id IN (${ids.joinToString(",")})", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getString(1)) }
        }
    }

    /** Pobiera oba rozkłady i wgrywa je do nowej bazy; stara zostaje do końca, więc nieudane pobieranie nic nie psuje. */
    suspend fun refresh(onProgress: (TransitProgress) -> Unit) = withContext(Dispatchers.IO) {
        val downloads = File(context.cacheDir, "gtfs").apply { mkdirs() }
        val archives = TransitFeed.entries.map { feed ->
            val target = File(downloads, feed.name + ".zip")
            downloadFeed(feed, target) { done, total -> onProgress(TransitProgress(feed, TransitProgress.Phase.DOWNLOAD, done, total)) }
            feed to target
        }
        val temp = File(file.parentFile, "transit-new.db")
        SQLiteDatabase.deleteDatabase(temp)
        val catalog = Catalog()
        var version = ""
        val db = SQLiteDatabase.openOrCreateDatabase(temp, null)
        try {
            // baza powstaje od zera, więc dziennik transakcji jest zbędny — bez niego wgrywanie jest dużo szybsze
            // PRAGMA journal_mode zwraca wiersz, więc musi iść przez rawQuery, a nie execSQL
            db.rawQuery("PRAGMA journal_mode = OFF", null).use { it.moveToFirst() }
            db.execSQL("PRAGMA synchronous = OFF")
            CREATE.forEach(db::execSQL)
            archives.forEach { (feed, archive) ->
                version = maxOf(version, importFeed(db, feed, archive, catalog, onProgress))
            }
            onProgress(TransitProgress(null, TransitProgress.Phase.INDEX))
            INDEXES.forEach(db::execSQL)
            db.transaction {
                mapOf(
                    "schema" to SCHEMA.toString(),
                    "version" to version,
                    "nodes" to catalog.nodes.size.toString(),
                    "trips" to catalog.trips.size.toString(),
                    "importedAt" to System.currentTimeMillis().toString(),
                ).forEach { (key, value) -> execSQL("INSERT INTO meta(k, v) VALUES(?, ?)", arrayOf(key, value)) }
            }
        } finally {
            db.close()
        }
        closeDatabase()
        SQLiteDatabase.deleteDatabase(file)
        if (!temp.renameTo(file)) throw IOException("Nie udało się zapisać bazy rozkładów")
        archives.forEach { (_, archive) -> archive.delete() }
    }

    /** Kasuje rozkłady. */
    suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        closeDatabase()
        SQLiteDatabase.deleteDatabase(file)
    }

    /** Wgrywa jeden rozkład; zwraca jego wersję z `feed_info.txt`. */
    private fun importFeed(
        db: SQLiteDatabase,
        feed: TransitFeed,
        archive: File,
        catalog: Catalog,
        onProgress: (TransitProgress) -> Unit,
    ): String = GtfsArchive(archive).use { gtfs ->
        var version = ""
        gtfs.rows("feed_info.txt") { row -> version = row.str("feed_version").orEmpty() }

        db.transaction {
            val node = compileStatement("INSERT OR IGNORE INTO node(id, name, norm) VALUES(?, ?, ?)")
            val stop = compileStatement("INSERT OR REPLACE INTO stop(id, node) VALUES(?, ?)")
            gtfs.rows("stops.txt") { row ->
                val id = row.str("stop_id") ?: return@rows
                val name = row.str("stop_name")?.trim() ?: return@rows
                val norm = normalize(name)
                val nodeId = catalog.nodes.id(norm)
                node.bindLong(1, nodeId.toLong())
                node.bindString(2, name)
                node.bindString(3, norm)
                node.executeInsert()
                stop.bindLong(1, catalog.stops.id(feed.key(id)).toLong())
                stop.bindLong(2, nodeId.toLong())
                stop.executeInsert()
            }
        }

        db.transaction {
            val route = compileStatement("INSERT OR REPLACE INTO route(id, name, tram) VALUES(?, ?, ?)")
            gtfs.rows("routes.txt") { row ->
                val id = row.str("route_id") ?: return@rows
                route.bindLong(1, catalog.routes.id(feed.key(id)).toLong())
                route.bindString(2, row.str("route_short_name") ?: row.str("route_long_name") ?: "?")
                route.bindLong(3, if (feed == TransitFeed.TRAM) 1 else 0)
                route.executeInsert()
            }
        }

        db.transaction {
            val trip = compileStatement("INSERT OR REPLACE INTO trip(id, route, service, head) VALUES(?, ?, ?, ?)")
            gtfs.rows("trips.txt") { row ->
                val id = row.str("trip_id") ?: return@rows
                val routeId = catalog.routes.get(row.str("route_id")?.let(feed::key)) ?: return@rows
                val serviceId = catalog.services.id(feed.key(row.str("service_id") ?: return@rows))
                trip.bindLong(1, catalog.trips.id(feed.key(id)).toLong())
                trip.bindLong(2, routeId.toLong())
                trip.bindLong(3, serviceId.toLong())
                trip.bindString(4, row.str("trip_headsign").orEmpty())
                trip.executeInsert()
            }
        }

        importServiceDays(db, feed, gtfs, catalog)
        importStopTimes(db, feed, gtfs, catalog, onProgress)
        version
    }

    /**
     * Dni kursowania: z `calendar.txt` rozwijamy maskę dni tygodnia na konkretne daty,
     * a `calendar_dates.txt` dokłada wyjątki (1 = kursuje, 2 = nie kursuje).
     * W rozkładach ZTP maski bywają puste i wszystko siedzi w wyjątkach — dlatego czytamy oba pliki.
     */
    private fun importServiceDays(db: SQLiteDatabase, feed: TransitFeed, gtfs: GtfsArchive, catalog: Catalog) {
        val days = HashMap<Int, MutableSet<Int>>()
        gtfs.rows("calendar.txt") { row ->
            val service = catalog.services.id(feed.key(row.str("service_id") ?: return@rows))
            val start = gtfsDate(row.str("start_date").orEmpty()) ?: return@rows
            val end = gtfsDate(row.str("end_date").orEmpty()) ?: return@rows
            val mask = WEEKDAYS.withIndex().filter { (_, name) -> row.int(name) == 1 }.map { it.index + 1 }.toSet()
            if (mask.isEmpty()) return@rows
            var day = localDate(start)
            val last = localDate(end)
            while (!day.isAfter(last)) {
                if (day.dayOfWeek.value in mask) days.getOrPut(service) { mutableSetOf() } += dayNumber(day)
                day = day.plusDays(1)
            }
        }
        gtfs.rows("calendar_dates.txt") { row ->
            val service = catalog.services.id(feed.key(row.str("service_id") ?: return@rows))
            val date = gtfsDate(row.str("date").orEmpty()) ?: return@rows
            val dates = days.getOrPut(service) { mutableSetOf() }
            if (row.int("exception_type") == 2) dates -= date else dates += date
        }
        db.transaction {
            val insert = compileStatement("INSERT INTO service_day(service, day) VALUES(?, ?)")
            days.forEach { (service, dates) ->
                dates.forEach { date ->
                    insert.bindLong(1, service.toLong())
                    insert.bindLong(2, date.toLong())
                    insert.executeInsert()
                }
            }
        }
    }

    /** Godziny na przystankach — kilkaset tysięcy wierszy na rozkład, więc transakcję zamykamy co jakiś czas. */
    private fun importStopTimes(
        db: SQLiteDatabase,
        feed: TransitFeed,
        gtfs: GtfsArchive,
        catalog: Catalog,
        onProgress: (TransitProgress) -> Unit,
    ) {
        val total = gtfs.size("stop_times.txt")
        val insert = db.compileStatement("INSERT OR REPLACE INTO stop_time(trip, seq, stop, arr, dep) VALUES(?, ?, ?, ?, ?)")
        var rows = 0
        db.beginTransaction()
        try {
            gtfs.rows("stop_times.txt") { row ->
                val trip = catalog.trips.get(row.str("trip_id")?.let(feed::key)) ?: return@rows
                val stop = catalog.stops.get(row.str("stop_id")?.let(feed::key)) ?: return@rows
                val arrival = gtfsMinutes(row.str("arrival_time").orEmpty())
                val departure = gtfsMinutes(row.str("departure_time").orEmpty()) ?: arrival ?: return@rows
                insert.bindLong(1, trip.toLong())
                insert.bindLong(2, (row.int("stop_sequence") ?: 0).toLong())
                insert.bindLong(3, stop.toLong())
                insert.bindLong(4, (arrival ?: departure).toLong())
                insert.bindLong(5, departure.toLong())
                insert.executeInsert()
                if (++rows % 50_000 == 0) {
                    db.setTransactionSuccessful()
                    db.endTransaction()
                    db.beginTransaction()
                    onProgress(TransitProgress(feed, TransitProgress.Phase.IMPORT, gtfs.bytesRead, total))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val DB_NAME = "transit.db"

        /** Ile minut rozkładu bierzemy pod uwagę przy jednym szukaniu. */
        const val WINDOW = 180
        const val DAY = 24 * 60

        /** Kursy z pełną trasą — wybrane po tym, że dotykają przystanku początkowego albo docelowego w oknie. */
        val RUNS = """
            SELECT st.trip, s.node, st.arr, st.dep, r.name, r.tram, t.head
            FROM stop_time st
            JOIN stop s ON s.id = st.stop
            JOIN trip t ON t.id = st.trip
            JOIN route r ON r.id = t.route
            WHERE st.trip IN (
                SELECT st2.trip FROM stop_time st2
                JOIN stop s2 ON s2.id = st2.stop
                JOIN trip t2 ON t2.id = st2.trip
                WHERE s2.node IN (?, ?) AND st2.dep BETWEEN ? AND ?
                  AND EXISTS(SELECT 1 FROM service_day d WHERE d.service = t2.service AND d.day = ?)
            )
            ORDER BY st.trip, st.seq
        """.trimIndent()

        /** Numer układu tabel — gdy go zmienimy, stara baza jest odrzucana i rozkłady pobierają się od nowa. */
        const val SCHEMA = 1

        val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

        val CREATE = listOf(
            "CREATE TABLE node(id INTEGER PRIMARY KEY, name TEXT NOT NULL, norm TEXT NOT NULL)",
            "CREATE TABLE stop(id INTEGER PRIMARY KEY, node INTEGER NOT NULL)",
            "CREATE TABLE route(id INTEGER PRIMARY KEY, name TEXT NOT NULL, tram INTEGER NOT NULL)",
            "CREATE TABLE trip(id INTEGER PRIMARY KEY, route INTEGER NOT NULL, service INTEGER NOT NULL, head TEXT NOT NULL)",
            // WITHOUT ROWID: tabela jest jednocześnie indeksem po (kurs, kolejność) — baza jest przez to o jedną trzecią mniejsza
            "CREATE TABLE stop_time(trip INTEGER NOT NULL, seq INTEGER NOT NULL, stop INTEGER NOT NULL, arr INTEGER NOT NULL, dep INTEGER NOT NULL, PRIMARY KEY(trip, seq)) WITHOUT ROWID",
            "CREATE TABLE service_day(service INTEGER NOT NULL, day INTEGER NOT NULL)",
            "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)",
        )

        val INDEXES = listOf(
            "CREATE INDEX stop_by_node ON stop(node)",
            "CREATE INDEX node_by_norm ON node(norm)",
            "CREATE INDEX time_by_stop ON stop_time(stop, dep)",
            "CREATE INDEX day_services ON service_day(day, service)",
        )
    }
}

/** Tekstowe identyfikatory z GTFS zamieniamy na liczby — bez tego baza byłaby kilka razy większa (SPEC 17.1). */
private class Ids {
    private val map = HashMap<String, Int>()
    val size: Int get() = map.size
    fun id(key: String): Int = map.getOrPut(key) { map.size + 1 }
    fun get(key: String?): Int? = key?.let { map[it] }
}

/** Numery nadane przy wgrywaniu. Przystanki są wspólne dla obu rozkładów — na tym stoją przesiadki tramwaj ↔ autobus. */
private class Catalog {
    val nodes = Ids()
    val stops = Ids()
    val routes = Ids()
    val trips = Ids()
    val services = Ids()
}

/** Identyfikatory powtarzają się między rozkładem tramwajowym i autobusowym, więc doklejamy numer rozkładu. */
private fun TransitFeed.key(id: String): String = "$ordinal:$id"

private fun SQLiteDatabase.meta(key: String): String? =
    runCatching {
        rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

private val COMBINING = Regex("\\p{Mn}+")

/** Nazwa przystanku bez ogonków i wielkich liter — po tym szukamy i po tym scalamy perony w jeden węzeł. */
private fun normalize(name: String): String =
    Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(COMBINING, "").replace('ł', 'l')

private fun localDate(number: Int): LocalDate = LocalDate.of(number / 10000, number / 100 % 100, number % 100)

private fun dayNumber(date: LocalDate): Int = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
