package pl.eclipse.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangesTest {
    @Test
    fun sameDataTwiceGivesNoChanges() {
        val data = mapOf("a" to "1", "b" to "2")
        assertTrue(detectChanges(data, data).isEmpty)
    }

    @Test
    fun detectsAddedChangedRemovedAndRestored() {
        val changes = detectChanges(
            old = mapOf("a" to "1", "b" to "2", "c" to "3", "d" to "4", "old" to "5"),
            new = mapOf("a" to "1", "b" to "22", "d" to "4", "e" to "6"),
            alreadyRemoved = setOf("d"),
            inScope = { it != "old" }, // „old” jest spoza pobranego zakresu dat
        )
        assertEquals(setOf("e"), changes.added)
        assertEquals(setOf("b", "d"), changes.changed) // „d” wrócił po usunięciu
        assertEquals(setOf("c"), changes.removed)
    }
}
