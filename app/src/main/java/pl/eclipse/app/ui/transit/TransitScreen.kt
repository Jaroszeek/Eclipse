package pl.eclipse.app.ui.transit

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.TransitInfo
import pl.eclipse.app.data.TransitNode
import pl.eclipse.app.data.TransitProgress
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.core.transit.TransitFeed

data class TransitState(
    val loading: Boolean = true,
    val info: TransitInfo? = null,
    val progress: TransitProgress? = null,
    val failed: Boolean = false,
    val nodes: List<TransitNode> = emptyList(),
)

class TransitViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.container.transit
    private val _state = MutableStateFlow(TransitState())
    val state: StateFlow<TransitState> = _state.asStateFlow()
    private var searching: Job? = null

    init {
        reload("")
    }

    private fun reload(query: String) {
        viewModelScope.launch {
            val info = store.info()
            _state.update { it.copy(loading = false, info = info, nodes = store.nodes(query)) }
        }
    }

    fun search(query: String) {
        searching?.cancel()
        searching = viewModelScope.launch { _state.update { it.copy(nodes = store.nodes(query)) } }
    }

    /** Pobiera rozkłady z ZTP i wgrywa je do bazy. Trwa długo, więc pokazujemy postęp. */
    fun refresh() {
        if (_state.value.progress != null) return
        viewModelScope.launch {
            _state.update { it.copy(progress = TransitProgress(TransitFeed.TRAM, TransitProgress.Phase.DOWNLOAD), failed = false) }
            val result = runCatching { store.refresh { progress -> _state.update { it.copy(progress = progress) } } }
            result.exceptionOrNull()?.let { Log.w("Eclipse", "Rozklady ZTP: " + it.javaClass.simpleName + ": " + it.message) }
            _state.update { it.copy(progress = null, failed = result.isFailure) }
            reload("")
        }
    }
}

/** Dojazd (SPEC 17): rozkłady ZTP Kraków pobrane na telefon, wyszukiwanie liczone na miejscu. */
@Composable
fun TransitScreen(contentPadding: PaddingValues, viewModel: TransitViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = state.progress
    Column(Modifier.padding(horizontal = 16.dp)) {
        when {
            progress != null -> ProgressCard(progress, contentPadding)
            state.loading -> Unit
            state.info == null -> StartCard(state.failed, contentPadding, viewModel::refresh)
            else -> StopList(state, contentPadding, viewModel::search, viewModel::refresh)
        }
    }
}

@Composable
private fun ProgressCard(progress: TransitProgress, contentPadding: PaddingValues) {
    val feed = progress.feed?.let { stringResource(if (it == TransitFeed.TRAM) R.string.transit_feed_tram else R.string.transit_feed_bus) }
    val text = when (progress.phase) {
        TransitProgress.Phase.DOWNLOAD -> stringResource(R.string.transit_phase_download, feed.orEmpty())
        TransitProgress.Phase.IMPORT -> stringResource(R.string.transit_phase_import, feed.orEmpty())
        TransitProgress.Phase.INDEX -> stringResource(R.string.transit_phase_index)
    }
    Column(Modifier.padding(contentPadding)) {
        EclipseCard {
            Text(text, style = MaterialTheme.typography.titleMedium, color = Eclipse.colors.text)
            Spacer(Modifier.height(12.dp))
            val fraction = progress.fraction
            if (fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Eclipse.colors.accent)
            } else {
                LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth(), color = Eclipse.colors.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.transit_wait), style = MaterialTheme.typography.bodySmall, color = Eclipse.colors.textSecondary)
        }
    }
}

@Composable
private fun StartCard(failed: Boolean, contentPadding: PaddingValues, onDownload: () -> Unit) {
    Column(Modifier.padding(contentPadding)) {
        EclipseCard {
            Text(stringResource(R.string.transit_empty_title), style = MaterialTheme.typography.titleMedium, color = Eclipse.colors.text)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.transit_empty_body), style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary)
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.transit_error), style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.readable(Palette.Critical))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.transit_download), onDownload)
        }
    }
}

@Composable
private fun StopList(state: TransitState, contentPadding: PaddingValues, onSearch: (String) -> Unit, onRefresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                label = { Text(stringResource(R.string.transit_search)) },
                singleLine = true,
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { query = ""; onSearch("") }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.inbox_search_clear), Modifier.size(18.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.nodes.isEmpty()) {
            item { EmptyState(stringResource(R.string.transit_no_stops)) }
        }
        items(state.nodes, key = { it.id }) { node ->
            EclipseCard(padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(node.name, style = MaterialTheme.typography.bodyLarge, color = Eclipse.colors.text)
            }
        }
        item {
            val info = state.info ?: return@item
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.transit_version, formatFeedVersion(info.version)) + " · " +
                    pluralStringResource(R.plurals.transit_stops, info.nodes, info.nodes),
                style = MaterialTheme.typography.bodySmall,
                color = Eclipse.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.transit_refresh), onRefresh)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** „20260930” na „30.09.2026”; gdy rozkład nie podaje wersji, zostaje myślnik. */
private fun formatFeedVersion(version: String): String =
    if (version.length == 8 && version.all { it.isDigit() }) {
        version.substring(6) + "." + version.substring(4, 6) + "." + version.substring(0, 4)
    } else {
        version.ifBlank { "—" }
    }
