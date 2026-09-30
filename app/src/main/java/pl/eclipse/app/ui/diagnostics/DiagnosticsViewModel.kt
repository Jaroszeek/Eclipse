package pl.eclipse.app.ui.diagnostics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.RecordType
import pl.eclipse.app.data.SyncRunEntity
import pl.eclipse.app.sync.SyncWorker

data class DiagnosticsState(
    val hasCredentials: Boolean = false,
    val demo: Boolean = false,
    val counts: Map<RecordType, Int> = emptyMap(),
    val runs: List<SyncRunEntity> = emptyList(),
    val periodicScheduled: Boolean = false,
    val syncRunning: Boolean = false,
    val loggingIn: Boolean = false,
    val message: String? = null,
)

private data class LocalState(val loggingIn: Boolean = false, val message: String? = null)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val work = WorkManager.getInstance(application)
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<DiagnosticsState> = combine(
        container.credentials.hasCredentials,
        container.settings.settings,
        container.school.counts(),
        container.school.syncRuns(),
        combine(
            work.getWorkInfosForUniqueWorkFlow(SyncWorker.PERIODIC),
            work.getWorkInfosForUniqueWorkFlow(SyncWorker.MANUAL),
            local,
        ) { periodic, manual, local -> Triple(periodic, manual, local) },
    ) { hasCredentials, settings, counts, runs, (periodic, manual, local) ->
        DiagnosticsState(
            hasCredentials = hasCredentials,
            demo = settings.demoMode,
            counts = counts,
            runs = runs,
            periodicScheduled = periodic.any { !it.state.isFinished },
            syncRunning = manual.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } ||
                periodic.any { it.state == WorkInfo.State.RUNNING },
            loggingIn = local.loggingIn,
            message = local.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsState())

    fun login(email: String, password: String) {
        local.update { it.copy(loggingIn = true, message = null) }
        viewModelScope.launch {
            val error = container.account.login(email, password)
            local.update { LocalState(message = if (error == null) string(R.string.login_success) else string(R.string.login_error, error.name)) }
        }
    }

    fun useDemo(demo: Boolean) {
        viewModelScope.launch { container.account.useDemo(demo) }
    }

    fun syncNow() {
        val last = state.value.runs.firstOrNull()?.startedAt ?: 0
        if (System.currentTimeMillis() - last < MANUAL_MIN_INTERVAL_MS) {
            local.update { it.copy(message = string(R.string.sync_too_often)) }
            return
        }
        SyncWorker.syncNow(getApplication())
    }

    fun sendTestNotification() {
        viewModelScope.launch { container.notifier.test() }
    }

    fun logout() {
        viewModelScope.launch {
            container.account.logout()
            local.update { LocalState(message = string(R.string.logout_done)) }
        }
    }

    private fun string(id: Int, vararg args: Any?) = getApplication<Application>().getString(id, *args)

    private companion object {
        const val MANUAL_MIN_INTERVAL_MS = 2 * 60 * 1000L
    }
}
