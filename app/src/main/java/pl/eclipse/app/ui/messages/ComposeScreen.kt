package pl.eclipse.app.ui.messages

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.core.model.Recipient
import pl.eclipse.core.source.SendResult
import java.io.IOException

data class ComposeState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val recipients: List<Recipient> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val subject: String = "",
    val text: String = "",
    val sending: Boolean = false,
    val result: SendResult? = null,
) {
    val chosen get() = recipients.filter { it.id in selected }

    /** Po niewiadomym wyniku nie pozwalamy wysłać ponownie — wiadomość mogła już pójść. */
    val canSend get() = !sending && result !is SendResult.Unknown &&
        selected.isNotEmpty() && subject.isNotBlank() && text.isNotBlank()

    /** Odbiorcy pasujący do szukania, pogrupowani jak w Librusie. */
    val visible: Map<String, List<Recipient>>
        get() = query.trim().let { q ->
            if (q.isEmpty()) recipients else recipients.filter { it.name.contains(q, true) || it.group.contains(q, true) }
        }.groupBy { it.group }
}

class ComposeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val ui = MutableStateFlow(ComposeState())
    val state: StateFlow<ComposeState> = ui

    init {
        load()
    }

    fun load() {
        ui.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val loaded = runCatching { container.messaging.recipients() }
            ui.update { s ->
                loaded.fold(
                    onSuccess = { s.copy(loading = false, recipients = it, loadError = null) },
                    onFailure = { s.copy(loading = false, loadError = reason(it)) },
                )
            }
        }
    }

    fun toggle(id: String) = ui.update { s ->
        s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
    }

    fun setQuery(value: String) = ui.update { it.copy(query = value) }
    fun setSubject(value: String) = ui.update { it.copy(subject = value) }
    fun setText(value: String) = ui.update { it.copy(text = value) }
    fun dismissResult() = ui.update { it.copy(result = null) }

    fun send() {
        val now = ui.value
        if (!now.canSend) return
        ui.update { it.copy(sending = true, result = null) }
        viewModelScope.launch {
            val sent = runCatching { container.messaging.send(now.selected.toList(), now.subject.trim(), now.text.trim()) }
            ui.update { s ->
                s.copy(
                    sending = false,
                    // Błąd połączenia w trakcie wysyłania: nie wiadomo, czy wiadomość poszła.
                    result = sent.getOrElse { e ->
                        if (e is IOException) SendResult.Unknown(reason(e)) else SendResult.Rejected(reason(e))
                    },
                )
            }
        }
    }

    private fun reason(e: Throwable) = e.message?.takeIf { it.isNotBlank() }
        ?: getApplication<Application>().getString(R.string.compose_error_other, e.javaClass.simpleName)
}

/**
 * „Napisz wiadomość” (SPEC 12.7) w układzie jak w poczcie: wiersz „Do” otwiera osobny ekran z odbiorcami,
 * niżej temat i treść bez ramek. Przed wysłaniem zawsze potwierdzenie — wysłania nie da się cofnąć.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen(
    contentPadding: PaddingValues,
    viewModel: ComposeViewModel,
    onPickRecipients: () -> Unit,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Eclipse.colors
    var confirming by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
        // Do
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPickRecipients).padding(horizontal = 16.dp, vertical = 14.dp),
            // do góry, żeby „Do” stało przy pierwszym odbiorcy, a nie pośrodku listy
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                stringResource(R.string.compose_to),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.width(56.dp).padding(top = 10.dp),
            )
            if (state.chosen.isEmpty()) {
                Text(
                    stringResource(R.string.compose_to_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
            } else {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.chosen.forEach { person ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggle(person.id) },
                            label = { Text(person.name) },
                            trailingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    stringResource(R.string.compose_remove_recipient),
                                    Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(20.dp).padding(top = 2.dp), tint = c.textSecondary)
        }
        HorizontalDivider(color = c.cardBorder)

        // Temat
        TextField(
            value = state.subject,
            onValueChange = viewModel::setSubject,
            placeholder = { Text(stringResource(R.string.compose_subject_placeholder), color = c.textSecondary) },
            singleLine = true,
            colors = flatFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider(color = c.cardBorder)

        // Treść
        TextField(
            value = state.text,
            onValueChange = viewModel::setText,
            placeholder = { Text(stringResource(R.string.compose_text_placeholder), color = c.textSecondary) },
            colors = flatFieldColors(),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        state.loadError?.let { error ->
            Text(
                stringResource(R.string.compose_load_failed) + " " + error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PrimaryButton(
            stringResource(if (state.sending) R.string.compose_sending else R.string.compose_send),
            { confirming = true },
            Modifier.fillMaxWidth().padding(16.dp),
            enabled = state.canSend,
        )
    }

    if (confirming) {
        ConfirmDialog(
            names = state.chosen.joinToString(", ") { it.name },
            subject = state.subject.trim(),
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                viewModel.send()
            },
        )
    }

    state.result?.let { result ->
        ResultDialog(result, onDismiss = viewModel::dismissResult, onDone = onDone)
    }
}

/** Osobny ekran wyboru odbiorców — otwierany z wiersza „Do”. */
@Composable
fun RecipientsScreen(contentPadding: PaddingValues, viewModel: ComposeViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Eclipse.colors

    Column(Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.compose_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.loading) {
            Text(
                stringResource(R.string.compose_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        ) {
            state.loadError?.let { error ->
                item {
                    EclipseCard {
                        Text(stringResource(R.string.compose_load_failed), style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        SecondaryButton(stringResource(R.string.compose_retry_load), viewModel::load, Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            val groups = state.visible
            if (!state.loading && state.loadError == null && groups.isEmpty()) {
                item { EmptyState(stringResource(R.string.compose_nobody_found)) }
            }
            groups.forEach { (group, people) ->
                item(key = "grupa-$group") { SectionTitle(group) }
                items(people, key = { it.id }) { person ->
                    RecipientRow(person, person.id in state.selected) { viewModel.toggle(person.id) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        PrimaryButton(
            if (state.selected.isEmpty()) {
                stringResource(R.string.compose_recipients_done_empty)
            } else {
                stringResource(R.string.compose_recipients_done, state.selected.size)
            },
            onDone,
            Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** Pola tematu i treści bez ramek — jak w poczcie. */
@Composable
private fun flatFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Eclipse.colors.accentText,
    focusedTextColor = Eclipse.colors.text,
    unfocusedTextColor = Eclipse.colors.text,
)

@Composable
private fun RecipientRow(person: Recipient, selected: Boolean, onClick: () -> Unit) {
    val c = Eclipse.colors
    EclipseCard(onClick = onClick, border = if (selected) c.accent.copy(alpha = 0.6f) else c.cardBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                person.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp), tint = c.accentText)
        }
    }
}

@Composable
private fun ConfirmDialog(names: String, subject: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Eclipse.colors.dialog,
        title = { Text(stringResource(R.string.compose_confirm_title)) },
        text = { Text(stringResource(R.string.compose_confirm_body, names, subject)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.compose_confirm_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ResultDialog(result: SendResult, onDismiss: () -> Unit, onDone: () -> Unit) {
    val title = when (result) {
        is SendResult.Sent -> R.string.compose_sent_title
        is SendResult.Rejected -> R.string.compose_rejected_title
        is SendResult.Unknown -> R.string.compose_unknown_title
    }
    val body = when (result) {
        is SendResult.Sent -> stringResource(R.string.compose_sent_body)
        is SendResult.Rejected -> result.reason
        is SendResult.Unknown -> stringResource(R.string.compose_unknown_body, result.reason)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Eclipse.colors.dialog,
        title = { Text(stringResource(title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = if (result is SendResult.Rejected) onDismiss else onDone) {
                Text(stringResource(if (result is SendResult.Rejected) R.string.compose_fix else R.string.ok))
            }
        },
    )
}
