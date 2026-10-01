package pl.eclipse.app.ui.inbox

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.Flags
import pl.eclipse.app.data.NotificationEntity
import pl.eclipse.app.data.UserFlagEntity
import pl.eclipse.app.formatDate
import pl.eclipse.app.ui.WARSAW
import pl.eclipse.app.ui.components.ChoiceChips
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.formatSyncTime
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.core.model.NoteKind

enum class InboxTab { MESSAGES, ANNOUNCEMENTS, NOTES, SENT }

data class InboxItem(val key: String, val title: String, val meta: String, val body: String, val unread: Boolean, val note: NoteKind? = null)

/** Wysłane nie są synchronizowane — pobieramy je na żądanie, więc mają własny stan ładowania. */
data class SentState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val items: List<InboxItem> = emptyList(),
)

data class InboxState(
    val messages: List<InboxItem> = emptyList(),
    val announcements: List<InboxItem> = emptyList(),
    val notes: List<InboxItem> = emptyList(),
    val sent: SentState = SentState(),
)

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val attachment = application.getString(R.string.inbox_attachment)
    private val sentState = MutableStateFlow(SentState())

    val state: StateFlow<InboxState> = combine(container.snapshot, container.user, sentState) { snapshot, user, sent ->
        val read = user.keys(Flags.READ)
        InboxState(
            sent = sent,
            messages = snapshot.messages.sortedByDescending { it.value.sentAt }.map { m ->
                val v = m.value
                val meta = v.sender + " · " + formatDate(v.sentAt.atZone(WARSAW).toLocalDate()) + if (v.hasAttachment) " · $attachment" else ""
                // przeczytana w Eclipse albo już w Librusie
                InboxItem(v.sourceKey, v.title, meta, v.content.orEmpty(), v.sourceKey !in read && v.readAt == null)
            },
            announcements = snapshot.announcements.sortedByDescending { it.value.date }.map { a ->
                val v = a.value
                InboxItem(v.sourceKey, v.title, listOfNotNull(v.author, formatDate(v.date)).joinToString(" · "), v.content, v.sourceKey !in read)
            },
            notes = snapshot.notes.sortedByDescending { it.value.date }.map { n ->
                val v = n.value
                InboxItem(v.sourceKey, v.category ?: "", listOfNotNull(v.teacher, formatDate(v.date)).joinToString(" · "), v.text, v.sourceKey !in read, v.kind)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxState())

    fun markRead(key: String) {
        viewModelScope.launch { container.database.user().setFlag(UserFlagEntity(Flags.READ, key)) }
    }

    /** Wszystko w Skrzynce jako przeczytane — tylko w Eclipse, w Librusie nic się nie zmienia. */
    fun markAllRead() {
        val unread = state.value.let { it.messages + it.announcements + it.notes }.filter { it.unread }
        viewModelScope.launch { container.database.user().setFlags(unread.map { UserFlagEntity(Flags.READ, it.key) }) }
    }

    /** Wysłane z Librusa: przy pierwszym wejściu w zakładkę, a potem na żądanie („Odśwież”). */
    fun loadSent(force: Boolean = false) {
        val now = sentState.value
        if (now.loading || (now.loaded && !force)) return
        sentState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val loaded = runCatching { container.messaging.sent() }
            sentState.update { s ->
                loaded.fold(
                    onSuccess = { list ->
                        s.copy(loading = false, loaded = true, error = null, items = list.map { sent ->
                            val date = formatDate(sent.sentAt.atZone(WARSAW).toLocalDate())
                            val meta = if (sent.recipients.isBlank()) date else getApplication<Application>()
                                .getString(R.string.inbox_sent_to, sent.recipients) + " · " + date
                            InboxItem(sent.sourceKey, sent.title, meta, sent.content.orEmpty(), unread = false)
                        })
                    },
                    onFailure = { e -> s.copy(loading = false, error = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName) },
                )
            }
        }
    }
}

/** Skrzynka (SPEC 12.7): tylko odczyt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxScreen(contentPadding: PaddingValues, onCompose: () -> Unit = {}, viewModel: InboxViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(InboxTab.MESSAGES) }
    var query by rememberSaveable { mutableStateOf("") }
    val all = when (tab) {
        InboxTab.MESSAGES -> state.messages
        InboxTab.ANNOUNCEMENTS -> state.announcements
        InboxTab.NOTES -> state.notes
        InboxTab.SENT -> state.sent.items
    }
    // szukanie po nadawcy, temacie i treści — w zakładce, która jest otwarta
    val needle = query.trim()
    val items = if (needle.isEmpty()) all else all.filter {
        it.title.contains(needle, true) || it.meta.contains(needle, true) || it.body.contains(needle, true)
    }
    LaunchedEffect(tab) { if (tab == InboxTab.SENT) viewModel.loadSent() }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.inbox_search)) },
                singleLine = true,
                trailingIcon = if (query.isEmpty()) null else ({
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.inbox_search_clear), Modifier.size(18.dp))
                    }
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            val unread = mapOf(
                InboxTab.MESSAGES to state.messages.count { it.unread },
                InboxTab.ANNOUNCEMENTS to state.announcements.count { it.unread },
                InboxTab.NOTES to state.notes.count { it.unread },
            )
            ChoiceChips(
                listOf(
                    InboxTab.MESSAGES to stringResource(R.string.inbox_messages),
                    InboxTab.ANNOUNCEMENTS to stringResource(R.string.inbox_announcements),
                    InboxTab.NOTES to stringResource(R.string.inbox_notes),
                    InboxTab.SENT to stringResource(R.string.inbox_sent),
                ),
                tab,
                { tab = it },
                badges = unread,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == InboxTab.MESSAGES) {
                    TextButton(onClick = onCompose) {
                        Icon(painterResource(R.drawable.ic_mail), null, Modifier.size(18.dp), tint = Eclipse.colors.accentText)
                        Text(stringResource(R.string.inbox_write), color = Eclipse.colors.accentText, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (tab == InboxTab.SENT) {
                    TextButton(onClick = { viewModel.loadSent(force = true) }, enabled = !state.sent.loading) {
                        Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(18.dp), tint = Eclipse.colors.accentText)
                        Text(stringResource(R.string.inbox_refresh), color = Eclipse.colors.accentText, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (tab != InboxTab.SENT && unread.values.any { it > 0 }) {
                    TextButton(onClick = viewModel::markAllRead) {
                        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp), tint = Eclipse.colors.accentText)
                        Text(stringResource(R.string.inbox_mark_all_read), color = Eclipse.colors.accentText, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (tab == InboxTab.SENT) {
            if (state.sent.loading) {
                item {
                    Text(stringResource(R.string.inbox_sent_loading), style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary)
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            state.sent.error?.let { error ->
                item {
                    EclipseCard {
                        Text(stringResource(R.string.inbox_sent_failed), style = MaterialTheme.typography.titleSmall, color = Eclipse.colors.text)
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (items.isEmpty() && !state.sent.loading && (tab != InboxTab.SENT || state.sent.error == null)) {
            item {
                EmptyState(
                    when {
                        needle.isNotEmpty() -> stringResource(R.string.inbox_search_none, needle)
                        tab == InboxTab.SENT -> stringResource(R.string.inbox_sent_empty)
                        else -> stringResource(R.string.inbox_empty)
                    },
                )
            }
        }
        items(items, key = { it.key }) { item -> InboxRow(item, onOpen = { viewModel.markRead(item.key) }) }
    }
}

@Composable
private fun InboxRow(item: InboxItem, onOpen: () -> Unit) {
    val c = Eclipse.colors
    var expanded by remember { mutableStateOf(false) }
    EclipseCard(
        onClick = {
            expanded = !expanded
            if (item.unread) onOpen()
        },
        border = if (item.unread) c.accent.copy(alpha = 0.6f) else c.cardBorder,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.note?.let { kind ->
                Icon(
                    painterResource(if (kind == NoteKind.NEGATIVE) R.drawable.ic_thumb_down else R.drawable.ic_thumb_up),
                    stringResource(
                        when (kind) {
                            NoteKind.POSITIVE -> R.string.note_positive
                            NoteKind.NEGATIVE -> R.string.note_negative
                            NoteKind.NEUTRAL -> R.string.note_neutral
                        },
                    ),
                    Modifier.size(18.dp).padding(end = 6.dp),
                    tint = c.readable(if (kind == NoteKind.NEGATIVE) Palette.Critical else if (kind == NoteKind.POSITIVE) Palette.DayOff else c.textSecondary),
                )
            }
            Text(
                item.title.ifBlank { stringResource(R.string.inbox_note) },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (item.unread) FontWeight.Bold else FontWeight.SemiBold),
                color = c.text, modifier = Modifier.weight(1f),
            )
            if (item.unread) Text(stringResource(R.string.unread), style = MaterialTheme.typography.labelSmall, color = c.accentText)
        }
        Text(item.meta, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (item.body.isNotBlank()) {
            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = if (expanded) Int.MAX_VALUE else 2, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    val notifications: StateFlow<List<NotificationEntity>> = container.user.map { it.notifications }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markAllRead() {
        viewModelScope.launch { container.database.user().markNotificationsRead(System.currentTimeMillis()) }
    }
}

/** Centrum powiadomień (dzwonek, SPEC 9): ostatnie zdarzenia, także gdy powiadomienia systemowe są wyłączone. */
@Composable
fun NotificationsScreen(contentPadding: PaddingValues, onOpenRoute: (String) -> Unit, viewModel: NotificationsViewModel = viewModel()) {
    val list by viewModel.notifications.collectAsStateWithLifecycle()
    val c = Eclipse.colors
    LaunchedEffect(Unit) { viewModel.markAllRead() }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        if (list.isEmpty()) item { EmptyState(stringResource(R.string.notifications_empty)) }
        items(list, key = { it.id }) { n ->
            EclipseCard(onClick = { n.route?.let(onOpenRoute) }, border = if (n.readAt == null) c.accent.copy(alpha = 0.6f) else c.cardBorder) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, color = c.text)
                        if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                    Text(formatSyncTime(n.postedAt), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}
