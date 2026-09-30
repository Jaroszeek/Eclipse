package pl.eclipse.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.eclipse.app.security.CredentialStore
import pl.eclipse.core.model.Recipient
import pl.eclipse.core.source.DataSource
import pl.eclipse.core.source.SendResult
import pl.eclipse.core.source.librus.LibrusException

/**
 * Pisanie wiadomości (SPEC 12.7). Osobna, zalogowana sesja — synchronizacja w tle ma swoją, więc jedna
 * nie przerywa drugiej. Sesja zostaje w pamięci do zamknięcia aplikacji.
 */
class Messaging(
    private val settings: SettingsStore,
    private val credentials: CredentialStore,
    private val source: (Boolean) -> DataSource,
) {
    private val lock = Mutex() // jedno zapytanie naraz — szanujemy serwery Librusa
    private var session: DataSource? = null

    suspend fun recipients(): List<Recipient> = withSession { it.messageRecipients() }

    suspend fun send(recipientIds: List<String>, subject: String, text: String): SendResult =
        withSession { it.sendMessage(recipientIds, subject, text) }

    private suspend fun <T> withSession(action: (DataSource) -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            val open = session ?: open().also { session = it }
            try {
                action(open)
            } catch (e: LibrusException) {
                // Wygasła sesja: jedna próba od nowa, żeby użytkownik nie musiał nic klikać.
                if (e.kind != LibrusException.Kind.CREDENTIALS) throw e
                session = null
                action(open().also { session = it })
            }
        }
    }

    private suspend fun open(): DataSource {
        val demo = settings.current().demoMode
        return source(demo).also { fresh ->
            if (!demo) {
                val saved = credentials.read() ?: throw LibrusException(
                    "Brak danych logowania — zaloguj się ponownie.",
                    LibrusException.Kind.CREDENTIALS,
                )
                fresh.login(saved.email, saved.password)
            }
        }
    }
}
