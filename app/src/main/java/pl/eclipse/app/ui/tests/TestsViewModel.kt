package pl.eclipse.app.ui.tests

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import pl.eclipse.app.container
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.mondayOf
import pl.eclipse.core.calc.GoalResult
import pl.eclipse.core.calc.goal
import pl.eclipse.core.calc.predictedGrade
import pl.eclipse.core.calc.subjectAverage
import pl.eclipse.core.model.EventType
import java.time.LocalDate

enum class TestFilter { TESTS, QUIZZES, HOMEWORK }

enum class TestGroup { THIS_WEEK, NEXT_WEEK, LATER, PAST }

/** Podpowiedź z kalkulatora: co trzeba dostać, żeby utrzymać obecną ocenę (SPEC 12.3). */
data class TestHint(val grade: Int, val result: GoalResult, val raise: Boolean)

data class TestEntry(
    val key: String,
    val type: EventType?,
    val isHomework: Boolean,
    val subjectKey: String?,
    val subject: String,
    val subjectColor: Color,
    val typeColor: Color,
    val description: String,
    val date: LocalDate,
    val lessonNo: Int?,
    val daysUntil: Long,
    val hoursUntil: Long?,
    val average: Double?,
    val hint: TestHint?,
    val movedFrom: LocalDate?,
    val removed: Boolean,
    val grades: List<String>,
    val group: TestGroup,
)

data class TestsState(
    val loading: Boolean = true,
    val filters: Set<TestFilter> = setOf(TestFilter.TESTS, TestFilter.QUIZZES),
    val upcoming: Map<TestGroup, List<TestEntry>> = emptyMap(),
    val past: List<TestEntry> = emptyList(),
    val showPast: Boolean = false,
)

private data class Ui(val filters: Set<TestFilter> = setOf(TestFilter.TESTS, TestFilter.QUIZZES), val showPast: Boolean = false)

class TestsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val ui = MutableStateFlow(Ui())

    val state: StateFlow<TestsState> = combine(container.snapshot, container.user, container.settings.settings, ui) { snapshot, user, settings, ui ->
        val i = Insights(snapshot, user, settings)
        val today = i.today
        val monday = mondayOf(today)
        val grades = snapshot.grades.filter { it.removedAt == null }.map { it.value }
        val semester = if (snapshot.student != null && today > snapshot.student.firstSemesterEnd) 2 else 1

        fun average(subjectKey: String?) = subjectKey?.let { key ->
            subjectAverage(grades.filter { it.subjectKey == key && it.semester == semester }, i.methods[key] ?: pl.eclipse.core.calc.AverageMethod.AUTO, settings.grading)
        }

        fun hint(subjectKey: String?, avg: Double?): TestHint? {
            if (subjectKey == null || avg == null) return null
            // przy jedynce podpowiadamy wyjście na 2, w pozostałych przypadkach utrzymanie obecnej oceny
            val current = predictedGrade(avg, settings.grading)
            val raise = current == 1
            val grade = if (raise) 2 else current
            val target = settings.grading.thresholds[grade] ?: return null
            val list = grades.filter { it.subjectKey == subjectKey && it.semester == semester }
            return TestHint(grade, goal(list, target, i.methods[subjectKey] ?: pl.eclipse.core.calc.AverageMethod.AUTO, settings.grading), raise)
        }

        fun group(date: LocalDate) = when {
            date < today -> TestGroup.PAST
            date < monday.plusWeeks(1) -> TestGroup.THIS_WEEK
            date < monday.plusWeeks(2) -> TestGroup.NEXT_WEEK
            else -> TestGroup.LATER
        }

        val events = snapshot.events.filter { it.value.type == EventType.TEST || it.value.type == EventType.QUIZ }
            .filter { (it.value.type == EventType.TEST && TestFilter.TESTS in ui.filters) || (it.value.type == EventType.QUIZ && TestFilter.QUIZZES in ui.filters) }
            .map { stored ->
                val e = stored.value
                val avg = average(e.subjectKey)
                TestEntry(
                    key = e.sourceKey,
                    type = e.type,
                    isHomework = false,
                    subjectKey = e.subjectKey,
                    subject = i.name(e.subjectKey).ifBlank { e.category },
                    subjectColor = i.color(e.subjectKey),
                    typeColor = i.typeColor(e.type),
                    description = e.description.ifBlank { e.category },
                    date = e.date,
                    lessonNo = e.lessonNo,
                    daysUntil = e.date.toEpochDay() - today.toEpochDay(),
                    hoursUntil = i.hoursUntil(e).takeIf { it in 0..23 },
                    average = avg,
                    hint = hint(e.subjectKey, avg),
                    movedFrom = stored.previous?.date?.takeIf { it != e.date },
                    removed = stored.removedAt != null,
                    grades = if (e.date <= today) grades.filter { it.subjectKey == e.subjectKey && it.date >= e.date && it.date <= e.date.plusDays(14) }.map { it.symbol } else emptyList(),
                    group = group(e.date),
                )
            }
        val homework = if (TestFilter.HOMEWORK !in ui.filters) emptyList() else snapshot.homework.filter { it.removedAt == null }.map { stored ->
            val h = stored.value
            TestEntry(
                key = h.sourceKey, type = null, isHomework = true, subjectKey = h.subjectKey, subject = i.name(h.subjectKey),
                subjectColor = i.color(h.subjectKey), typeColor = i.typeColor("HOMEWORK"), description = h.title, date = h.dueDate, lessonNo = null,
                daysUntil = h.dueDate.toEpochDay() - today.toEpochDay(), hoursUntil = null, average = null, hint = null,
                movedFrom = null, removed = false, grades = emptyList(), group = group(h.dueDate),
            )
        }
        val all = (events + homework).sortedWith(compareBy({ it.date }, { it.lessonNo ?: 0 }))
        TestsState(
            loading = false,
            filters = ui.filters,
            upcoming = all.filter { it.group != TestGroup.PAST }.groupBy { it.group }.toSortedMap(),
            past = all.filter { it.group == TestGroup.PAST }.sortedByDescending { it.date },
            showPast = ui.showPast,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestsState())

    fun toggle(filter: TestFilter) = ui.update { it.copy(filters = if (filter in it.filters) it.filters - filter else it.filters + filter) }

    fun togglePast() = ui.update { it.copy(showPast = !it.showPast) }
}
