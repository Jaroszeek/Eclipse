package pl.eclipse.core.sync

/** Wynik porównania rekordów jednego rodzaju (SPEC 4.4). */
data class Changes(val added: Set<String>, val changed: Set<String>, val removed: Set<String>) {
    val isEmpty get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

/**
 * Porównuje zapisane rekordy [old] (klucz → dane) z pobranymi [new].
 * [alreadyRemoved] — klucze oznaczone wcześniej jako usunięte; gdy wrócą, liczą się jako zmienione.
 * [inScope] — czy rekord był w zakresie pobierania (np. lekcja z pobranego tygodnia); tylko takie mogą zniknąć.
 */
fun detectChanges(
    old: Map<String, String>,
    new: Map<String, String>,
    alreadyRemoved: Set<String> = emptySet(),
    inScope: (String) -> Boolean = { true },
): Changes = Changes(
    added = new.keys - old.keys,
    changed = new.keys.filter { it in old && (old[it] != new[it] || it in alreadyRemoved) }.toSet(),
    removed = (old.keys - new.keys - alreadyRemoved).filter(inScope).toSet(),
)
