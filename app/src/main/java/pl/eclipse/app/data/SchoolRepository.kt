package pl.eclipse.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/** Rekord z bazy razem z informacją o zmianach (SPEC 4.4). */
data class Stored<T>(
    val value: T,
    val firstSeenAt: Instant,
    val changedAt: Instant?,
    val removedAt: Instant?,
    val previous: T?,
)

/** Odczyt danych szkolnych z bazy. Zapis robi tylko synchronizacja (`SyncWorker`). */
class SchoolRepository(private val db: EclipseDatabase) {
    fun <T> observe(type: RecordType, serializer: KSerializer<T>): Flow<List<Stored<T>>> =
        db.records().observe(type).map { rows -> rows.map { it.decode(serializer) } }

    fun counts(): Flow<Map<RecordType, Int>> = db.records().counts().map { list -> list.associate { it.type to it.count } }

    fun syncRuns() = db.syncRuns().latest()

    private fun <T> RecordEntity.decode(serializer: KSerializer<T>) = Stored(
        value = JSON.decodeFromString(serializer, json),
        firstSeenAt = Instant.ofEpochMilli(firstSeenAt),
        changedAt = changedAt?.let(Instant::ofEpochMilli),
        removedAt = removedAt?.let(Instant::ofEpochMilli),
        previous = previousJson?.let { JSON.decodeFromString(serializer, it) },
    )
}

/** Wspólna konfiguracja JSON: nowe pola w modelach nie psują starych zapisów. */
val JSON = Json { ignoreUnknownKeys = true }
