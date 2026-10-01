@file:OptIn(ExperimentalLayoutApi::class)

package pl.eclipse.app.ui.grades

import pl.eclipse.app.ui.components.ChoiceChips
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.R
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.formatPoints
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.AverageChart
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.GradeTile
import pl.eclipse.app.ui.components.PercentBar
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.gradeColor
import pl.eclipse.app.ui.theme.levelColor
import pl.eclipse.core.calc.AverageMethod
import pl.eclipse.core.calc.GoalResult
import pl.eclipse.core.calc.PercentSource
import pl.eclipse.core.calc.Period
import pl.eclipse.core.calc.PointScale
import pl.eclipse.core.calc.ThresholdDistance
import pl.eclipse.core.model.GradeKind
import java.util.Locale

private val POLISH: Locale = Locale.forLanguageTag("pl-PL")

/** Punkty procentowe z jednym miejscem po przecinku: „3,2”. */
fun formatPp(value: Double): String = String.format(POLISH, "%.1f", value)

@Composable
fun distanceText(d: ThresholdDistance): String = listOfNotNull(
    d.nextGrade?.let { next -> d.toNext?.let { stringResource(R.string.distance_to_next, next, formatPp(it)) } },
    stringResource(R.string.distance_margin, formatPp(d.margin)),
).joinToString(" · ")

@Composable
private fun PeriodSwitch(period: Period, onPeriod: (Period) -> Unit) {
    val options = listOf(Period.FIRST to R.string.period_first, Period.SECOND to R.string.period_second, Period.YEAR to R.string.period_year)
    ChoiceChips(options.map { (value, label) -> value to stringResource(label) }, period, onPeriod)
}

@Composable
fun GradesScreen(
    contentPadding: PaddingValues,
    syncing: Boolean,
    onRefresh: () -> Unit,
    onOpenSubject: (String) -> Unit,
    viewModel: GradesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Eclipse.colors
    PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            item { PeriodSwitch(state.period, viewModel::setPeriod) }
            item {
                ChoiceChips(
                    listOf(GradeSort.NAME to R.string.sort_name, GradeSort.LOWEST to R.string.sort_lowest, GradeSort.RECENT to R.string.sort_recent)
                        .map { (s, label) -> s to stringResource(label) },
                    state.sort, viewModel::setSort,
                )
            }
            item {
                EclipseCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.overall_average), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f))
                        Text(
                            state.overall?.let(::formatPercent) ?: stringResource(R.string.no_grades),
                            style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                            color = c.text,
                        )
                    }
                }
            }
            if (!state.loading && state.cards.isEmpty()) item { EmptyState(stringResource(R.string.grades_empty)) }
            items(state.cards, key = { it.key }) { card -> SubjectCard(card, state.thresholds) { onOpenSubject(card.key) } }
        }
    }
}

@Composable
private fun SubjectCard(card: SubjectCardState, thresholds: List<Double>, onClick: () -> Unit) {
    val c = Eclipse.colors
    EclipseCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(card.color))
            Text(card.name, style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
            card.warning?.let { level ->
                Icon(painterResource(R.drawable.ic_warning), stringResource(R.string.in_important), Modifier.size(20.dp).padding(end = 6.dp), tint = c.readable(levelColor(level)))
            }
            Text(
                card.average?.let(::formatPercent) ?: stringResource(R.string.no_grades),
                style = (if (card.average != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyMedium).merge(TabularNumbers),
                color = card.predicted?.let { c.readable(gradeColor(it)) } ?: c.textSecondary,
            )
        }
        card.predicted?.let { Text(stringResource(R.string.prediction, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        if (card.average != null) {
            PercentBar(card.average, thresholds, Modifier.padding(vertical = 8.dp), grade = card.predicted)
            card.distance?.let { Text(distanceText(it), style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = c.textSecondary) }
        }
        if (card.tiles.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                card.tiles.forEach { GradeTile(it.text, it.grade, isNew = it.isNew) }
            }
        }
    }
}

/** Ekran przedmiotu (SPEC 12.4): oceny, wykres, oceny proponowane i końcowe, kalkulator „co jeśli”, ustawienia. */
@Composable
fun SubjectScreen(key: String, contentPadding: PaddingValues, onTitle: (String) -> Unit, viewModel: GradesViewModel = viewModel()) {
    LaunchedEffect(key) { viewModel.openSubject(key) }
    val state by viewModel.subject.collectAsStateWithLifecycle()
    LaunchedEffect(state.name) { if (state.name.isNotBlank()) onTitle(state.name) }
    val c = Eclipse.colors
    if (state.key != key) return
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        item { PeriodSwitch(state.period, viewModel::setPeriod) }
        item {
            EclipseCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        state.predicted?.let { Text(stringResource(R.string.prediction, it), style = MaterialTheme.typography.titleLarge, color = c.text) }
                        if (state.allPoints) Text(stringResource(R.string.points_subject), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Text(
                        state.average?.let(::formatPercent) ?: stringResource(R.string.no_grades),
                        style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
                        color = state.predicted?.let { c.readable(gradeColor(it)) } ?: c.textSecondary,
                    )
                }
                if (state.average != null) {
                    PercentBar(state.average, state.thresholds.values, Modifier.padding(vertical = 8.dp), grade = state.predicted)
                    state.distance?.let { Text(distanceText(it), style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = c.textSecondary) }
                }
            }
        }
        if (state.series.size >= 2) {
            item {
                EclipseCard {
                    Text(stringResource(R.string.average_over_time), style = MaterialTheme.typography.titleMedium, color = c.text)
                    AverageChart(listOf(state.color to state.series), state.thresholds, Modifier.padding(top = 8.dp))
                }
            }
        }
        if (state.scales.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.points_scale_title)) }
            item { PointScales(state.scales) }
        }
        item { SectionTitle(stringResource(R.string.grades_list)) }
        if (state.rows.isEmpty()) item { EmptyState(stringResource(R.string.no_grades_period)) }
        items(state.rows, key = { it.key }) { row -> GradeRowView(row, onToggleSkip = { viewModel.toggleSkipped(row.key, !row.skipped) }) }
        if (state.special.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.special_grades)) }
            items(state.special, key = { "s-" + it.key }) { row -> GradeRowView(row, onToggleSkip = null) }
        }
        item { Calculator(state, viewModel) }
        item { SubjectSettings(state, viewModel) }
    }
}

@Composable
private fun GradeRowView(row: GradeRow, onToggleSkip: (() -> Unit)?) {
    val c = Eclipse.colors
    EclipseCard(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeTile(row.symbol, row.grade)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    listOfNotNull(row.category, row.kind.takeIf { it != GradeKind.REGULAR && it != GradeKind.POINT }?.let { stringResource(it.word) }).joinToString(" · ").ifBlank { stringResource(R.string.kind_grade) },
                    style = MaterialTheme.typography.titleSmall, color = c.text,
                )
                Text(
                    listOfNotNull(formatDate(row.date), row.teacher).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.percent?.let(::formatPercent) ?: "—", style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), color = c.text)
                Text(stringResource(row.source.word), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
        row.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.text, modifier = Modifier.padding(top = 6.dp)) }
        if (!row.counts) Tag(stringResource(R.string.not_counted), c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        if (onToggleSkip != null && row.counts && (row.percent ?: 100.0) < 50.0) {
            TextButton(onClick = onToggleSkip) { Text(stringResource(if (row.skipped) R.string.unskip_grade else R.string.skip_grade)) }
        }
    }
}

private val PercentSource.word
    get() = when (this) {
        PercentSource.POINTS -> R.string.source_points
        PercentSource.TABLE -> R.string.source_table
        PercentSource.NONE -> R.string.source_none
    }

private val GradeKind.word
    get() = when (this) {
        GradeKind.PROPOSED -> R.string.kind_proposed
        GradeKind.SEMESTER -> R.string.kind_semester
        GradeKind.FINAL -> R.string.kind_final
        GradeKind.DESCRIPTIVE -> R.string.kind_descriptive
        else -> R.string.kind_grade
    }

/** „17/20” → (17, 20); „85” albo „85%” → (85, 100). */
fun parseScore(text: String): Pair<Double, Double>? {
    val t = text.trim().replace(',', '.').removeSuffix("%").trim()
    if ("/" in t) {
        val (a, b) = t.split("/", limit = 2).map { it.trim().toDoubleOrNull() }
        return if (a != null && b != null && b > 0 && a in 0.0..b) a to b else null
    }
    return t.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }?.let { it to 100.0 }
}

/** Za co ile punktów w tym przedmiocie — wyliczone z ocen, bo Librus nigdzie tego nie podaje (SPEC 6.7). */
@Composable
private fun PointScales(scales: List<PointScale>) {
    val c = Eclipse.colors
    EclipseCard {
        scales.forEach { scale ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        scale.category.ifBlank { stringResource(R.string.points_scale_other) },
                        style = MaterialTheme.typography.titleSmall,
                        color = c.text,
                    )
                    Text(
                        pluralStringResource(R.plurals.points_scale_count, scale.count, scale.count) +
                            if (scale.varied) " · " + stringResource(R.string.points_scale_varied) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
                Text(
                    stringResource(R.string.points_scale_points, formatPoints(scale.maxPoints)),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    color = c.text,
                )
            }
        }
        Text(
            stringResource(R.string.points_scale_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Calculator(state: SubjectState, viewModel: GradesViewModel) {
    val c = Eclipse.colors
    val calc = state.calculator
    EclipseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_calculate), null, tint = c.accentText)
            Text(stringResource(R.string.calculator), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(start = 8.dp))
        }
        Text(stringResource(R.string.calculator_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        ChoiceChips(
            listOf(false to stringResource(R.string.calc_add), true to stringResource(R.string.calc_goal)),
            calc.goalMode, viewModel::setGoalMode, Modifier.padding(vertical = 8.dp),
        )
        if (!calc.goalMode) {
            Text(stringResource(R.string.calc_add_grade), style = MaterialTheme.typography.titleSmall, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.table.entries.sortedByDescending { it.key }.forEach { (grade, percent) ->
                    FilterChip(selected = false, onClick = { viewModel.addHypothetical(percent, 100.0) }, label = { Text(grade.toString()) })
                }
            }
            var score by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    score, { score = it },
                    label = { Text(stringResource(R.string.calc_score)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { parseScore(score)?.let { (p, m) -> viewModel.addHypothetical(p, m); score = "" } }, enabled = parseScore(score) != null) {
                    Text(stringResource(R.string.calc_add_button))
                }
            }
            if (calc.extra.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    calc.extra.forEachIndexed { index, (p, m) ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeHypothetical(index) },
                            label = { Text(if (m == 100.0) formatPercent(p) else "${p.toBigDecimal().stripTrailingZeros().toPlainString()}/${m.toBigDecimal().stripTrailingZeros().toPlainString()}") },
                            trailingIcon = { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.remove), Modifier.size(16.dp)) },
                        )
                    }
                }
                calc.extraAverage?.let { avg ->
                    Text(
                        stringResource(R.string.calc_new_average, formatPercent(avg), calc.extraPredicted ?: 1),
                        style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), color = c.text, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        } else {
            Text(stringResource(R.string.calc_target), style = MaterialTheme.typography.titleSmall, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.thresholds.entries.sortedBy { it.key }.forEach { (grade, value) ->
                    FilterChip(selected = calc.targetPercent == value, onClick = { viewModel.setTarget(value) }, label = { Text("$grade (${formatPercent(value)})") })
                }
            }
            var custom by remember { mutableStateOf("") }
            OutlinedTextField(
                custom,
                { v -> custom = v; v.replace(',', '.').removeSuffix("%").trim().toDoubleOrNull()?.let(viewModel::setTarget) },
                label = { Text(stringResource(R.string.calc_target_custom)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.allPoints && state.prefs.averageMethod == AverageMethod.POINTS_SUM.name) {
                var max by remember { mutableStateOf(calc.testMaxPoints?.toString().orEmpty()) }
                OutlinedTextField(
                    max,
                    { v -> max = v; viewModel.setTestMax(v.replace(',', '.').toDoubleOrNull()) },
                    label = { Text(stringResource(R.string.calc_test_max)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
            calc.result?.let { Text(goalText(it, calc.targetPercent), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
fun goalText(result: GoalResult, target: Double): String = when (result) {
    GoalResult.AlreadySafe -> stringResource(R.string.goal_safe, formatPercent(target))
    is GoalResult.Required -> {
        val points = result.points
        val grade = result.lowestGrade
        when {
            points != null -> stringResource(R.string.goal_points, formatPp(points), formatPercent(result.percent))
            grade != null -> stringResource(R.string.goal_required_grade, formatPercent(result.percent), grade)
            else -> stringResource(R.string.goal_required, formatPercent(result.percent))
        }
    }
    is GoalResult.NeedsMore -> stringResource(R.string.hint_more, result.gradesAt100)
    GoalResult.Impossible -> stringResource(R.string.goal_impossible, formatPercent(target))
}

@Composable
private fun SubjectSettings(state: SubjectState, viewModel: GradesViewModel) {
    val c = Eclipse.colors
    val prefs = state.prefs.copy(subjectKey = state.key)
    EclipseCard {
        Text(stringResource(R.string.subject_settings), style = MaterialTheme.typography.titleLarge, color = c.text)
        Text(stringResource(R.string.event_color), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Palette.Subjects.forEach { col ->
                val selected = state.color.toArgb() == col.toArgb()
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(col)
                        .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
                        .selectable(selected, role = Role.RadioButton) { viewModel.savePrefs(prefs.copy(color = col.toArgb())) },
                )
            }
        }
        var short by remember(state.key) { mutableStateOf(state.short) }
        OutlinedTextField(
            short,
            { v -> short = v.take(4); viewModel.savePrefs(prefs.copy(short = short)) },
            label = { Text(stringResource(R.string.subject_short)) },
            singleLine = true,
            modifier = Modifier.width(200.dp).padding(top = 8.dp),
        )
        Text(stringResource(R.string.average_method), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(top = 8.dp))
        val methods = listOf(AverageMethod.AUTO to R.string.method_auto, AverageMethod.MEAN_PERCENT to R.string.method_mean, AverageMethod.POINTS_SUM to R.string.method_points)
        ChoiceChips(
            methods.map { (m, label) -> m.name to stringResource(label) },
            prefs.averageMethod, { name -> viewModel.savePrefs(prefs.copy(averageMethod = name)) },
        )
        Text(stringResource(R.string.method_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = c.border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mark_difficult), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
            Switch(checked = prefs.isDifficult, onCheckedChange = { viewModel.savePrefs(prefs.copy(isDifficult = it)) })
        }
    }
}
