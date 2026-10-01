package pl.eclipse.app.ui.home

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.eclipse.app.container
import pl.eclipse.app.data.CustomKind
import pl.eclipse.app.data.HomeTile
import pl.eclipse.app.data.homeTiles
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.WARSAW
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.calc.PercentSource
import pl.eclipse.core.calc.gradePercent
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.LessonStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class DiscState(val coverage: Float, val value: String?, val unitHours: Boolean, val subject: String?, val typeQuiz: Boolean)

data class LessonRow(
    val key: String,
    val no: Int,
    val time: String,
    val subject: String,
    val color: Color,
    val room: String?,
    val status: LessonStatus,
    val current: Boolean,
    val hasTest: EventType?,
)

data class NewItem(val title: String, val detail: String, val kind: NewKind)

enum class NewKind { GRADE, TEST, MOVED_TEST, MESSAGE, ANNOUNCEMENT }

data class WarningRow(val subject: String, val level: WarningLevel, val average: Double?)

data class HomeState(
    val loading: Boolean = true,
    val disc: DiscState? = null,
    val dayLabelTomorrow: Boolean = false,
    val lessons: List<LessonRow> = emptyList(),
    val newItems: List<NewItem> = emptyList(),
    val warningCounts: Map<WarningLevel, Int> = emptyMap(),
    val topWarnings: List<WarningRow> = emptyList(),
    val luckyNumber: Int? = null,
    val luckyIsMine: Boolean = false,
    val homework: List<Pair<String, String>> = emptyList(),
    val lastSyncAt: Long? = null,
    val order: List<HomeTile> = HomeTile.entries,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val lastVisit = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { lastVisit.value = container.visits.visit("home") }
    }

    val state: StateFlow<HomeState> = combine(container.snapshot, container.user, container.settings.settings, lastVisit) { snapshot, user, settings, visit ->
        val i = Insights(snapshot, user, settings)
        val today = i.today
        val now = LocalTime.now(WARSAW)

        val next = i.nextTest
        val disc = next?.let { e ->
            val days = (e.date.toEpochDay() - today.toEpochDay()).toInt()
            val hours = i.hoursUntil(e)
            val inHours = hours in 0..23 && days <= 1
            DiscState(
                coverage = 1f - (days.coerceIn(0, 14) / 14f),
                value = if (inHours) hours.toString() else days.toString(),
                unitHours = inHours,
                subject = i.name(e.subjectKey).ifBlank { e.category },
                typeQuiz = e.type == EventType.QUIZ,
            )
        } ?: DiscState(0f, null, false, null, false)

        // Dziś, a po ostatniej lekcji — jutro (SPEC 12.1)
        val lessons = snapshot.lessons.filter { it.removedAt == null }.map { it.value }
        val todays = lessons.filter { it.date == today }.sortedBy { it.lessonNo }
        val afterLessons = todays.isEmpty() || now.isAfter(todays.last().end)
        val shownDay: LocalDate = if (afterLessons) nextSchoolDay(today, lessons.map { it.date }.toSet()) else today
        val tests = i.activeEvents.filter { it.type == EventType.TEST || it.type == EventType.QUIZ }
        val rows = lessons.filter { it.date == shownDay }.sortedBy { it.lessonNo }.map { l ->
            LessonRow(
                key = l.sourceKey,
                no = l.lessonNo,
                time = "%d:%02d".format(l.start.hour, l.start.minute),
                subject = i.name(l.subjectKey),
                color = i.color(l.subjectKey),
                room = l.room,
                status = l.status,
                current = shownDay == today && !now.isBefore(l.start) && now.isBefore(l.end),
                hasTest = tests.firstOrNull { it.date == l.date && it.subjectKey == l.subjectKey && (it.lessonNo == null || it.lessonNo == l.lessonNo) }?.type,
            )
        }

        // Nowe od ostatniej wizyty
        val since = visit ?: Long.MAX_VALUE
        val newItems = buildList {
            snapshot.grades.filter { it.removedAt == null && it.firstSeenAt.toEpochMilli() > since }.forEach { g ->
                // procent tylko przy ocenach punktowych
                val p = gradePercent(g.value, settings.grading).takeIf { it.source == PercentSource.POINTS }?.percent
                add(NewItem(i.name(g.value.subjectKey), g.value.symbol + (p?.let { " (${formatPercent(it)})" } ?: ""), NewKind.GRADE))
            }
            snapshot.events.filter { it.removedAt == null && (it.value.type == EventType.TEST || it.value.type == EventType.QUIZ) }.forEach { e ->
                val movedAt = e.changedAt?.toEpochMilli()
                when {
                    e.firstSeenAt.toEpochMilli() > since -> add(NewItem(i.name(e.value.subjectKey).ifBlank { e.value.category }, formatDate(e.value.date), NewKind.TEST))
                    movedAt != null && movedAt > since && e.previous?.date != e.value.date ->
                        add(NewItem(i.name(e.value.subjectKey), "${e.previous?.date?.let(::formatDate)} → ${formatDate(e.value.date)}", NewKind.MOVED_TEST))
                }
            }
            snapshot.messages.filter { it.firstSeenAt.toEpochMilli() > since && it.value.sentAt.toEpochMilli() > since }.forEach { add(NewItem(it.value.sender, it.value.title, NewKind.MESSAGE)) }
            snapshot.announcements.filter { it.firstSeenAt.toEpochMilli() > since }.forEach { add(NewItem(it.value.title, it.value.author.orEmpty(), NewKind.ANNOUNCEMENT)) }
        }

        val warnings = i.warnings
        val lucky = snapshot.luckyNumbers.firstOrNull { it.date == today }?.number
        // Zadania na 3 dni: z Librusa i własne z kalendarza
        val window = today..today.plusDays(3)
        val fromLibrus = snapshot.homework.filter { it.removedAt == null && it.value.dueDate in window }
            .map { it.value.dueDate to listOf(i.name(it.value.subjectKey), it.value.title).filter(String::isNotBlank).joinToString(": ") }
        val own = user.customEvents.filter { it.kind == CustomKind.HOMEWORK }.mapNotNull { e ->
            val due = runCatching { LocalDateTime.parse(e.start).toLocalDate() }.getOrNull()?.takeIf { it in window } ?: return@mapNotNull null
            due to listOf(i.name(e.subjectKey), e.title).filter(String::isNotBlank).joinToString(": ")
        }
        HomeState(
            loading = false,
            disc = disc,
            dayLabelTomorrow = shownDay != today,
            lessons = rows,
            newItems = newItems,
            warningCounts = warnings.groupingBy { it.level }.eachCount(),
            topWarnings = warnings.take(3).map { WarningRow(i.name(it.subjectKey), it.level, it.average) },
            luckyNumber = lucky,
            luckyIsMine = lucky != null && lucky == settings.myDiaryNumber,
            homework = (fromLibrus + own).sortedBy { it.first }.map { (due, text) -> text to formatDate(due) },
            lastSyncAt = snapshot.lastSuccessAt,
            order = settings.homeTiles(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Przesuwa kafelek Pulpitu o [delta] miejsc: −1 wyżej, +1 niżej. */
    fun move(tile: HomeTile, delta: Int) {
        viewModelScope.launch {
            container.settings.update { s ->
                val order = s.homeTiles().toMutableList()
                val to = (order.indexOf(tile) + delta).coerceIn(0, order.lastIndex)
                order.remove(tile)
                order.add(to, tile)
                s.copy(homeOrder = order)
            }
        }
    }

    fun resetOrder() {
        viewModelScope.launch { container.settings.update { it.copy(homeOrder = HomeTile.entries) } }
    }

    private fun nextSchoolDay(today: LocalDate, days: Set<LocalDate>): LocalDate =
        days.filter { it > today }.minOrNull() ?: today.plusDays(1)
}
