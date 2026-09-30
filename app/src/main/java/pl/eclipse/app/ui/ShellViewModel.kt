package pl.eclipse.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.eclipse.app.container
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.Flags
import pl.eclipse.app.data.ThemeMode
import pl.eclipse.core.calc.WarningLevel

data class ShellState(
    val ready: Boolean = false,
    val loggedIn: Boolean = false,
    val firstSyncPending: Boolean = false,
    val askPermission: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val syncRunning: Boolean = false,
    /** Ręczna synchronizacja czeka w kolejce i jeszcze nie ruszyła. */
    val syncWaiting: Boolean = false,
    /** Błędy ostatniej synchronizacji, jeśli się nie udała. */
    val lastSyncError: String? = null,
    val syncFailing: Boolean = false,
    val lastSyncAt: Long? = null,
    val newGrades: Int = 0,
    val unreadInbox: Int = 0,
    val critical: Int = 0,
    val unreadNotifications: Int = 0,
)

class ShellViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container

    val state: StateFlow<ShellState> = combine(
        container.account.hasCredentials,
        container.settings.settings,
        container.snapshot,
        container.user,
        combine(container.sync.running, container.sync.waiting, container.visits.observe("grades"), ::Triple),
    ) { hasCredentials, settings, snapshot, user, (running, waiting, gradesVisit) ->
        val loggedIn = hasCredentials || settings.demoMode
        val insights = Insights(snapshot, user, settings)
        val read = user.keys(Flags.READ)
        ShellState(
            ready = true,
            loggedIn = loggedIn,
            // Do pierwszej udanej synchronizacji; po nieudanej — dopóki nie ma żadnych danych (ekran pokazuje błąd).
            firstSyncPending = loggedIn && snapshot.lastSuccessAt == null && (snapshot.lastSync == null || snapshot.isEmpty),
            askPermission = loggedIn && !settings.permissionAsked && !notificationsGranted(),
            settings = settings,
            syncRunning = running,
            syncWaiting = waiting,
            lastSyncError = snapshot.lastSync?.takeIf { !it.success }?.errors,
            // po 3 nieudanych próbach z rzędu — baner (SPEC 4.3)
            syncFailing = snapshot.recentRuns.size >= 3 && snapshot.recentRuns.take(3).none { it.success },
            lastSyncAt = snapshot.lastSuccessAt,
            newGrades = snapshot.grades.count { it.removedAt == null && gradesVisit > 0 && it.firstSeenAt.toEpochMilli() > gradesVisit },
            unreadInbox = (snapshot.messages.map { it.value.sourceKey } + snapshot.announcements.map { it.value.sourceKey } + snapshot.notes.map { it.value.sourceKey })
                .count { it !in read },
            critical = insights.warnings.count { it.level == WarningLevel.CRITICAL },
            unreadNotifications = user.notifications.count { it.readAt == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellState())

    fun syncNow() = container.sync.syncNow()

    /** „Spróbuj ponownie” na ekranie pierwszej synchronizacji: zastępuje zadanie, które mogło utknąć w kolejce. */
    fun retrySync() = container.sync.syncNow(replace = true)

    fun logout() {
        viewModelScope.launch { container.account.logout() }
    }

    fun permissionAsked() {
        viewModelScope.launch { container.settings.update { it.copy(permissionAsked = true) } }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            container.settings.update {
                it.copy(themeMode = when (it.themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                })
            }
        }
    }

    private fun notificationsGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
