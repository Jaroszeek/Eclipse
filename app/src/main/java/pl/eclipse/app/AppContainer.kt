package pl.eclipse.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import pl.eclipse.app.data.Account
import pl.eclipse.app.data.EclipseDatabase
import pl.eclipse.app.data.Messaging
import pl.eclipse.app.data.SyncStatus
import pl.eclipse.app.data.SchoolRepository
import pl.eclipse.app.data.SettingsStore
import pl.eclipse.app.data.TransitStore
import pl.eclipse.app.data.VisitStore
import pl.eclipse.app.data.ensureUserDefaults
import pl.eclipse.app.data.snapshot
import pl.eclipse.app.data.userData
import pl.eclipse.app.notify.Notifier
import pl.eclipse.app.security.CredentialStore
import pl.eclipse.app.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pl.eclipse.core.source.DataSource
import pl.eclipse.core.source.demo.DemoSource
import pl.eclipse.core.source.librus.LibrusSource
import java.io.File
import java.time.Instant

/** Ręczne składanie zależności (bez Hilta) — jedno miejsce, gdzie powstają bazy i źródła danych. */
class AppContainer(context: Context) {
    val database: EclipseDatabase = Room.databaseBuilder(context, EclipseDatabase::class.java, "eclipse.db").build()
    val school = SchoolRepository(database)
    val settings = SettingsStore(context)
    val credentials = CredentialStore(context)
    val notifier = Notifier(context, database)

    /** Zakres dla krótkich zadań aplikacji (np. zapis ustawień) niezależnych od ekranu. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Wspólne dane dla ekranów, współdzielone przez wszystkie ViewModele. */
    val snapshot = school.snapshot(database, scope)
    val user = userData(database, scope)
    val visits = VisitStore(context)
    val account = Account(context, database, settings, credentials)
    val sync = SyncStatus(context)
    val messaging = Messaging(settings, credentials, ::dataSource)

    /** Rozkłady jazdy ZTP (SPEC 17) — osobna baza, żeby dało się je skasować bez ruszania danych szkolnych. */
    val transit = TransitStore(context)

    fun dataSource(demo: Boolean): DataSource = if (demo) DemoSource() else LibrusSource()
}

class EclipseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        container.notifier.createChannels()
        container.scope.launch {
            ensureUserDefaults(container.database)
            val settings = container.settings.current()
            if (settings.demoMode || container.credentials.read() != null) {
                SyncWorker.schedulePeriodic(this@EclipseApp, settings.syncIntervalHours)
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as EclipseApp).container

/**
 * Ostatnia awaria aplikacji — widoczna na ekranie, bo na telefonie nie ma Logcata. Zapisujemy tylko nazwy klas
 * i miejsca w kodzie, bez komunikatów błędów, które mogą zawierać dane z Librusa.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText(report(e)) }
            default?.uncaughtException(thread, e)
        }
    }

    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    private fun report(e: Throwable) = "${Instant.now()} (${BuildConfig.VERSION_NAME})\n" +
        generateSequence(e) { it.cause }.take(3).joinToString("\n") { t ->
            // Początek śladu i miejsca w kodzie Eclipse — reszta to zwykle wnętrze Androida i bibliotek.
            val frames = t.stackTrace.take(4) + t.stackTrace.drop(4).filter { it.className.startsWith("pl.eclipse") }.take(6)
            t.javaClass.name + frames.joinToString("") { "\n  $it" }
        }
}
