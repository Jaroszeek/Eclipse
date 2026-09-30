package pl.eclipse.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.eclipse.app.container
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.EventTemplateEntity
import pl.eclipse.app.data.LabelAssignmentEntity
import pl.eclipse.app.data.LabelEntity
import pl.eclipse.app.data.SyncRunEntity
import pl.eclipse.app.sync.SyncWorker
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject

data class SettingsState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val email: String? = null,
    val labels: List<LabelEntity> = emptyList(),
    val templates: List<EventTemplateEntity> = emptyList(),
    /** Reguły „wszystkie lekcje przedmiotu” z nazwą przedmiotu i etykiety. */
    val subjectRules: List<Triple<LabelAssignmentEntity, String, LabelEntity?>> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val student: StudentInfo? = null,
    /** Typy frekwencji z Librusa: skrót → kategoria (mapowanie z rekonesansu). */
    val attendanceTypes: List<Pair<String, AttendanceCategory>> = emptyList(),
    val runs: List<SyncRunEntity> = emptyList(),
    val periodicScheduled: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val email = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch { email.value = container.credentials.read()?.email }
    }

    val state: StateFlow<SettingsState> = combine(
        container.settings.settings,
        container.snapshot,
        container.user,
        combine(email, message, container.sync.periodicScheduled) { e, m, p -> Triple(e, m, p) },
    ) { settings, snapshot, user, (email, message, periodic) ->
        val names = snapshot.subjects.associate { it.sourceKey to it.name }
        val labels = user.labels.associateBy { it.id }
        SettingsState(
            loading = false,
            settings = settings,
            email = email,
            labels = user.labels,
            templates = user.templates,
            subjectRules = user.assignments.filter { it.target == "SUBJECT_ALL_LESSONS" }.map { Triple(it, names[it.targetKey] ?: it.targetKey, labels[it.labelId]) },
            subjects = snapshot.subjects,
            student = snapshot.student,
            attendanceTypes = snapshot.attendance.map { it.typeShort to it.category }.distinct().sortedBy { it.first },
            runs = snapshot.recentRuns.take(8),
            periodicScheduled = periodic,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val before = container.settings.current()
            container.settings.update(transform)
            val after = container.settings.current()
            if (before.syncIntervalHours != after.syncIntervalHours) SyncWorker.schedulePeriodic(getApplication(), after.syncIntervalHours)
        }
    }

    fun say(text: String) {
        message.value = text
    }

    fun useDemo(demo: Boolean) {
        viewModelScope.launch { container.account.useDemo(demo) }
    }

    fun logout() {
        viewModelScope.launch { container.account.logout() }
    }

    fun refetch() {
        viewModelScope.launch { container.account.refetch() }
    }

    fun syncNow() = container.sync.syncNow()

    fun sendTest() {
        viewModelScope.launch { container.notifier.test() }
    }

    fun addLabel(name: String, color: Int) {
        viewModelScope.launch { container.database.user().upsertLabel(LabelEntity(name = name.trim(), color = color)) }
    }

    fun deleteLabel(label: LabelEntity) {
        viewModelScope.launch {
            container.database.user().deleteAssignmentsOf(label.id)
            container.database.user().deleteLabel(label)
        }
    }

    fun addTemplate(name: String, minutes: Int) {
        viewModelScope.launch { container.database.user().upsertTemplate(EventTemplateEntity(name = name.trim(), durationMinutes = minutes)) }
    }

    fun deleteTemplate(template: EventTemplateEntity) {
        viewModelScope.launch { container.database.user().deleteTemplate(template) }
    }

    fun deleteRule(rule: LabelAssignmentEntity) {
        viewModelScope.launch { container.database.user().deleteAssignment(rule) }
    }
}
