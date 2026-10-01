package pl.eclipse.core.transit

import java.io.File
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GtfsTest {
    private fun rows(text: String): List<List<String>> {
        val csv = CsvReader(StringReader(text))
        return generateSequence { csv.next() }.toList()
    }

    @Test
    fun plainRowsAreRead() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\n1,2\n"))
    }

    @Test
    fun lastRowWithoutNewlineIsRead() {
        assertEquals(listOf(listOf("a"), listOf("1")), rows("a\n1"))
    }

    @Test
    fun emptyFieldsAreKept() {
        assertEquals(listOf(listOf("1", "", "3", "")), rows("1,,3,"))
    }

    @Test
    fun quotedFieldsKeepCommasAndQuotes() {
        // nazwy przystanków bywają w cudzysłowach, czasem z przecinkiem
        assertEquals(
            listOf(listOf("stop_1", "Rondo Mogilskie, peron 01", "x")),
            rows("""stop_1,"Rondo Mogilskie, peron 01",x"""),
        )
        assertEquals(listOf(listOf("""on" air""")), rows(""""on"" air""""))
    }

    @Test
    fun quotedFieldCanContainNewline() {
        assertEquals(listOf(listOf("a", "dwie\nlinie"), listOf("b", "c")), rows("a,\"dwie\nlinie\"\nb,c\n"))
    }

    @Test
    fun windowsLineEndingsWork() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), rows("a,b\r\n1,2\r\n"))
    }

    @Test
    fun timesBecomeMinutes() {
        assertEquals(0, gtfsMinutes("00:00:00"))
        assertEquals(845, gtfsMinutes("14:05:00"))
        // kurs po północy — GTFS zapisuje go jako godzinę powyżej 24
        assertEquals(1510, gtfsMinutes("25:10:00"))
        assertNull(gtfsMinutes(""))
        assertNull(gtfsMinutes("bez godziny"))
    }

    @Test
    fun datesBecomeNumbers() {
        assertEquals(20261001, gtfsDate("20261001"))
        assertNull(gtfsDate("2026-10-01"))
    }

    @Test
    fun archiveReadsRowsAndSkipsBom() {
        val file = File.createTempFile("gtfs", ".zip")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("stops.txt"))
            zip.write("﻿stop_id,stop_name\nstop_1,\"Rondo, 01\"\n\n".toByteArray())
            zip.closeEntry()
        }
        GtfsArchive(file).use { archive ->
            assertTrue(archive.has("stops.txt"))
            assertFalse(archive.has("shapes.txt"))
            val read = mutableListOf<String>()
            archive.rows("stops.txt") { read += it.str("stop_id") + "|" + it.str("stop_name") }
            assertEquals(listOf("stop_1|Rondo, 01"), read)
            // brak pliku nie jest błędem — po prostu nic nie czytamy
            var missing = 0
            archive.rows("shapes.txt") { missing++ }
            assertEquals(0, missing)
        }
    }
}
