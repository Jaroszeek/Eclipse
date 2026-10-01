package pl.eclipse.app.ui.calendar

import pl.eclipse.app.ui.components.ChoiceChips
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.data.CustomEventEntity
import pl.eclipse.app.data.CustomKind
import pl.eclipse.app.ui.WARSAW
import pl.eclipse.app.ui.components.BlockKind
import pl.eclipse.app.ui.components.BlockStatus
import pl.eclipse.app.ui.components.BlurWhileMoving
import pl.eclipse.app.ui.components.EclipseMotion
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.GlassState
import pl.eclipse.app.ui.components.ScheduleBlock
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.glass
import pl.eclipse.app.ui.components.glassSource
import pl.eclipse.app.ui.components.rememberGlassState
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val POLISH: Locale = Locale.forLanguageTag("pl-PL")
private val MIN_DP = 1.2.dp
private val TIME_COLUMN_BASE = 38.dp

/** Szerokość kolumny godzin rośnie z czcionką systemową, żeby „10:15” się nie łamało. */
private val TIME_COLUMN: Dp
    @Composable get() = TIME_COLUMN_BASE * LocalDensity.current.fontScale.coerceAtLeast(1f)

/** Co pokazuje arkusz nad kalendarzem. */
sealed interface CalendarSheet {
    data class Details(val itemId: String) : CalendarSheet
    data class Edit(val event: CustomEventEntity) : CalendarSheet
    data class AttachLabel(val labelId: Long?, val itemId: String) : CalendarSheet
    data object NewLabel : CalendarSheet
}

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    glass: GlassState,
    toolboxOpen: Boolean,
    onToolboxChange: (Boolean) -> Unit,
    syncing: Boolean,
    onRefresh: () -> Unit,
    viewModel: CalendarViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<CalendarSheet?>(null) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = WEEKS_BACK.toInt()) { WEEKS_TOTAL }

    val tutoringName = stringResource(R.string.kind_tutoring)

    fun create(date: LocalDate, time: LocalTime?, kind: CustomKind = CustomKind.EVENT) {
        val start = date.atTime(time ?: LocalTime.of(16, 0))
        sheet = CalendarSheet.Edit(
            if (kind == CustomKind.HOMEWORK) {
                CustomEventEntity(title = "", start = date.atStartOfDay().toString(), end = date.atTime(23, 59).toString(), allDay = true, kind = kind)
            } else {
                CustomEventEntity(
                    title = if (kind == CustomKind.TUTORING) tutoringName else "",
                    start = start.toString(), end = start.plusHours(1).toString(), kind = kind,
                )
            },
        )
    }

    val actions = CalendarActions(
        onItem = { sheet = CalendarSheet.Details(it.id) },
        onEmpty = ::create,
        onDropLabel = { labelId, item ->
            onToolboxChange(false)
            sheet = CalendarSheet.AttachLabel(labelId, item.id)
        },
        onDropTemplate = { templateId, date, time ->
            onToolboxChange(false)
            state.templates.firstOrNull { it.id == templateId }?.let { sheet = CalendarSheet.Edit(viewModel.eventFromTemplate(it, date, time)) }
        },
    )

    // Własne źródło szkła: Przybornik leży obok kalendarza, nie w nim — Haze nie rozmywa warstwy, w której sam jest.
    val localGlass = rememberGlassState()
    val reduceMotion = Eclipse.reduceMotion
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = syncing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().glassSource(localGlass)) {
            Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
                ViewSwitcher(state.view, viewModel::setView)
                // miejsce pod ostatnim wierszem, żeby przycisk „+” nie zasłaniał lekcji
                val bottomSpace = contentPadding.calculateBottomPadding() + FAB_SPACE
                AnimatedContent(state.view, transitionSpec = { EclipseMotion.through(reduceMotion) }, label = "calendar-view") { view ->
                    BlurWhileMoving {
                        Column(Modifier.fillMaxSize()) {
                            when (view) {
                                CalendarView.WEEK -> WeekPager(state, pager, actions, bottomSpace, onWeekShift = { delta ->
                                    scope.launch { pager.animateScrollToPage((pager.currentPage + delta).coerceIn(0, WEEKS_TOTAL - 1)) }
                                })
                                CalendarView.DAY -> DayView(state, actions, viewModel::shiftDay, bottomSpace)
                                CalendarView.MONTH -> MonthView(state, viewModel::openDay)
                                CalendarView.LIST -> ListView(state, actions, bottomSpace)
                            }
                        }
                    }
                }
            }
        }
        AddEntryButton(
            onHomework = { create(state.today.plusDays(1), null, CustomKind.HOMEWORK) },
            onTutoring = { create(state.today, null, CustomKind.TUTORING) },
            onEvent = { create(state.today, null) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
        if (toolboxOpen) {
            // dotknięcie poza Przybornikiem zamyka go
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onToolboxChange(false) } })
        }
        AnimatedVisibility(
            visible = toolboxOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Toolbox(
                state = state,
                glass = localGlass,
                topPadding = contentPadding.calculateTopPadding(),
                onDragStart = { onToolboxChange(false) },
                onNewLabel = { sheet = CalendarSheet.NewLabel },
                onAddEvent = { create(state.today, null) },
                onOpenItem = { sheet = CalendarSheet.Details(it.id) },
            )
        }
    }

    CalendarSheets(sheet, state, viewModel, onChange = { sheet = it })
}

private val FAB_SPACE = 80.dp

/** Przycisk „+”: własne zadanie domowe, korepetycje albo wydarzenie. */
@Composable
private fun AddEntryButton(onHomework: () -> Unit, onTutoring: () -> Unit, onEvent: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FloatingActionButton(onClick = { open = true }, containerColor = Eclipse.colors.accent, contentColor = Palette.Ink) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_entry))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                Triple(R.string.add_homework, R.drawable.ic_menu_book, onHomework),
                Triple(R.string.add_tutoring, R.drawable.ic_school, onTutoring),
                Triple(R.string.add_event, R.drawable.ic_star, onEvent),
            ).forEach { (text, icon, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(text)) },
                    leadingIcon = { Icon(painterResource(icon), null) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/** Zdarzenia z kalendarza: dotknięcie elementu, wolnego miejsca, upuszczenie etykiety lub szablonu. */
class CalendarActions(
    val onItem: (CalendarItem) -> Unit,
    val onEmpty: (LocalDate, LocalTime?) -> Unit,
    val onDropLabel: (Long, CalendarItem) -> Unit,
    val onDropTemplate: (Long, LocalDate, LocalTime) -> Unit,
)

@Composable
private fun ViewSwitcher(view: CalendarView, onView: (CalendarView) -> Unit) {
    val options = listOf(
        CalendarView.MONTH to R.string.cal_month,
        CalendarView.WEEK to R.string.cal_week,
        CalendarView.DAY to R.string.cal_day,
        CalendarView.LIST to R.string.cal_list,
    )
    ChoiceChips(options.map { (value, label) -> value to stringResource(label) }, view, onView, Modifier.padding(horizontal = 16.dp))
}

private fun dropText(event: DragAndDropEvent): String? = event.toAndroidDragEvent().clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

private fun acceptsText(event: DragAndDropEvent) = event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)

/** Cel upuszczenia etykiety na blok (lekcję, wydarzenie). */
@Composable
private fun Modifier.labelTarget(item: CalendarItem, actions: CalendarActions): Modifier {
    if (item.kind == BlockKind.HOMEWORK || item.kind == BlockKind.DAY_OFF) return this
    val target = remember(item.id) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val text = dropText(event) ?: return false
                if (!text.startsWith("label:")) return false
                val id = text.removePrefix("label:").toLongOrNull() ?: return false
                actions.onDropLabel(id, item)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = ::acceptsText, target = target)
}

@Composable
private fun WeekPager(
    state: CalendarState,
    pager: androidx.compose.foundation.pager.PagerState,
    actions: CalendarActions,
    bottomPadding: Dp,
    onWeekShift: (Int) -> Unit,
) {
    val c = Eclipse.colors
    val monday = state.firstMonday.plusWeeks(pager.currentPage.toLong())
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onWeekShift(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.cal_prev_week), tint = c.text) }
        Text(weekLabel(monday), style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = { onWeekShift(1) }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.cal_next_week), tint = c.text) }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        val weekStart = state.firstMonday.plusWeeks(page.toLong())
        WeekGrid(state, weekStart, actions, bottomPadding)
    }
}

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", POLISH)

private fun weekLabel(monday: LocalDate): String {
    val friday = monday.plusDays(4)
    return if (monday.month == friday.month) "${monday.dayOfMonth}–${friday.format(SHORT_DAY)}" else "${monday.format(SHORT_DAY)} – ${friday.format(SHORT_DAY)}"
}

/** Tydzień: kolumny pn–pt (weekend tylko z wydarzeniami), oś godzin z dzwonków, linia „teraz” (SPEC 13). */
@Composable
private fun WeekGrid(state: CalendarState, monday: LocalDate, actions: CalendarActions, bottomPadding: Dp) {
    val c = Eclipse.colors
    val days = (0L..6L).map(monday::plusDays).filter { d ->
        d.dayOfWeek < DayOfWeek.SATURDAY || state.items(d).isNotEmpty()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp)) {
            Spacer(Modifier.width(TIME_COLUMN))
            days.forEach { d ->
                val isToday = d == state.today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, POLISH), style = MaterialTheme.typography.labelMedium, color = if (isToday) c.accentText else c.textSecondary)
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                        color = if (isToday) c.onAccent else c.text,
                        modifier = Modifier.clip(CircleShape).background(if (isToday) c.accent else Color.Transparent).padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }
        }
        AllDayBar(state, days, actions)
        HorizontalDivider(color = c.border)
        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding, end = 4.dp)) {
            TimeAxis(state)
            days.forEach { d -> DayColumn(state, d, actions, compact = true, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AllDayBar(state: CalendarState, days: List<LocalDate>, actions: CalendarActions) {
    val hasAny = days.any { d -> state.items(d).any { it.allDay } }
    if (!hasAny) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 0.dp)) {
        Spacer(Modifier.width(TIME_COLUMN))
        days.forEach { d ->
            Column(Modifier.weight(1f).padding(horizontal = 1.5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val allDay = state.items(d).filter { it.allDay }
                allDay.take(2).forEach { item ->
                    ScheduleBlock(
                        item.short, item.kind, item.typeColor, item.subjectColor,
                        status = item.status, compact = true, labelColors = item.labels.map { Color(it.label.color) },
                        onClick = { actions.onItem(item) },
                        modifier = Modifier.fillMaxWidth().height(24.dp).labelTarget(item, actions),
                    )
                }
                if (allDay.size > 2) Text("+${allDay.size - 2}", style = MaterialTheme.typography.labelSmall, color = Eclipse.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TimeAxis(state: CalendarState) {
    val c = Eclipse.colors
    val total = state.rangeEnd - state.rangeStart
    Box(Modifier.width(TIME_COLUMN).height(MIN_DP * total)) {
        state.bells.forEach { (_, times) ->
            val minute = times.first.hour * 60 + times.first.minute - state.rangeStart
            Text(
                "%d:%02d".format(times.first.hour, times.first.minute),
                style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                color = c.textSecondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset(y = MIN_DP * minute).padding(start = 4.dp),
            )
        }
    }
}

/** Kolumna jednego dnia: bloki w miejscu godzin, dotknięcie wolnego miejsca tworzy wydarzenie, cel dla szablonów. */
@Composable
private fun DayColumn(state: CalendarState, date: LocalDate, actions: CalendarActions, compact: Boolean, modifier: Modifier = Modifier) {
    val c = Eclipse.colors
    val density = LocalDensity.current
    val total = state.rangeEnd - state.rangeStart
    var top by remember { mutableFloatStateOf(0f) }
    fun minuteAt(y: Float): LocalTime {
        val minutes = state.rangeStart + ((y / density.density) / MIN_DP.value).toInt()
        val rounded = (minutes / 15) * 15
        return LocalTime.of((rounded / 60).coerceIn(0, 23), rounded % 60)
    }
    val templateTarget = remember(date, state.rangeStart) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val text = dropText(event) ?: return false
                if (!text.startsWith("template:")) return false
                val id = text.removePrefix("template:").toLongOrNull() ?: return false
                actions.onDropTemplate(id, date, minuteAt(event.toAndroidDragEvent().y - top))
                return true
            }
        }
    }
    val items = state.items(date).filter { !it.allDay }
    val lanes = lanes(items)
    BoxWithConstraints(
        modifier
            .height(MIN_DP * total)
            .onGloballyPositioned { top = it.positionInRoot().y }
            .dragAndDropTarget(shouldStartDragAndDrop = ::acceptsText, target = templateTarget)
            .pointerInput(date, state.rangeStart) { detectTapGestures { offset -> actions.onEmpty(date, minuteAt(offset.y)) } }
            .drawBehind {
                // linie godzin
                var m = (state.rangeStart / 60 + 1) * 60
                while (m < state.rangeEnd) {
                    val y = (m - state.rangeStart) * MIN_DP.toPx()
                    drawLine(c.border, Offset(0f, y), Offset(size.width, y), 1f)
                    m += 60
                }
            }
            // linia „teraz” rysowana po blokach, inaczej chowała się pod kafelkami lekcji
            .drawWithContent {
                drawContent()
                if (date != state.today) return@drawWithContent
                val now = LocalTime.now(WARSAW)
                val minute = now.hour * 60 + now.minute
                if (minute in state.rangeStart..state.rangeEnd) {
                    val y = (minute - state.rangeStart) * MIN_DP.toPx()
                    drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    drawCircle(c.accent, 4.dp.toPx(), Offset(2.dp.toPx(), y))
                }
            },
    ) {
        val width = maxWidth
        items.forEach { item ->
            val start = item.start ?: return@forEach
            val end = item.end ?: start.plusMinutes(45)
            val startMin = start.hour * 60 + start.minute - state.rangeStart
            val length = (end.hour * 60 + end.minute - (start.hour * 60 + start.minute)).coerceAtLeast(20)
            val (lane, laneCount) = lanes[item.id] ?: (0 to 1)
            val laneWidth = width / laneCount
            ScheduleBlock(
                title = if (compact) item.short else item.title,
                kind = item.kind,
                typeColor = item.typeColor,
                subjectColor = item.subjectColor,
                subtitle = if (compact) null else subtitle(item),
                time = if (compact) null else timeText(item),
                status = item.status,
                tag = tagFor(item, compact),
                important = item.isImportant && item.kind == BlockKind.LESSON,
                labelColors = item.labels.map { Color(it.label.color) },
                compact = compact,
                onClick = { actions.onItem(item) },
                modifier = Modifier
                    .offset(x = laneWidth * lane, y = MIN_DP * startMin)
                    .width(laneWidth)
                    .height(MIN_DP * length)
                    .padding(horizontal = 1.5.dp, vertical = 1.dp)
                    .labelTarget(item, actions),
            )
        }
    }
}

/** Nakładające się elementy obok siebie (prosty podział na tory). */
private fun lanes(items: List<CalendarItem>): Map<String, Pair<Int, Int>> {
    val sorted = items.filter { it.start != null }.sortedBy { it.start }
    val result = mutableMapOf<String, Pair<Int, Int>>()
    var group = mutableListOf<CalendarItem>()
    var groupEnd = LocalTime.MIN
    fun flush() {
        val laneEnds = mutableListOf<LocalTime>()
        val assigned = group.map { item ->
            val lane = laneEnds.indexOfFirst { !it.isAfter(item.start) }.takeIf { it >= 0 } ?: laneEnds.size.also { laneEnds += LocalTime.MIN }
            laneEnds[lane] = item.end ?: item.start!!.plusMinutes(45)
            item.id to lane
        }
        assigned.forEach { (id, lane) -> result[id] = lane to laneEnds.size }
        group = mutableListOf()
    }
    sorted.forEach { item ->
        val start = item.start ?: return@forEach
        if (group.isNotEmpty() && !start.isBefore(groupEnd)) flush()
        group += item
        val end = item.end ?: start.plusMinutes(45)
        if (end.isAfter(groupEnd) || group.size == 1) groupEnd = end
    }
    if (group.isNotEmpty()) flush()
    return result
}

/** Godziny bloku — pokazywane w rogu, nie w podtytule, bo długa nazwa przedmiotu wypychała je poza blok. */
private fun timeText(item: CalendarItem): String? =
    item.start?.let { s -> item.end?.let { e -> "%d:%02d–%d:%02d".format(s.hour, s.minute, e.hour, e.minute) } }

@Composable
private fun subtitle(item: CalendarItem): String? {
    val lesson = (item.ref as? CalendarRef.OfLesson)?.lesson
    return listOfNotNull(
        lesson?.room?.let { stringResource(R.string.room, it) },
        lesson?.teacher,
        item.labels.firstOrNull()?.let { l -> l.label.name + (l.assignment.note.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") },
    ).joinToString(" · ").ifBlank { null }
}

@Composable
private fun tagFor(item: CalendarItem, compact: Boolean): String? = when {
    item.status == BlockStatus.SUBSTITUTION && !compact -> stringResource(R.string.substitution)
    item.status == BlockStatus.CANCELLED && !compact -> stringResource(R.string.cancelled)
    item.status == BlockStatus.REMOVED -> stringResource(R.string.cal_removed)
    (item.kind == BlockKind.TEST || item.kind == BlockKind.QUIZ) && !compact && item.daysUntil >= 0 -> countdown(item.daysUntil)
    else -> null
}

@Composable
fun countdown(days: Long): String = when (days) {
    0L -> stringResource(R.string.today_lower)
    1L -> stringResource(R.string.tomorrow_lower)
    else -> stringResource(R.string.in_days, days.toInt(), pluralStringResource(R.plurals.days, days.toInt()))
}

/** Dzień: jedna kolumna, większe bloki ze szczegółami. */
@Composable
private fun DayView(state: CalendarState, actions: CalendarActions, onShift: (Long) -> Unit, bottomPadding: Dp) {
    val c = Eclipse.colors
    val date = state.day
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.cal_prev_day), tint = c.text) }
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", POLISH)),
            style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onShift(1) }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.cal_next_day), tint = c.text) }
    }
    val allDay = state.items(date).filter { it.allDay }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        allDay.forEach { item ->
            ScheduleBlock(item.title, item.kind, item.typeColor, item.subjectColor, status = item.status, onClick = { actions.onItem(item) }, modifier = Modifier.fillMaxWidth().labelTarget(item, actions))
        }
    }
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = bottomPadding, end = 12.dp, top = 8.dp)) {
        TimeAxis(state)
        DayColumn(state, date, actions, compact = false, modifier = Modifier.weight(1f))
    }
}

/** Miesiąc: dni ze sprawdzianem zabarwione kolorem typu (kartkówka słabiej), inne wpisy jako kropki (SPEC 13). */
@Composable
private fun MonthView(state: CalendarState, onDay: (LocalDate) -> Unit) {
    val c = Eclipse.colors
    val now = remember { YearMonth.now(WARSAW) }
    val calendar = rememberCalendarState(
        startMonth = now.minusMonths(6),
        endMonth = now.plusMonths(10),
        firstVisibleMonth = now,
        firstDayOfWeek = DayOfWeek.MONDAY,
        outDateStyle = OutDateStyle.EndOfGrid,
    )
    HorizontalCalendar(
        state = calendar,
        modifier = Modifier.padding(horizontal = 8.dp),
        monthHeader = { month ->
            Text(
                month.yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, POLISH).replaceFirstChar { it.uppercase() } + " " + month.yearMonth.year,
                style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.padding(8.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                daysOfWeek(DayOfWeek.MONDAY).forEach { d ->
                    Text(d.getDisplayName(TextStyle.SHORT, POLISH), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
        },
        dayContent = { day ->
            val items = state.items(day.date).filter { it.status != BlockStatus.REMOVED }
            val test = items.any { it.kind == BlockKind.TEST }
            val quiz = items.any { it.kind == BlockKind.QUIZ }
            val dots = items.filter { it.kind != BlockKind.LESSON && it.kind != BlockKind.TEST && it.kind != BlockKind.QUIZ }.map { it.typeColor }.distinct().take(3)
            val inMonth = day.position == DayPosition.MonthDate
            val isToday = day.date == state.today
            Box(
                Modifier
                    .aspectRatio(0.9f)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            test -> Palette.Test.copy(alpha = 0.26f)
                            quiz -> Palette.Quiz.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        },
                    )
                    .border(if (isToday) 1.5.dp else 0.dp, if (isToday) c.accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(enabled = inMonth) { onDay(day.date) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
                    color = if (inMonth) c.text else c.textSecondary.copy(alpha = 0.4f),
                )
                if (test || quiz) {
                    Icon(
                        painterResource(if (test) R.drawable.ic_fact_check else R.drawable.ic_bolt),
                        stringResource(if (test) R.string.word_test else R.string.word_quiz),
                        Modifier.size(12.dp).align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp),
                        tint = c.readable(if (test) Palette.Test else Palette.Quiz),
                    )
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    dots.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(it)) }
                }
            }
        },
    )
}

/** Lista: najbliższe 14 dni z nagłówkami dni; zwykłe lekcje pomijamy, zostają zmiany i ważne lekcje. */
@Composable
private fun ListView(state: CalendarState, actions: CalendarActions, bottomPadding: Dp) {
    val days = (0L..13L).map(state.today::plusDays).mapNotNull { d ->
        val items = state.items(d).filter { it.kind != BlockKind.LESSON || it.status != BlockStatus.NORMAL || it.labels.isNotEmpty() }
        if (items.isEmpty()) null else d to items
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (days.isEmpty()) item { EmptyState(stringResource(R.string.cal_list_empty)) }
        days.forEach { (date, items) ->
            item(key = date.toString()) { SectionTitle(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", POLISH)).replaceFirstChar { it.uppercase() }) }
            items(items, key = { it.id }) { item ->
                ScheduleBlock(
                    item.title, item.kind, item.typeColor, item.subjectColor,
                    subtitle = subtitle(item), status = item.status, tag = tagFor(item, false),
                    important = item.isImportant && item.kind == BlockKind.LESSON,
                    labelColors = item.labels.map { Color(it.label.color) },
                    onClick = { actions.onItem(item) },
                    modifier = Modifier.fillMaxWidth().labelTarget(item, actions),
                )
            }
        }
    }
}

/** Przybornik (SPEC 13): etykiety i szablony do przeciągania, najbliższe sprawdziany. Chowa się, gdy zaczyna się przeciąganie. */
@Composable
private fun Toolbox(
    state: CalendarState,
    glass: GlassState,
    topPadding: Dp,
    onDragStart: () -> Unit,
    onNewLabel: () -> Unit,
    onAddEvent: () -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
) {
    val c = Eclipse.colors
    Column(
        Modifier
            .fillMaxHeight()
            .width(280.dp)
            .glass(glass, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
            .padding(top = topPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.open_toolbox), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Text(stringResource(R.string.toolbox_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        SectionTitle(stringResource(R.string.toolbox_labels))
        state.labels.forEach { label -> DraggableChip(label.name, Color(label.color), "label:${label.id}", onDragStart) }
        TextButton(onClick = onNewLabel) { Text(stringResource(R.string.new_label)) }
        SectionTitle(stringResource(R.string.toolbox_templates))
        state.templates.forEach { t -> DraggableChip(stringResource(R.string.template_chip, t.name, t.durationMinutes), t.color?.let(::Color) ?: Palette.Custom, "template:${t.id}", onDragStart) }
        TextButton(onClick = onAddEvent) { Text(stringResource(R.string.add_event)) }
        SectionTitle(stringResource(R.string.toolbox_upcoming))
        if (state.upcomingTests.isEmpty()) EmptyState(stringResource(R.string.disc_none))
        state.upcomingTests.forEach { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpenItem(item) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(if (item.kind == BlockKind.QUIZ) R.drawable.ic_bolt else R.drawable.ic_fact_check), null, Modifier.size(16.dp), tint = c.readable(item.typeColor))
                Text(item.title.substringBefore(" — "), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                Text(countdown(item.daysUntil), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun DraggableChip(text: String, color: Color, payload: String, onDragStart: () -> Unit) {
    val c = Eclipse.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .dragAndDropSource { _ ->
                onDragStart()
                DragAndDropTransferData(ClipData.newPlainText("eclipse", payload))
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_drag_indicator), null, Modifier.size(18.dp), tint = c.textSecondary)
        Box(Modifier.padding(start = 6.dp).size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.padding(start = 8.dp))
    }
}
