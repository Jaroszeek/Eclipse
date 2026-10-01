package pl.eclipse.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.container
import pl.eclipse.app.data.CustomEventEntity
import pl.eclipse.app.data.EventTemplateEntity
import pl.eclipse.app.data.LabelAssignmentEntity
import pl.eclipse.app.data.LabelEntity
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.mondayOf
import pl.eclipse.app.ui.today
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Subject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class CalendarView { MONTH, WEEK, DAY, LIST }

data class CalendarState(
    val loading: Boolean = true,
    val view: CalendarView = CalendarView.WEEK,
    val today: LocalDate = today(),
    val day: LocalDate = today(),
    val firstMonday: LocalDate = mondayOf(today()).minusWeeks(WEEKS_BACK),
    val itemsByDate: Map<LocalDate, List<CalendarItem>> = emptyMap(),
    val bells: Map<Int, Pair<LocalTime, LocalTime>> = emptyMap(),
    /** Zakres siatki godzinowej w minutach od północy (z dzwonków, SPEC 13). */
    val rangeStart: Int = 7 * 60 + 30,
    val rangeEnd: Int = 16 * 60,
    val labels: List<LabelEntity> = emptyList(),
    val templates: List<EventTemplateEntity> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val upcomingTests: List<CalendarItem> = emptyList(),
) {
    fun items(date: LocalDate) = itemsByDate[date].orEmpty()
}

const val WEEKS_BACK = 20L
const val WEEKS_TOTAL = 60

private data class UiState(val view: CalendarView = CalendarView.WEEK, val day: LocalDate = today())

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val ui = MutableStateFlow(UiState())
    private val lastVisit = MutableStateFlow(Long.MAX_VALUE)

    init {
        viewModelScope.launch { lastVisit.value = container.visits.visit("calendar") }
    }

    val state: StateFlow<CalendarState> = combine(container.snapshot, container.user, container.settings.settings, ui, lastVisit) { snapshot, user, settings, ui, since ->
        val i = Insights(snapshot, user, settings)
        val first = mondayOf(i.today).minusWeeks(WEEKS_BACK)
        val items = calendarItems(i, first, first.plusWeeks(WEEKS_TOTAL.toLong()), since)
        val bells = bells(snapshot.lessons.map { it.value })
        val timed = items.filter { !it.allDay }
        val start = (listOfNotNull(bells.values.minOfOrNull { it.first }, timed.minOfOrNull { it.start ?: LocalTime.NOON })
            .minOrNull() ?: LocalTime.of(7, 30))
        val end = (listOfNotNull(bells.values.maxOfOrNull { it.second }, timed.maxOfOrNull { it.end ?: LocalTime.NOON })
            .maxOrNull() ?: LocalTime.of(16, 0))
        CalendarState(
            loading = false,
            view = ui.view,
            today = i.today,
            day = ui.day,
            firstMonday = first,
            itemsByDate = items.groupBy { it.date },
            bells = bells,
            rangeStart = (start.hour * 60 + start.minute) / 30 * 30,
            rangeEnd = ((end.hour * 60 + end.minute) + 29) / 30 * 30,
            labels = user.labels,
            templates = user.templates,
            subjects = snapshot.subjects,
            upcomingTests = items.filter {
                it.date >= i.today && it.status != pl.eclipse.app.ui.components.BlockStatus.REMOVED &&
                    (it.kind == pl.eclipse.app.ui.components.BlockKind.TEST || it.kind == pl.eclipse.app.ui.components.BlockKind.QUIZ)
            }.distinctBy { (it.ref as? CalendarRef.OfLesson)?.tests?.firstOrNull()?.sourceKey ?: it.id }.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun setView(view: CalendarView) = ui.update { it.copy(view = view) }

    fun openDay(date: LocalDate) = ui.update { it.copy(view = CalendarView.DAY, day = date) }

    fun shiftDay(days: Long) = ui.update { it.copy(day = it.day.plusDays(days)) }

    /** Zapisuje wpis, a przy [repeatWeeks] > 1 także kopie w kolejnych tygodniach (np. cotygodniowe korepetycje). */
    fun saveCustomEvent(event: CustomEventEntity, repeatWeeks: Int = 1) {
        viewModelScope.launch {
            val dao = container.database.user()
            dao.upsertCustomEvent(event)
            // Powtarzamy tylko nowe wpisy — edycja jednego nie powiela całej serii.
            if (event.id != 0L || repeatWeeks <= 1) return@launch
            val start = runCatching { LocalDateTime.parse(event.start) }.getOrNull() ?: return@launch
            val end = runCatching { LocalDateTime.parse(event.end) }.getOrNull() ?: return@launch
            (1 until repeatWeeks).forEach { week ->
                dao.upsertCustomEvent(
                    event.copy(
                        id = 0,
                        start = start.plusWeeks(week.toLong()).toString(),
                        end = end.plusWeeks(week.toLong()).toString(),
                    ),
                )
            }
        }
    }

    fun deleteCustomEvent(event: CustomEventEntity) {
        viewModelScope.launch {
            container.database.user().deleteCustomEvent(event)
            state.value.itemsByDate.values.flatten().firstOrNull { it.id == "custom|${event.id}" }?.labels
                ?.forEach { container.database.user().deleteAssignment(it.assignment) }
        }
    }

    /** Nowe wydarzenie z szablonu w miejscu upuszczenia; zwraca je do edycji (SPEC 13). */
    fun eventFromTemplate(template: EventTemplateEntity, date: LocalDate, time: LocalTime): CustomEventEntity {
        val start = date.atTime(time)
        return CustomEventEntity(
            title = template.name,
            start = start.toString(),
            end = start.plusMinutes(template.durationMinutes.toLong()).toString(),
            color = template.color,
            templateId = template.id,
        )
    }

    fun attachLabel(labelId: Long, target: String, key: String, note: String) {
        viewModelScope.launch {
            container.database.user().insertAssignment(LabelAssignmentEntity(labelId = labelId, target = target, targetKey = key, note = note.trim()))
        }
    }

    fun removeLabel(assignment: LabelAssignmentEntity) {
        viewModelScope.launch { container.database.user().deleteAssignment(assignment) }
    }

    fun createLabel(name: String, color: Int) {
        viewModelScope.launch { container.database.user().upsertLabel(LabelEntity(name = name.trim(), color = color)) }
    }

    /** Ocena „powiązana” ze sprawdzianem: z tego przedmiotu, wystawiona w ciągu 14 dni od sprawdzianu (SPEC 13). */
    fun relatedGrades(subjectKey: String?, date: LocalDate, type: EventType): List<String> {
        if (subjectKey == null || (type != EventType.TEST && type != EventType.QUIZ)) return emptyList()
        return container.snapshot.replayCache.firstOrNull()?.grades.orEmpty()
            .filter { it.removedAt == null && it.value.subjectKey == subjectKey && it.value.date >= date && it.value.date <= date.plusDays(14) }
            .map { it.value.symbol }
    }
}
