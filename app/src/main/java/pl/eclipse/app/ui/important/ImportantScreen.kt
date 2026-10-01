package pl.eclipse.app.ui.important

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.Flags
import pl.eclipse.app.data.SubjectPrefsEntity
import pl.eclipse.app.data.UserFlagEntity
import pl.eclipse.app.formatDate
import pl.eclipse.app.formatPercent
import pl.eclipse.app.ui.Insights
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.grades.formatPp
import pl.eclipse.app.ui.home.word
import pl.eclipse.app.ui.reasonKey
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.levelColor
import pl.eclipse.core.calc.GoalResult
import pl.eclipse.core.calc.Reason
import pl.eclipse.core.calc.SubjectWarning
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.calc.WarningRule
import pl.eclipse.core.calc.predictedGrade
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.SchoolEvent

data class WarningCard(
    val warning: SubjectWarning,
    val subject: String,
    val predicted: Int?,
    val reserve: Int?,
    val nextTest: SchoolEvent?,
    val isDifficult: Boolean,
    val hintTarget: Double?,
)

/** Powód ukryty przez użytkownika — pokazujemy go osobno, żeby dało się go przywrócić. */
data class HiddenRow(val subjectKey: String, val subject: String, val reason: Reason, val reserve: Int?)

data class ImportantState(
    val loading: Boolean = true,
    val cards: List<WarningCard> = emptyList(),
    val hidden: List<HiddenRow> = emptyList(),
    val nextTest: Pair<String, SchoolEvent>? = null,
)

class ImportantViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container

    val state: StateFlow<ImportantState> = combine(container.snapshot, container.user, container.settings.settings) { snapshot, user, settings ->
        val i = Insights(snapshot, user, settings)
        val statuses = i.statuses.associateBy { it.subjectKey }
        ImportantState(
            loading = false,
            cards = i.warnings.map { w ->
                WarningCard(
                    warning = w,
                    subject = i.name(w.subjectKey),
                    predicted = w.average?.let { predictedGrade(it, settings.grading) },
                    reserve = statuses[w.subjectKey]?.reserve?.reserve,
                    nextTest = statuses[w.subjectKey]?.events?.filter { (it.type == EventType.TEST || it.type == EventType.QUIZ) && it.date >= i.today }?.minByOrNull { it.date },
                    isDifficult = user.prefs[w.subjectKey]?.isDifficult == true,
                    hintTarget = w.hintGrade?.let { settings.grading.thresholds[it] },
                )
            },
            hidden = i.hiddenReasons.map { (subjectKey, reason) ->
                HiddenRow(subjectKey, i.name(subjectKey), reason, statuses[subjectKey]?.reserve?.reserve)
            },
            nextTest = i.nextTest?.let { i.name(it.subjectKey).ifBlank { it.category } to it },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportantState())

    fun setDifficult(subjectKey: String, difficult: Boolean) {
        viewModelScope.launch {
            val current = container.database.user().subjectPrefsOnce().firstOrNull { it.subjectKey == subjectKey } ?: SubjectPrefsEntity(subjectKey)
            container.database.user().upsertSubjectPrefs(current.copy(isDifficult = difficult))
        }
    }

    /** „Ukryj ten powód” — wraca, gdy dane się zmienią (klucz zawiera wartość) albo po przywróceniu. */
    fun hide(subjectKey: String, reason: Reason) {
        viewModelScope.launch { container.database.user().setFlag(UserFlagEntity(Flags.HIDDEN_REASON, reasonKey(subjectKey, reason))) }
    }

    fun restore(subjectKey: String, reason: Reason) {
        viewModelScope.launch { container.database.user().clearFlag(Flags.HIDDEN_REASON, reasonKey(subjectKey, reason)) }
    }

    fun restoreAll() {
        val hidden = state.value.hidden
        viewModelScope.launch {
            hidden.forEach { container.database.user().clearFlag(Flags.HIDDEN_REASON, reasonKey(it.subjectKey, it.reason)) }
        }
    }
}

@Composable
fun ImportantScreen(contentPadding: PaddingValues, onOpenSubject: (String) -> Unit, viewModel: ImportantViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        if (!state.loading && state.cards.isEmpty()) {
            item {
                EmptyState(
                    state.nextTest?.let { (subject, e) -> stringResource(R.string.important_empty_next, subject, formatDate(e.date)) }
                        ?: stringResource(R.string.important_empty),
                )
            }
        }
        items(state.cards, key = { it.warning.subjectKey }) { card ->
            WarningCardView(card, onOpenSubject, viewModel)
        }
        if (state.hidden.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.important_hidden)) }
            item {
                Text(
                    stringResource(R.string.important_hidden_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Eclipse.colors.textSecondary,
                )
            }
            items(state.hidden, key = { it.subjectKey + "|" + it.reason.rule.name }) { row ->
                HiddenRowView(row, viewModel)
            }
            item {
                TextButton(onClick = viewModel::restoreAll) {
                    Text(stringResource(R.string.important_restore_all), color = Eclipse.colors.accentText)
                }
            }
        }
    }
}

@Composable
private fun HiddenRowView(row: HiddenRow, viewModel: ImportantViewModel) {
    val c = Eclipse.colors
    EclipseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.subject, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(reasonText(row.reason, row.reserve), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            TextButton(onClick = { viewModel.restore(row.subjectKey, row.reason) }) {
                Icon(painterResource(R.drawable.ic_visibility), null, Modifier.size(18.dp), tint = c.accentText)
                Text(stringResource(R.string.restore_reason), color = c.accentText, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun WarningCardView(card: WarningCard, onOpenSubject: (String) -> Unit, viewModel: ImportantViewModel) {
    val c = Eclipse.colors
    val w = card.warning
    val color = levelColor(w.level)
    EclipseCard(border = color.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(w.level.icon), null, Modifier.size(22.dp), tint = c.readable(color))
            Tag(stringResource(w.level.word), color, modifier = Modifier.padding(start = 8.dp))
            Column(Modifier.weight(1f)) {}
            Text(
                w.average?.let(::formatPercent) ?: stringResource(R.string.no_grades),
                style = MaterialTheme.typography.displaySmall.merge(TabularNumbers), color = c.text,
            )
        }
        Text(card.subject, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = 4.dp))
        card.predicted?.let { Text(stringResource(R.string.prediction, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }

        Text(stringResource(R.string.important_reasons), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(top = 10.dp))
        w.reasons.forEach { reason ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• " + reasonText(reason, card.reserve), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.hide(w.subjectKey, reason) }) {
                    Icon(painterResource(R.drawable.ic_visibility_off), stringResource(R.string.hide_reason), Modifier.size(18.dp), tint = c.textSecondary)
                }
            }
        }
        val hints = hints(card)
        if (hints.isNotEmpty()) {
            Text(stringResource(R.string.important_hints), style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(top = 6.dp))
            hints.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.accentText, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                stringResource(if (card.isDifficult) R.string.unmark_difficult else R.string.mark_difficult),
                { viewModel.setDifficult(w.subjectKey, !card.isDifficult) },
            )
            TextButton(onClick = { onOpenSubject(w.subjectKey) }) { Text(stringResource(R.string.open_grades)) }
        }
    }
}

private val WarningLevel.icon
    get() = when (this) {
        WarningLevel.CRITICAL -> R.drawable.ic_error
        WarningLevel.WARNING -> R.drawable.ic_warning
        WarningLevel.WATCH -> R.drawable.ic_visibility
    }

@Composable
private fun reasonText(reason: Reason, reserve: Int?): String {
    val value = reason.value
    return when (reason.rule) {
        WarningRule.AVERAGE_BELOW_2 -> stringResource(R.string.reason_below_2, value?.let(::formatPercent).orEmpty())
        WarningRule.PROPOSED_ONE -> stringResource(R.string.reason_proposed_one, reason.grade?.date?.let(::formatDate).orEmpty())
        WarningRule.ATTENDANCE_CRITICAL -> if (reserve != null && reserve <= 0) stringResource(R.string.reason_no_reserve)
        else stringResource(R.string.reason_attendance_critical, value?.let(::formatPercent).orEmpty())
        WarningRule.AVERAGE_IN_2 -> stringResource(R.string.reason_in_2, value?.let(::formatPercent).orEmpty())
        WarningRule.FRESH_LOW_GRADE -> reason.grade?.let { g ->
            stringResource(R.string.reason_fresh, g.symbol, value?.let(::formatPercent) ?: "—", formatDate(g.date), g.category ?: stringResource(R.string.kind_grade).lowercase())
        }.orEmpty()
        WarningRule.ATTENDANCE_NEAR -> value?.let { stringResource(R.string.reason_attendance_near, formatPercent(it)) } ?: stringResource(R.string.reason_reserve_low)
        WarningRule.AVERAGE_DROP -> stringResource(R.string.reason_drop, formatPp(-(value ?: 0.0)))
        WarningRule.JUST_ABOVE_3 -> stringResource(R.string.reason_above_3, value?.let(::formatPercent).orEmpty())
        WarningRule.MARKED_DIFFICULT -> stringResource(R.string.reason_difficult)
        WarningRule.UPCOMING_TEST -> reason.event?.let { e ->
            // dni liczone ze sprawdzianu z tego powodu, nie z najbliższego w przedmiocie
            val days = (e.date.toEpochDay() - pl.eclipse.app.ui.today().toEpochDay()).toInt()
            stringResource(
                R.string.reason_upcoming,
                stringResource(if (e.type == EventType.QUIZ) R.string.word_quiz_cap else R.string.word_test_cap),
                if (days <= 0) stringResource(R.string.today_lower) else stringResource(R.string.in_days, days, pluralStringResource(R.plurals.days, days)),
                formatDate(e.date),
            )
        }.orEmpty()
    }
}

/** Podpowiedzi z szablonów (SPEC 8) — bez AI, z kalkulatora i danych. */
@Composable
private fun hints(card: WarningCard): List<String> = buildList {
    val w = card.warning
    val target = card.hintTarget
    when (val h = w.hint) {
        is GoalResult.Required -> {
            val hintGrade = w.hintGrade
            if (hintGrade != null && target != null) {
                val source = card.nextTest?.let { stringResource(R.string.hint_from_test, formatDate(it.date)) } ?: stringResource(R.string.hint_from_next)
                val grade = h.lowestGrade
                add(
                    if (grade != null) stringResource(R.string.hint_goal_grade, hintGrade, formatPercent(target), source, formatPercent(h.percent), grade)
                    else stringResource(R.string.hint_goal, hintGrade, formatPercent(target), source, formatPercent(h.percent)),
                )
            }
        }
        is GoalResult.NeedsMore -> add(stringResource(R.string.hint_more, h.gradesAt100))
        else -> Unit
    }
    w.reasons.firstOrNull { it.rule == WarningRule.FRESH_LOW_GRADE }?.grade?.let { g ->
        add(stringResource(R.string.hint_fresh, g.symbol, (g.category ?: stringResource(R.string.kind_grade)).lowercase(), formatDate(g.date)))
    }
    if (w.reasons.any { it.rule == WarningRule.ATTENDANCE_CRITICAL || it.rule == WarningRule.ATTENDANCE_NEAR }) {
        card.reserve?.let { r ->
            add(if (r <= 0) stringResource(R.string.hint_no_reserve, card.subject) else stringResource(R.string.hint_reserve, card.subject, r, pluralStringResource(R.plurals.lessons, r)))
        }
    }
    if (w.reasons.any { it.rule == WarningRule.UPCOMING_TEST }) card.nextTest?.let { e ->
        val days = (e.date.toEpochDay() - pl.eclipse.app.ui.today().toEpochDay()).toInt()
        add(stringResource(R.string.hint_upcoming, stringResource(if (e.type == EventType.QUIZ) R.string.word_quiz_cap else R.string.word_test_cap), card.subject, days, pluralStringResource(R.plurals.days, days)))
    }
}
