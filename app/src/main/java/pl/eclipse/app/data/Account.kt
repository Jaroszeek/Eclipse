package pl.eclipse.app.data

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pl.eclipse.app.security.CredentialStore
import pl.eclipse.app.security.Credentials
import pl.eclipse.app.sync.SyncWorker
import pl.eclipse.core.source.librus.LibrusException
import pl.eclipse.core.source.librus.LibrusSource
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Powód nieudanego logowania — ekran pokazuje na jego podstawie komunikat z `strings.xml` (SPEC 12.9). */
enum class LoginError { CREDENTIALS, NETWORK, SERVER, CAPTCHA, MAINTENANCE, OTHER }

/** Akcje konta wspólne dla logowania, ustawień i diagnostyki. */
class Account(
    private val context: Context,
    private val db: EclipseDatabase,
    private val settings: SettingsStore,
    private val credentials: CredentialStore,
) {
    val hasCredentials = credentials.hasCredentials

    /** Sprawdza dane w Librusie; przy sukcesie zapisuje je zaszyfrowane i uruchamia pierwszą synchronizację. */
    suspend fun login(email: String, password: String): LoginError? {
        val error = withContext(Dispatchers.IO) {
            try {
                LibrusSource().login(email.trim(), password)
                null
            } catch (e: LibrusException) {
                when (e.kind) {
                    LibrusException.Kind.CREDENTIALS -> LoginError.CREDENTIALS
                    LibrusException.Kind.CAPTCHA -> LoginError.CAPTCHA
                    LibrusException.Kind.MAINTENANCE -> LoginError.MAINTENANCE
                    LibrusException.Kind.SERVER -> LoginError.SERVER
                    LibrusException.Kind.OTHER -> LoginError.OTHER
                }
            } catch (e: IOException) {
                LoginError.NETWORK
            }
        }
        if (error == null) {
            credentials.save(Credentials(email.trim(), password))
            switchSource(demo = false)
        }
        return error
    }

    suspend fun useDemo(demo: Boolean) = switchSource(demo)

    /** „Wyloguj i usuń dane” (SPEC 10.3): dane logowania, dane z Librusa i zaplanowane zadania. */
    suspend fun logout() {
        SyncWorker.cancelAll(context)
        credentials.clear()
        db.records().clear()
        db.records().clearSyncRuns()
        settings.update { it.copy(demoMode = false) }
    }

    /** Przy otwarciu aplikacji: synchronizuj, jeśli ostatnia próba była ponad godzinę temu (SPEC 4.3). */
    suspend fun syncIfStale() {
        val ready = settings.current().demoMode || hasCredentials.first()
        val last = db.syncRuns().latestOnce(1).firstOrNull()?.startedAt ?: 0
        if (ready && System.currentTimeMillis() - last > TimeUnit.HOURS.toMillis(1)) SyncWorker.syncNow(context)
    }

    /** „Wyczyść dane z Librusa i pobierz od nowa” — dane użytkownika zostają. */
    suspend fun refetch() {
        db.records().clear()
        SyncWorker.syncNow(context)
    }

    /** Dane demo i z Librusa się nie mieszają: przy zmianie źródła czyścimy dane szkolne (dane użytkownika zostają). */
    private suspend fun switchSource(demo: Boolean) {
        db.records().clear()
        settings.update { it.copy(demoMode = demo) }
        SyncWorker.schedulePeriodic(context, settings.current().syncIntervalHours)
        SyncWorker.syncNow(context)
    }
}

/** Stan synchronizacji do górnego paska i przeciągnięcia w dół. */
class SyncStatus(private val context: Context) {
    private val work = WorkManager.getInstance(context)

    val running: Flow<Boolean> = combine(
        work.getWorkInfosForUniqueWorkFlow(SyncWorker.PERIODIC),
        work.getWorkInfosForUniqueWorkFlow(SyncWorker.MANUAL),
    ) { periodic, manual -> (periodic + manual).any { it.state == WorkInfo.State.RUNNING } }

    /** Ręczna synchronizacja czeka w kolejce WorkManagera i jeszcze nie ruszyła. */
    val waiting: Flow<Boolean> =
        work.getWorkInfosForUniqueWorkFlow(SyncWorker.MANUAL).map { list -> list.any { it.state == WorkInfo.State.ENQUEUED } }

    val periodicScheduled: Flow<Boolean> =
        work.getWorkInfosForUniqueWorkFlow(SyncWorker.PERIODIC).map { list -> list.any { !it.state.isFinished } }

    fun syncNow(replace: Boolean = false) = SyncWorker.syncNow(context, replace)
}
