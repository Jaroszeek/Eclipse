package pl.eclipse.core.source.librus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagesModuleTest {
    @Test
    fun flatItemsAreRead() {
        val xml = """
            <response><GetListForType><data>
              <ArrayItem><id>101</id><label>Kowalski Jan</label></ArrayItem>
              <ArrayItem><id>102</id><label>Nowak  Anna</label></ArrayItem>
            </data></GetListForType></response>
        """.trimIndent()
        assertEquals(listOf("101" to "Kowalski Jan", "102" to "Nowak  Anna"), xmlItems(xml).map { it["id"] to it["label"] })
    }

    @Test
    fun onlyInnermostItemsAreRead() {
        // lista pogrupowana: zewnętrzny ArrayItem ma etykietę grupy i zagnieżdżone osoby
        val xml = """
            <data><ArrayItem><label>Klasa 2A</label><list>
              <ArrayItem><id>7</id><label>Wiśniewski Adam</label></ArrayItem>
            </list></ArrayItem></data>
        """.trimIndent()
        assertEquals(listOf(mapOf("id" to "7", "label" to "Wiśniewski Adam")), xmlItems(xml))
    }

    @Test
    fun entitiesAreDecodedInLabels() {
        assertEquals("Żak &amp; Syn", xmlItems("<ArrayItem><label>Żak &amp;amp; Syn</label></ArrayItem>").single()["label"])
    }

    @Test
    fun okResponseIsNotAnError() {
        assertNull(moduleError("<response><SendMessage><status>ok</status><data>12345</data></SendMessage></response>"))
    }

    @Test
    fun knownErrorsAreRecognised() {
        assertEquals(
            LibrusException.Kind.CREDENTIALS,
            assertNotNull(moduleError("<error><message>Niepoprawny login i/lub hasło.</message></error>")).kind,
        )
        assertEquals(
            LibrusException.Kind.MAINTENANCE,
            assertNotNull(moduleError("<html>OffLine</html>")).kind,
        )
        assertNotNull(moduleError("<response><status>error</status><message>Brak odbiorcy.</message></response>")).let {
            assertEquals("Brak odbiorcy.", it.message)
        }
        assertNotNull(moduleError("<response><type>eAccessDeny</type></response>"))
    }

    @Test
    fun valuesAreEscapedForXml() {
        assertEquals("a &amp; b &lt;c&gt;", xmlEscape("a & b <c>"))
        // base64 przechodzi bez zmian — to najczęstszy przypadek
        assertEquals("SGVqLCA9PQ==", xmlEscape("SGVqLCA9PQ=="))
    }

    @Test
    fun entitiesRoundTrip() {
        assertTrue(unescapeEntities(xmlEscape("2 < 3 & \"tak\"")) == "2 < 3 & \"tak\"")
    }
}
