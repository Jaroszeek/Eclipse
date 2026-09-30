package pl.eclipse.core.source.librus

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTextTest {
    @Test
    fun htmlBecomesPlainText() {
        assertEquals("Dzień dobry,\njutro sprawdzian & kartkówka.", htmlToText("<p>Dzień dobry,<br>jutro sprawdzian &amp; kartkówka.</p>"))
    }

    @Test
    fun base64IsDecoded() {
        val encoded = Base64.getEncoder().encodeToString("<p>Zadanie na piątek</p>".toByteArray())
        assertEquals("Zadanie na piątek", messageText(encoded))
    }

    @Test
    fun plainTextStays() {
        assertEquals("Zwykły tekst bez znaczników", messageText("Zwykły tekst bez znaczników"))
        assertEquals("AAAAAAAA", messageText("AAAAAAAA")) // po odkodowaniu same zera — to nie jest tekst
    }

    @Test
    fun datesAreWarsawTime() {
        val warsaw = ZoneId.of("Europe/Warsaw")
        assertEquals(LocalDateTime.of(2026, 9, 30, 14, 3, 12).atZone(warsaw).toInstant(), warsawInstant("2026-09-30 14:03:12"))
        assertEquals(LocalDateTime.of(2026, 9, 30, 0, 0).atZone(warsaw).toInstant(), warsawInstant("2026-09-30"))
    }
}
