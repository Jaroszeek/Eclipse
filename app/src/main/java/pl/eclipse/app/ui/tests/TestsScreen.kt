package pl.eclipse.app.ui.tests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.R
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.eventColor
import pl.eclipse.core.calc.GoalResult
import pl.eclipse.core.model.EventType

@Composable
fun TestsScreen(
    contentPadding: PaddingValues,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onOpenCalculator: (String) -> Unit,
    viewModel: TestsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TestFilter.TESTS to R.string.filter_tests, TestFilter.QUIZZES to R.string.filter_quizzes, TestFilter.HOMEWORK to R.string.filter_homework)
                        .forEach { (f, label) -> FilterChip(selected = f in state.filters, onClick = { viewModel.toggle(f) }, label = { Text(stringResource(label)) }) }
                }
            }
            if (!state.loading && state.upcoming.isEmpty()) item { EmptyState(stringResource(R.string.tests_empty)) }
            state.upcoming.forEach { (group, entries) ->
                item(key = group.name) { SectionTitle(stringResource(group.title)) }
                items(entries, key = { it.key }) { TestCard(it, onOpenCalculator) }
            }
            if (state.past.isNotEmpty()) {
                item {
                    TextButton(onClick = viewModel::togglePast) {
                        Text(stringResource(if (state.showPast) R.string.tests_past_hide else R.string.tests_past_show, state.past.size))
                    }
                }
                if (state.showPast) items(state.past, key = { "past-" + it.key }) { TestCard(it, onOpenCalculator) }
            }
        }
    }
}

private val TestGroup.title
    get() = when (this) {
        TestGroup.THIS_WEEK -> R.string.tests_this_week
        TestGroup.NEXT_WEEK -> R.string.tests_next_week
        TestGroup.LATER -> R.string.tests_later
        TestGroup.PAST -> R.string.tests_past
    }

@Composable
private fun TestCard(entry: TestEntry, onOpenCalculator: (String) -> Unit) {
    val c = Eclipse.colors
    val typeColor = entry.type?.let(::eventColor) ?: Palette.Homework
    val icon = when {
        entry.isHomework -> R.drawable.ic_menu_book
        entry.type == EventType.QUIZ -> R.drawable.ic_bolt
        else -> R.drawable.ic_fact_check
    }
    val word = when {
        entry.isHomework -> R.string.kind_homework
        entry.type == EventType.QUIZ -> R.string.word_quiz_cap
        else -> R.string.word_test_cap
    }
    EclipseCard(Modifier.alpha(if (entry.removed) 0.55f else 1f), border = typeColor.copy(alpha = 0.35f)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(18.dp), tint = c.readable(typeColor))
                    Tag(stringResource(word), typeColor)
                    entry.movedFrom?.let { Tag(stringResource(R.string.moved_from, formatDate(it)), Palette.Warning) }
                    if (entry.removed) Tag(stringResource(R.string.cal_removed), Palette.Critical)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(entry.subjectColor))
                    Text(
                        entry.subject,
                        style = MaterialTheme.typography.titleLarge.copy(textDecoration = if (entry.removed) TextDecoration.LineThrough else null),
                        color = c.text, modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(entry.description, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 3)
                Text(
                    listOfNotNull(formatDate(entry.date), entry.lessonNo?.let { stringResource(R.string.lesson_no, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            Countdown(entry)
        }
        if (entry.grades.isNotEmpty()) {
            Text(stringResource(R.string.related_grade, entry.grades.joinToString(", ")), style = MaterialTheme.typography.titleSmall, color = c.accentText, modifier = Modifier.padding(top = 6.dp))
        }
        if (!entry.isHomework && entry.daysUntil >= 0) {
            Text(
                entry.average?.let { stringResource(R.string.tests_average, formatPercent(it)) } ?: stringResource(R.string.no_grades),
                style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = c.textSecondary, modifier = Modifier.padding(top = 8.dp),
            )
            entry.hint?.let { Text(hintText(it), style = MaterialTheme.typography.bodyMedium, color = c.text) }
            entry.subjectKey?.let { key ->
                SecondaryButton(stringResource(R.string.open_calculator), { onOpenCalculator(key) }, Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Countdown(entry: TestEntry) {
    val c = Eclipse.colors
    val (value, caption) = when {
        entry.daysUntil < 0 -> formatDate(entry.date) to ""
        entry.hoursUntil != null && entry.daysUntil <= 1 -> entry.hoursUntil.toString() to pluralStringResource(R.plurals.hours, entry.hoursUntil.toInt())
        entry.daysUntil == 0L -> stringResource(R.string.today_lower) to (entry.lessonNo?.let { stringResource(R.string.lesson_no, it) } ?: "")
        entry.daysUntil == 1L -> stringResource(R.string.tomorrow_lower) to ""
        else -> entry.daysUntil.toString() to pluralStringResource(R.plurals.days, entry.daysUntil.toInt())
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
        Text(value, style = MaterialTheme.typography.displaySmall.merge(TabularNumbers), color = c.text)
        if (caption.isNotBlank()) Text(caption, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
fun hintText(hint: TestHint): String {
    val goalWords = stringResource(if (hint.raise) R.string.hint_goal_raise else R.string.hint_goal_keep, hint.grade)
    return when (val r = hint.result) {
        is GoalResult.AlreadySafe -> stringResource(R.string.hint_safe, hint.grade)
        is GoalResult.Required -> r.lowestGrade?.let { stringResource(R.string.hint_required_grade, goalWords, formatPercent(r.percent), it) }
            ?: stringResource(R.string.hint_required, goalWords, formatPercent(r.percent))
        is GoalResult.NeedsMore -> stringResource(R.string.hint_more, r.gradesAt100)
        GoalResult.Impossible -> stringResource(R.string.hint_impossible, hint.grade)
    }
}
