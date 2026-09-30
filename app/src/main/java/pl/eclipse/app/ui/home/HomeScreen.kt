package pl.eclipse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.R
import pl.eclipse.app.formatPercent
import pl.eclipse.app.ui.components.DiscLabel
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EclipseDisc
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.levelColor
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.LessonStatus

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenTests: () -> Unit,
    onOpenImportant: () -> Unit,
    onOpenGrades: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Eclipse.colors
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        item { Disc(state.disc, onOpenTests) }

        item { SectionTitle(stringResource(if (state.dayLabelTomorrow) R.string.home_tomorrow else R.string.home_today)) }
        if (state.lessons.isEmpty()) item { EmptyState(stringResource(R.string.home_no_lessons)) }
        items(state.lessons, key = { it.key }) { LessonLine(it) }

        if (state.newItems.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.home_new)) }
            item {
                EclipseCard(onClick = onOpenGrades) {
                    state.newItems.take(8).forEach { n ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(n.kind.icon), null, Modifier.size(18.dp), tint = c.accentText)
                            Text(n.title, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 1)
                            Text(n.detail, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), color = c.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.nav_important)) }
        item {
            EclipseCard(onClick = onOpenImportant) {
                if (state.topWarnings.isEmpty()) {
                    Text(stringResource(R.string.home_important_none), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WarningLevel.entries.reversed().forEach { level ->
                            val count = state.warningCounts[level] ?: 0
                            Tag("${stringResource(level.word)}: $count", levelColor(level))
                        }
                    }
                    state.topWarnings.forEach { w ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(levelColor(w.level)))
                            Text(w.subject, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                            Text(
                                w.average?.let(::formatPercent) ?: stringResource(R.string.no_grades),
                                style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                                color = c.readable(levelColor(w.level)),
                            )
                        }
                    }
                }
            }
        }

        item {
            val lucky = state.luckyNumber
            EclipseCard(border = if (state.luckyIsMine) c.accent else c.cardBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_lucky), style = MaterialTheme.typography.titleSmall, color = c.text)
                        if (state.luckyIsMine) Text(stringResource(R.string.home_lucky_mine), style = MaterialTheme.typography.bodyMedium, color = c.accentText)
                    }
                    Text(
                        lucky?.toString() ?: "—",
                        style = MaterialTheme.typography.displaySmall.merge(TabularNumbers),
                        color = if (state.luckyIsMine) c.accentText else c.text,
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.home_homework)) }
        if (state.homework.isEmpty()) item { EmptyState(stringResource(R.string.home_homework_none)) }
        items(state.homework) { (title, due) ->
            EclipseCard {
                Row {
                    Icon(painterResource(R.drawable.ic_menu_book), null, Modifier.size(18.dp), tint = c.readable(Palette.Homework))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text(due, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun Disc(disc: DiscState?, onOpenTests: () -> Unit) {
    val c = Eclipse.colors
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val description = disc?.value?.let { stringResource(R.string.disc_description, disc.subject.orEmpty(), it) }
            ?: stringResource(R.string.disc_none)
        Box(Modifier.clip(CircleShape).clickable(onClick = onOpenTests).semantics { contentDescription = description }) {
            EclipseDisc(coverage = disc?.coverage ?: 0f, size = 240.dp, animateIn = true) {
                val value = disc?.value
                if (value != null) {
                    val count = value.toIntOrNull() ?: 0
                    val caption = when {
                        disc.unitHours -> pluralStringResource(R.plurals.hours, count)
                        count == 0 -> stringResource(R.string.disc_today)
                        else -> pluralStringResource(R.plurals.days, count)
                    }
                    DiscLabel(if (!disc.unitHours && count == 0) "!" else value, caption, disc.subject)
                }
            }
        }
        if (disc?.value == null) {
            Text(
                stringResource(R.string.disc_none),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                stringResource(if (disc.typeQuiz) R.string.disc_next_quiz else R.string.disc_next_test),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun LessonLine(row: LessonRow) {
    val c = Eclipse.colors
    val shape = RoundedCornerShape(10.dp)
    val cancelled = row.status == LessonStatus.CANCELLED
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (row.current) c.accent.copy(alpha = 0.14f) else c.card)
            .border(1.dp, if (row.current) c.accent.copy(alpha = 0.6f) else c.cardBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.time, style = MaterialTheme.typography.titleSmall.merge(TabularNumbers), color = c.textSecondary, modifier = Modifier.width(48.dp))
        Box(Modifier.size(10.dp).clip(CircleShape).background(row.color))
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                row.subject,
                style = MaterialTheme.typography.titleSmall.copy(textDecoration = if (cancelled) TextDecoration.LineThrough else null),
                color = if (cancelled) c.textSecondary else c.text,
            )
            row.room?.let { Text(stringResource(R.string.room, it), style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        }
        row.hasTest?.let { type ->
            Icon(
                painterResource(if (type == EventType.QUIZ) R.drawable.ic_bolt else R.drawable.ic_fact_check),
                stringResource(if (type == EventType.QUIZ) R.string.word_quiz else R.string.word_test),
                Modifier.size(18.dp).padding(end = 2.dp),
                tint = c.readable(if (type == EventType.QUIZ) Palette.Quiz else Palette.Test),
            )
        }
        when {
            row.current -> Tag(stringResource(R.string.now), c.accent)
            row.status == LessonStatus.SUBSTITUTION -> Tag(stringResource(R.string.substitution), Palette.SchoolEvent)
            cancelled -> Tag(stringResource(R.string.cancelled), Palette.Critical)
        }
    }
}

private val NewKind.icon
    get() = when (this) {
        NewKind.GRADE -> R.drawable.ic_school
        NewKind.TEST -> R.drawable.ic_fact_check
        NewKind.MOVED_TEST -> R.drawable.ic_event
        NewKind.MESSAGE -> R.drawable.ic_mail
        NewKind.ANNOUNCEMENT -> R.drawable.ic_campaign
    }

val WarningLevel.word
    get() = when (this) {
        WarningLevel.CRITICAL -> R.string.level_critical
        WarningLevel.WARNING -> R.string.level_warning
        WarningLevel.WATCH -> R.string.level_watch
    }
