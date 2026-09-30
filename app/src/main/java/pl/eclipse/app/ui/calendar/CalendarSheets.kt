@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pl.eclipse.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pl.eclipse.app.R
import pl.eclipse.app.data.CustomEventEntity
import pl.eclipse.app.formatDate
import pl.eclipse.app.ui.components.BlockKind
import pl.eclipse.app.ui.components.BlockStatus
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.core.model.EventType
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun CalendarSheets(sheet: CalendarSheet?, state: CalendarState, viewModel: CalendarViewModel, onChange: (CalendarSheet?) -> Unit) {
    fun find(id: String) = state.itemsByDate.values.asSequence().flatten().firstOrNull { it.id == id }
    when (sheet) {
        null -> Unit
        is CalendarSheet.Details -> find(sheet.itemId)?.let { item ->
            DetailsSheet(
                item = item,
                relatedGrades = (item.ref as? CalendarRef.OfEvent)?.event?.value?.let { viewModel.relatedGrades(it.subjectKey, it.date, it.type) }
                    ?: (item.ref as? CalendarRef.OfLesson)?.tests?.flatMap { viewModel.relatedGrades(it.subjectKey, it.date, it.type) }.orEmpty(),
                onDismiss = { onChange(null) },
                onAddLabel = { onChange(CalendarSheet.AttachLabel(null, item.id)) },
                onRemoveLabel = { viewModel.removeLabel(it.assignment) },
                onEdit = { (item.ref as? CalendarRef.OfCustom)?.let { onChange(CalendarSheet.Edit(it.event)) } },
                onDelete = {
                    (item.ref as? CalendarRef.OfCustom)?.let { viewModel.deleteCustomEvent(it.event) }
                    onChange(null)
                },
            )
        } ?: LaunchedEffect(sheet) { onChange(null) }
        is CalendarSheet.Edit -> EditSheet(
            initial = sheet.event,
            subjects = state.subjects.map { it.sourceKey to it.name },
            onDismiss = { onChange(null) },
            onSave = {
                viewModel.saveCustomEvent(it)
                onChange(null)
            },
            onDelete = {
                viewModel.deleteCustomEvent(it)
                onChange(null)
            },
        )
        is CalendarSheet.AttachLabel -> find(sheet.itemId)?.let { item ->
            AttachLabelDialog(
                item = item,
                labels = state.labels.map { it.id to (it.name to Color(it.color)) },
                preselected = sheet.labelId,
                onDismiss = { onChange(null) },
                onNewLabel = { onChange(CalendarSheet.NewLabel) },
                onConfirm = { labelId, allLessons, note ->
                    val (target, key) = when {
                        item.ref is CalendarRef.OfLesson && allLessons -> "SUBJECT_ALL_LESSONS" to item.subjectKey.orEmpty()
                        item.ref is CalendarRef.OfLesson -> "LESSON" to lessonKey(item.ref.lesson)
                        item.ref is CalendarRef.OfEvent -> "SCHOOL_EVENT" to item.ref.event.value.sourceKey
                        item.ref is CalendarRef.OfCustom -> "CUSTOM_EVENT" to item.ref.event.id.toString()
                        else -> return@AttachLabelDialog
                    }
                    viewModel.attachLabel(labelId, target, key, note)
                    onChange(null)
                },
            )
        } ?: LaunchedEffect(sheet) { onChange(null) }
        CalendarSheet.NewLabel -> NewLabelDialog(onDismiss = { onChange(null) }, onCreate = { name, color ->
            viewModel.createLabel(name, color)
            onChange(null)
        })
    }
}

@Composable
private fun sheetColor() = Eclipse.colors.dialog.compositeOver(Eclipse.colors.backgroundBottom)

@Composable
private fun DetailsSheet(
    item: CalendarItem,
    relatedGrades: List<String>,
    onDismiss: () -> Unit,
    onAddLabel: () -> Unit,
    onRemoveLabel: (AttachedLabel) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Eclipse.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.kind.icon?.let { Icon(painterResource(it), null, Modifier.size(22.dp), tint = c.readable(item.typeColor)) }
                Text(item.title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(start = 8.dp).weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(stringResource(item.kind.word), item.typeColor)
                when (item.status) {
                    BlockStatus.SUBSTITUTION -> Tag(stringResource(R.string.substitution), Palette.SchoolEvent)
                    BlockStatus.CANCELLED -> Tag(stringResource(R.string.cancelled), Palette.Critical)
                    BlockStatus.REMOVED -> Tag(stringResource(R.string.cal_removed), Palette.Critical)
                    BlockStatus.CHANGED -> item.movedFrom?.let { Tag(stringResource(R.string.moved_from, formatDate(it)), Palette.Warning) }
                    BlockStatus.NORMAL -> Unit
                }
            }
            val time = item.start?.let { s -> item.end?.let { e -> "%d:%02d–%d:%02d".format(s.hour, s.minute, e.hour, e.minute) } }
            Detail(listOfNotNull(formatDate(item.date), time, item.lessonNo?.let { stringResource(R.string.lesson_no, it) }).joinToString(" · "))
            when (val ref = item.ref) {
                is CalendarRef.OfLesson -> {
                    ref.lesson.room?.let { Detail(stringResource(R.string.room, it)) }
                    ref.lesson.teacher?.let { Detail(it) }
                    ref.lesson.changeNote?.let { Detail(it) }
                    ref.tests.forEach { t ->
                        Text(
                            stringResource(if (t.type == EventType.QUIZ) R.string.word_quiz_cap else R.string.word_test_cap) + ": " + t.description.ifBlank { t.category },
                            style = MaterialTheme.typography.bodyMedium, color = c.text,
                        )
                    }
                }
                is CalendarRef.OfEvent -> {
                    Detail(ref.event.value.category)
                    ref.event.value.description.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text) }
                    ref.event.previous?.let { prev ->
                        if (prev.date != ref.event.value.date) Detail(stringResource(R.string.history_moved, formatDate(prev.date), formatDate(ref.event.value.date)))
                        else if (prev.description != ref.event.value.description) Detail(stringResource(R.string.history_changed))
                    }
                }
                is CalendarRef.OfHomework -> ref.homework.description.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text) }
                is CalendarRef.OfCustom -> ref.event.note.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text) }
            }
            if (relatedGrades.isNotEmpty()) Detail(stringResource(R.string.related_grade, relatedGrades.joinToString(", ")))

            if (item.labels.isNotEmpty()) {
                Text(stringResource(R.string.labels_title), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(top = 8.dp))
                item.labels.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(l.label.color)))
                        Text(
                            l.label.name + (l.assignment.note.takeIf { it.isNotBlank() }?.let { " — $it" } ?: "") +
                                if (l.assignment.target == "SUBJECT_ALL_LESSONS") " · " + stringResource(R.string.label_scope_all) else "",
                            style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        IconButton(onClick = { onRemoveLabel(l) }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.remove_label, l.label.name), tint = c.textSecondary)
                        }
                    }
                }
            }
            if (item.kind != BlockKind.HOMEWORK && item.kind != BlockKind.DAY_OFF) {
                SecondaryButton(stringResource(R.string.add_label), onAddLabel, Modifier.fillMaxWidth())
            }
            if (item.ref is CalendarRef.OfCustom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(stringResource(R.string.edit), onEdit, Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.delete), onDelete, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary)
}

private val BlockKind.word
    get() = when (this) {
        BlockKind.LESSON -> R.string.kind_lesson
        BlockKind.TEST -> R.string.word_test_cap
        BlockKind.QUIZ -> R.string.word_quiz_cap
        BlockKind.HOMEWORK -> R.string.kind_homework
        BlockKind.EVENT -> R.string.kind_event
        BlockKind.DAY_OFF -> R.string.kind_day_off
        BlockKind.CUSTOM -> R.string.kind_custom
    }

private val EVENT_COLORS = listOf(Palette.Custom, Palette.Homework, Palette.SchoolEvent, Palette.DayOff, Palette.Quiz, Palette.Subjects[1], Palette.Subjects[3])

/** Arkusz edycji własnego wydarzenia: tytuł, czas, kolor, notatka, opcjonalnie przedmiot (SPEC 13). */
@Composable
private fun EditSheet(
    initial: CustomEventEntity,
    subjects: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (CustomEventEntity) -> Unit,
    onDelete: (CustomEventEntity) -> Unit,
) {
    val c = Eclipse.colors
    val startInitial = runCatching { LocalDateTime.parse(initial.start) }.getOrDefault(LocalDateTime.now())
    val endInitial = runCatching { LocalDateTime.parse(initial.end) }.getOrDefault(startInitial.plusHours(1))
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(startInitial.toLocalDate()) }
    var start by remember { mutableStateOf(startInitial.toLocalTime()) }
    var end by remember { mutableStateOf(endInitial.toLocalTime()) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var color by remember { mutableStateOf(initial.color) }
    var note by remember { mutableStateOf(initial.note) }
    var subject by remember { mutableStateOf(initial.subjectKey) }
    var picker by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = sheetColor()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(if (initial.id == 0L) R.string.add_event else R.string.edit_event), style = MaterialTheme.typography.headlineSmall, color = c.text)
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(formatDate(date), { picker = "date" }, Modifier.weight(1f))
                if (!allDay) {
                    SecondaryButton("%d:%02d".format(start.hour, start.minute), { picker = "start" })
                    SecondaryButton("%d:%02d".format(end.hour, end.minute), { picker = "end" })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.all_day), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            Text(stringResource(R.string.event_color), style = MaterialTheme.typography.titleSmall, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EVENT_COLORS.forEach { col ->
                    val selected = (color ?: Palette.Custom.toArgb()) == col.toArgb()
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(col)
                            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
                            .selectable(selected, role = Role.RadioButton) { color = col.toArgb() },
                    )
                }
            }
            Text(stringResource(R.string.event_subject), style = MaterialTheme.typography.titleSmall, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = subject == null, onClick = { subject = null }, label = { Text(stringResource(R.string.no_subject)) })
                subjects.forEach { (key, name) -> FilterChip(selected = subject == key, onClick = { subject = key }, label = { Text(name) }) }
            }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.event_note)) }, modifier = Modifier.fillMaxWidth())
            val valid = title.isNotBlank() && (allDay || end.isAfter(start))
            if (!valid && title.isNotBlank()) Text(stringResource(R.string.event_time_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            PrimaryButton(stringResource(R.string.save_event), {
                onSave(
                    initial.copy(
                        title = title.trim(),
                        start = date.atTime(if (allDay) LocalTime.MIDNIGHT else start).toString(),
                        end = date.atTime(if (allDay) LocalTime.of(23, 59) else end).toString(),
                        allDay = allDay,
                        color = color,
                        note = note.trim(),
                        subjectKey = subject,
                    ),
                )
            }, Modifier.fillMaxWidth(), enabled = valid)
            if (initial.id != 0L) SecondaryButton(stringResource(R.string.delete_event), { onDelete(initial) }, Modifier.fillMaxWidth())
        }
    }

    when (picker) {
        "date" -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        picker = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(state) }
        }
        "start", "end" -> {
            val current = if (picker == "start") start else end
            val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val chosen = LocalTime.of(state.hour, state.minute)
                        if (picker == "start") {
                            val length = java.time.Duration.between(start, end)
                            start = chosen
                            end = chosen.plus(length).takeIf { it.isAfter(chosen) } ?: chosen.plusHours(1)
                        } else end = chosen
                        picker = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cancel)) } },
                text = { TimePicker(state) },
            )
        }
    }
}

/** Po upuszczeniu etykiety na lekcję: „Tylko ta lekcja” albo „Wszystkie lekcje: {przedmiot}” i opcjonalna notatka (SPEC 13). */
@Composable
private fun AttachLabelDialog(
    item: CalendarItem,
    labels: List<Pair<Long, Pair<String, Color>>>,
    preselected: Long?,
    onDismiss: () -> Unit,
    onNewLabel: () -> Unit,
    onConfirm: (Long, Boolean, String) -> Unit,
) {
    val c = Eclipse.colors
    var chosen by remember { mutableStateOf(preselected ?: labels.firstOrNull()?.first) }
    var allLessons by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val isLesson = item.ref is CalendarRef.OfLesson
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sheetColor(),
        title = { Text(stringResource(R.string.add_label), color = c.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (preselected == null) {
                    labels.forEach { (id, pair) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).selectable(chosen == id, role = Role.RadioButton) { chosen = id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = chosen == id, onClick = null)
                            Box(Modifier.padding(start = 4.dp).size(10.dp).clip(CircleShape).background(pair.second))
                            Text(pair.first, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    TextButton(onClick = onNewLabel) { Text(stringResource(R.string.new_label)) }
                } else {
                    labels.firstOrNull { it.first == preselected }?.let { (_, pair) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(pair.second))
                            Text(pair.first, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (isLesson) {
                    listOf(false to stringResource(R.string.label_only_this), true to stringResource(R.string.label_all_lessons, item.title)).forEach { (value, text) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).selectable(allLessons == value, role = Role.RadioButton) { allLessons = value }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = allLessons == value, onClick = null)
                            Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let { onConfirm(it, allLessons, note) } }, enabled = chosen != null) { Text(stringResource(R.string.add_label)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val LABEL_COLORS = listOf(Palette.Critical, Palette.Homework, Palette.Watch, Palette.SchoolEvent, Palette.Custom, Palette.Quiz, Palette.Subjects[1], Palette.DayOff)

@Composable
private fun NewLabelDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    val c = Eclipse.colors
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(LABEL_COLORS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = sheetColor(),
        title = { Text(stringResource(R.string.new_label), color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LABEL_COLORS.forEach { col ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(col)
                                .border(2.dp, if (col == color) c.text else Color.Transparent, CircleShape)
                                .clickable { color = col }
                                .semantics { contentDescription = "#%06X".format(col.toArgb() and 0xFFFFFF) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, color.toArgb()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create_label)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
