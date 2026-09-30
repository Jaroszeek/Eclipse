@file:OptIn(ExperimentalLayoutApi::class)

package pl.eclipse.app.ui.stats

import pl.eclipse.app.ui.components.ChoiceChips
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.R
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.ui.components.AverageChart
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.WeekColumns
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.core.calc.Period

@Composable
fun StatsScreen(contentPadding: PaddingValues, viewModel: StatsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Eclipse.colors
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            val options = listOf(Period.FIRST to R.string.period_first, Period.SECOND to R.string.period_second, Period.YEAR to R.string.period_year)
            ChoiceChips(options.map { (value, label) -> value to stringResource(label) }, state.period, viewModel::setPeriod)
        }

        item {
            EclipseCard {
                Text(stringResource(R.string.stats_attendance), style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(
                    state.attendance?.let(::formatPercent) ?: stringResource(R.string.stats_no_attendance),
                    style = MaterialTheme.typography.displayMedium.merge(TabularNumbers), color = c.text,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Counter(stringResource(R.string.stats_present), state.present, Palette.DayOff)
                    Counter(stringResource(R.string.stats_late), state.late, Palette.Watch)
                    Counter(stringResource(R.string.stats_excused), state.excused, Palette.Homework)
                    Counter(stringResource(R.string.stats_unexcused), state.unexcused, Palette.Critical)
                }
            }
        }

        item { SectionTitle(stringResource(R.string.stats_by_subject)) }
        item {
            EclipseCard {
                if (state.bySubject.isEmpty()) EmptyState(stringResource(R.string.stats_no_attendance))
                state.bySubject.forEach { s -> AttendanceBar(s, state.criticalAttendance, state.nearAttendance) }
                Text(stringResource(R.string.stats_rule_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item { SectionTitle(stringResource(R.string.stats_unexcused_title)) }
        item {
            EclipseCard {
                var showAll by remember { mutableStateOf(false) }
                if (state.unexcusedList.isEmpty()) EmptyState(stringResource(R.string.stats_unexcused_none))
                (if (showAll) state.unexcusedList else state.unexcusedList.take(5)).forEach { AbsenceLine(it) }
                if (state.unexcusedList.size > 5) TextButton(onClick = { showAll = !showAll }) {
                    Text(stringResource(if (showAll) R.string.show_less else R.string.show_all, state.unexcusedList.size))
                }
                if (state.lateList.isNotEmpty()) {
                    Text(stringResource(R.string.stats_late_title, state.lateList.size), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(top = 8.dp))
                    state.lateList.take(5).forEach { AbsenceLine(it) }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.average_over_time)) }
        item {
            EclipseCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.subjects.forEach { (key, name, color) ->
                        FilterChip(
                            selected = key in state.selected, onClick = { viewModel.toggleSubject(key) },
                            label = { Text(name) },
                            leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(color)) },
                        )
                    }
                }
                if (state.averageLines.all { it.second.size < 2 }) EmptyState(stringResource(R.string.stats_chart_empty))
                else AverageChart(state.averageLines, state.thresholds, Modifier.padding(top = 8.dp), bands = true)
                Text(stringResource(R.string.stats_chart_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        item { SectionTitle(stringResource(R.string.stats_load)) }
        item {
            EclipseCard {
                WeekColumns(state.loadValues, state.loadLabels, { it in state.loadHeavy }, c.textSecondary.copy(alpha = 0.6f), Palette.Test)
                Text(stringResource(R.string.stats_load_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        item { SectionTitle(stringResource(R.string.stats_reserve)) }
        item {
            EclipseCard {
                if (state.reserves.isEmpty()) EmptyState(stringResource(R.string.stats_no_attendance))
                state.reserves.forEach { r ->
                    val color = when {
                        r.reserve <= 0 -> Palette.Critical
                        r.reserve <= state.reserveNear -> Palette.Warning
                        else -> c.text
                    }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(r.color))
                        Text(r.name, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                        Text(
                            if (r.reserve <= 0) stringResource(R.string.reserve_none) else stringResource(R.string.reserve_left, r.reserve, pluralStringResource(R.plurals.lessons, r.reserve)),
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = c.readable(color),
                        )
                    }
                }
                Text(stringResource(R.string.reserve_estimate), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color) {
    val c = Eclipse.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(start = 6.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall.merge(TabularNumbers), color = c.text)
    }
}

/** Poziomy pasek frekwencji z linią 50% i strefą ostrzegawczą do 60% (SPEC 12.5). */
@Composable
private fun AttendanceBar(s: SubjectAttendance, critical: Double, near: Double) {
    val c = Eclipse.colors
    val color = when {
        s.percent < critical -> Palette.Critical
        s.percent < near -> Palette.Warning
        else -> Palette.DayOff
    }
    Column(Modifier.padding(vertical = 5.dp)) {
        Row {
            Text(s.name, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            Text(formatPercent(s.percent), style = MaterialTheme.typography.titleSmall.merge(TabularNumbers), color = c.readable(color))
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp).padding(top = 3.dp).clip(RoundedCornerShape(3.dp)).background(c.text.copy(alpha = 0.08f))
                .drawBehind {
                    // strefa ostrzegawcza (50–60%) i linia 50%
                    drawRect(Palette.Warning.copy(alpha = 0.18f), topLeft = Offset(size.width * (critical / 100).toFloat(), 0f), size = Size(size.width * ((near - critical) / 100).toFloat(), size.height))
                    drawRect(color, size = Size(size.width * (s.percent / 100).toFloat(), size.height))
                    val x = size.width * (critical / 100).toFloat()
                    drawLine(c.text.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                },
        )
    }
}

@Composable
private fun AbsenceLine(row: AbsenceRow) {
    val c = Eclipse.colors
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(formatDate(row.date), style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = c.textSecondary, modifier = Modifier.width(96.dp))
        Text(stringResource(R.string.lesson_no, row.lessonNo), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.width(80.dp))
        Text(row.subject, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
    }
}
