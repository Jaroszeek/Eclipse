package pl.eclipse.core.transit

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.Reader
import java.util.zip.ZipFile

// Czytanie rozkładów w formacie GTFS (SPEC 17.1). Pliki mają po kilkadziesiąt megabajtów,
// więc wszystko czytamy strumieniowo — nigdy nie trzymamy całego pliku w pamięci.

/**
 * Czyta CSV zgodnie z GTFS: pola w cudzysłowach, przecinki i znaki nowej linii wewnątrz pola,
 * podwójny cudzysłów jako zwykły cudzysłów.
 */
internal class CsvReader(private val input: Reader) {
    private var pushed = NOTHING

    private fun read(): Int {
        if (pushed != NOTHING) {
            val c = pushed
            pushed = NOTHING
            return c
        }
        return input.read()
    }

    /** Kolejny wiersz albo null na końcu pliku. */
    fun next(): List<String>? {
        var c = read()
        if (c == -1) return null
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        while (true) {
            when {
                c == -1 -> {
                    row += field.toString()
                    return row
                }
                quoted && c == QUOTE -> {
                    val next = read()
                    if (next == QUOTE) {
                        field.append('"')
                    } else {
                        quoted = false
                        pushed = next
                    }
                }
                quoted -> field.append(c.toChar())
                c == QUOTE && field.isEmpty() -> quoted = true
                c == COMMA -> {
                    row += field.toString()
                    field.setLength(0)
                }
                c == CR -> Unit // koniec wiersza rozpoznajemy po \n
                c == LF -> {
                    row += field.toString()
                    return row
                }
                else -> field.append(c.toChar())
            }
            c = read()
        }
    }

    private companion object {
        const val NOTHING = -2
        const val QUOTE = '"'.code
        const val COMMA = ','.code
        const val CR = '\r'.code
        const val LF = '\n'.code
    }
}

/** Jeden wiersz pliku GTFS. Puste pole traktujemy jak brak wartości. */
class GtfsRow internal constructor(private val columns: Map<String, Int>, private val values: List<String>) {
    fun str(name: String): String? = columns[name]?.let { values.getOrNull(it) }?.takeIf { it.isNotEmpty() }
    fun int(name: String): Int? = str(name)?.toIntOrNull()
    fun num(name: String): Double? = str(name)?.toDoubleOrNull()
}

/** Archiwum .zip z rozkładem GTFS; czyta wskazane pliki wiersz po wierszu. */
class GtfsArchive(file: File) : Closeable {
    private val zip = ZipFile(file)

    /** Bajty przeczytane z pliku czytanego w tej chwili — do pokazywania postępu importu. */
    var bytesRead: Long = 0
        private set

    fun has(entry: String): Boolean = zip.getEntry(entry) != null

    /** Rozmiar pliku po rozpakowaniu; 0, gdy archiwum go nie ma. */
    fun size(entry: String): Long = zip.getEntry(entry)?.size ?: 0

    /**
     * Woła [onRow] dla każdego wiersza pliku [entry]. Wiersze o innej liczbie kolumn niż nagłówek pomijamy —
     * w praktyce to puste linie na końcu pliku.
     */
    fun rows(entry: String, onRow: (GtfsRow) -> Unit) {
        val zipEntry = zip.getEntry(entry) ?: return
        bytesRead = 0
        val counting = object : FilterInputStream(zip.getInputStream(zipEntry)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
        }
        counting.bufferedReader().use { reader ->
            val csv = CsvReader(reader)
            val header = csv.next() ?: return
            // pierwszy plik bywa z BOM-em, który doklejałby się do nazwy pierwszej kolumny
            val columns = header.mapIndexed { i, name -> name.removePrefix("﻿").trim() to i }.toMap()
            while (true) {
                val values = csv.next() ?: return
                if (values.size == header.size) onRow(GtfsRow(columns, values))
            }
        }
    }

    override fun close() = zip.close()
}

/**
 * Godzina z GTFS („14:05:00”) na minuty od północy. Kursy po północy mają godziny powyżej 24
 * („25:10:00” to 1:10 następnego dnia) — zostawiamy je takimi, bo tak liczy się je względem dnia kursowania.
 */
fun gtfsMinutes(time: String): Int? {
    val parts = time.trim().split(':')
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    if (minutes !in 0..59) return null
    return hours * 60 + minutes
}

/** Data z GTFS („20261001”) jako liczba — w tej postaci trzymamy ją też w bazie. */
fun gtfsDate(value: String): Int? = value.trim().takeIf { it.length == 8 }?.toIntOrNull()
