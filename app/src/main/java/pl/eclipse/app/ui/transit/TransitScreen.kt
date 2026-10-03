package pl.eclipse.app.ui.transit

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.TransitInfo
import pl.eclipse.app.data.TransitJourney
import pl.eclipse.app.data.TransitNode
import pl.eclipse.app.data.TransitPlace
import pl.eclipse.app.data.LOCATION_PERMISSION
import pl.eclipse.app.data.RouteTileEntity
import pl.eclipse.app.data.SavedPlaceEntity
import pl.eclipse.app.data.TransitProgress
import pl.eclipse.app.data.currentLocation
import pl.eclipse.app.data.hasLocationPermission
import pl.eclipse.app.ui.WARSAW
import pl.eclipse.app.ui.components.ChoiceChips
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EmptyState
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.core.transit.TransitFeed
import java.time.LocalDate
import java.time.LocalTime

/** Które pole przystanku wybieramy. */
enum class StopField { FROM, TO }

/** Zapisane miejsce z odnalezionym przystankiem; [node] jest puste, gdy rozkład go już nie zna. */
data class SavedStop(val id: Long, val label: String, val stopName: String, val node: TransitNode?)

data class TransitState(
    val loading: Boolean = true,
    val info: TransitInfo? = null,
    val progress: TransitProgress? = null,
    val failed: Boolean = false,
    val stops: List<TransitNode> = emptyList(),
    val from: TransitNode? = null,
    val to: TransitNode? = null,
    /** null znaczy „teraz”. */
    val time: LocalTime? = null,
    /** Czy wpisana godzina to godzina przyjazdu, a nie odjazdu. */
    val arriveBy: Boolean = false,
    val places: List<TransitPlace> = emptyList(),
    /** Lista przystanków pokazuje teraz najbliższe, nie wyniki szukania. */
    val nearby: Boolean = false,
    /** Miejsce, wokół którego pokazujemy przystanki; null, gdy to po prostu okolica telefonu. */
    val nearbyPlace: String? = null,
    val locating: Boolean = false,
    val locationFailed: Boolean = false,
    val saved: List<SavedStop> = emptyList(),
    val tiles: List<RouteTileEntity> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val journeys: List<TransitJourney> = emptyList(),
)

class TransitViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.container.transit
    private val places = application.container.places
    private val _state = MutableStateFlow(TransitState())
    val state: StateFlow<TransitState> = _state.asStateFlow()
    private var stopSearch: Job? = null
    private var journeySearch: Job? = null

    private val user = application.container.database.user()

    init {
        viewModelScope.launch { _state.update { it.copy(loading = false, info = store.info()) } }
        // zapisane miejsca odnajdujemy po nazwie, bo numery przystanków powstają od nowa przy każdym wgraniu rozkładu
        viewModelScope.launch {
            combine(user.savedPlaces(), user.routeTiles()) { places, tiles -> places to tiles }.collect { (places, tiles) ->
                val resolved = places.map { SavedStop(it.id, it.label, it.stopName, store.nodeByName(it.stopName)) }
                _state.update { it.copy(saved = resolved, tiles = tiles) }
            }
        }
    }

    fun savePlace(label: String, stopName: String) {
        viewModelScope.launch { user.upsertSavedPlace(SavedPlaceEntity(label = label.trim(), stopName = stopName)) }
    }

    fun deleteSavedPlace(saved: SavedStop) {
        viewModelScope.launch { user.deleteSavedPlace(SavedPlaceEntity(saved.id, saved.label, saved.stopName)) }
    }

    /** Zapisuje obecną trasę jako panel na Pulpicie. */
    fun addTile() {
        val from = _state.value.from ?: return
        val to = _state.value.to ?: return
        viewModelScope.launch {
            user.upsertRouteTile(RouteTileEntity(fromName = from.name, toName = to.name, position = user.nextTilePosition()))
        }
    }

    fun deleteTile(tile: RouteTileEntity) {
        viewModelScope.launch { user.deleteRouteTile(tile) }
    }

    fun searchStops(query: String) {
        stopSearch?.cancel()
        stopSearch = viewModelScope.launch {
            val stops = store.nodes(query)
            // miejsc szukamy tylko wtedy, gdy coś wpisano — pusta lista przystanków to po prostu spis wszystkich
            val found = if (query.isBlank()) emptyList() else places.search(query)
            _state.update {
                it.copy(stops = stops, places = found, nearby = false, nearbyPlace = null, locationFailed = false)
            }
        }
    }

    /** Po wybraniu miejsca pokazujemy przystanki najbliżej niego — to z nich trzeba wsiąść. */
    fun pickPlace(place: TransitPlace) {
        stopSearch?.cancel()
        stopSearch = viewModelScope.launch {
            val near = store.nearest(places.points(place.name).ifEmpty { listOf(place.lat to place.lon) })
            _state.update { it.copy(stops = near, places = emptyList(), nearby = true, nearbyPlace = place.name) }
        }
    }

    /** Przystanki najbliżej telefonu. Położenie zostaje w telefonie — służy tylko do ustawienia listy. */
    fun useLocation(context: Context) {
        stopSearch?.cancel()
        stopSearch = viewModelScope.launch {
            _state.update { it.copy(locating = true, locationFailed = false) }
            val here = currentLocation(context)
            val found = here?.let { (lat, lon) -> store.nearest(lat, lon) }.orEmpty()
            _state.update {
                it.copy(
                    locating = false,
                    nearby = found.isNotEmpty(),
                    nearbyPlace = null,
                    locationFailed = found.isEmpty(),
                    stops = found.ifEmpty { it.stops },
                    places = if (found.isEmpty()) it.places else emptyList(),
                )
            }
        }
    }

    fun pick(field: StopField, node: TransitNode) {
        _state.update { if (field == StopField.FROM) it.copy(from = node) else it.copy(to = node) }
        search()
    }

    fun swap() {
        _state.update { it.copy(from = it.to, to = it.from) }
        search()
    }

    fun setTime(time: LocalTime?) {
        _state.update { it.copy(time = time) }
        search()
    }

    fun setArriveBy(arriveBy: Boolean) {
        _state.update { it.copy(arriveBy = arriveBy) }
        search()
    }

    private fun search() {
        val current = _state.value
        val from = current.from ?: return
        val to = current.to ?: return
        journeySearch?.cancel()
        journeySearch = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            val time = current.time ?: LocalTime.now(WARSAW)
            val found = store.journeys(from.id, to.id, LocalDate.now(WARSAW), time.hour * 60 + time.minute, current.arriveBy)
            _state.update { it.copy(searching = false, searched = true, journeys = found) }
        }
    }

    /** Pobiera rozkłady z ZTP i wgrywa je do bazy. Trwa długo, więc pokazujemy postęp. */
    fun refresh() {
        if (_state.value.progress != null) return
        viewModelScope.launch {
            _state.update { it.copy(progress = TransitProgress(TransitFeed.TRAM, TransitProgress.Phase.DOWNLOAD), failed = false) }
            val result = runCatching { store.refresh { progress -> _state.update { it.copy(progress = progress) } } }
            result.exceptionOrNull()?.let { Log.w("Eclipse", "Rozklady ZTP: " + it.javaClass.simpleName + ": " + it.message) }
            val info = store.info()
            _state.update { it.copy(progress = null, failed = result.isFailure, info = info, journeys = emptyList(), searched = false) }
        }
    }
}

/** Dojazd (SPEC 17): rozkłady ZTP Kraków pobrane na telefon, połączenia liczone na miejscu. */
@Composable
fun TransitScreen(contentPadding: PaddingValues, viewModel: TransitViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<StopField?>(null) }
    val progress = state.progress
    val picked = picking
    Column(Modifier.padding(horizontal = 16.dp)) {
        when {
            progress != null -> ProgressCard(progress, contentPadding)
            state.loading -> Unit
            state.info == null -> StartCard(state.failed, contentPadding, viewModel::refresh)
            picked != null -> StopPicker(
                state,
                contentPadding,
                viewModel::searchStops,
                viewModel::useLocation,
                viewModel::pickPlace,
                viewModel::savePlace,
                viewModel::deleteSavedPlace,
            ) {
                viewModel.pick(picked, it)
                picking = null
            }
            else -> SearchPane(
                state,
                contentPadding,
                onPick = { picking = it; viewModel.searchStops("") },
                onSwap = viewModel::swap,
                onTime = viewModel::setTime,
                onArriveBy = viewModel::setArriveBy,
                onAddTile = viewModel::addTile,
                onDeleteTile = viewModel::deleteTile,
                onRefresh = viewModel::refresh,
            )
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
                Text(
                    stringResource(R.string.transit_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Eclipse.colors.readable(Palette.Critical),
                )
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.transit_download), onDownload)
        }
    }
}

/** Wybór przystanku: szukanie po nazwie i lista wyników (SPEC 17.3). */
@Composable
private fun StopPicker(
    state: TransitState,
    contentPadding: PaddingValues,
    onSearch: (String) -> Unit,
    onLocation: (Context) -> Unit,
    onPlace: (TransitPlace) -> Unit,
    onSave: (String, String) -> Unit,
    onForget: (SavedStop) -> Unit,
    onPick: (TransitNode) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf<TransitNode?>(null) }
    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onLocation(context)
    }
    // ekran otwiera się po to, żeby wpisać nazwę — klawiatura ma czekać gotowa
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !state.locating,
                    onClick = {
                        query = ""
                        if (hasLocationPermission(context)) onLocation(context) else askLocation.launch(LOCATION_PERMISSION)
                    },
                ) {
                    Icon(painterResource(R.drawable.ic_my_location), null, Modifier.size(18.dp), tint = Eclipse.colors.accentText)
                    Text(
                        stringResource(if (state.locating) R.string.transit_locating else R.string.transit_nearby),
                        color = Eclipse.colors.accentText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (state.nearby) {
                Text(
                    state.nearbyPlace?.let { stringResource(R.string.transit_near_place, it) }
                        ?: stringResource(R.string.transit_nearby_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Eclipse.colors.textSecondary,
                )
            }
            if (state.locationFailed) {
                Text(
                    stringResource(R.string.transit_location_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Eclipse.colors.readable(Palette.Critical),
                )
            }
        }
        if (state.saved.isNotEmpty() && query.isBlank() && !state.nearby) {
            item {
                Text(
                    stringResource(R.string.transit_saved_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Eclipse.colors.textSecondary,
                )
            }
            items(state.saved, key = { "saved-" + it.id }) { saved ->
                EclipseCard(
                    onClick = { saved.node?.let(onPick) },
                    padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(saved.label, style = MaterialTheme.typography.bodyLarge, color = Eclipse.colors.text)
                            Text(
                                saved.node?.name ?: stringResource(R.string.transit_saved_missing, saved.stopName),
                                style = MaterialTheme.typography.bodySmall,
                                color = Eclipse.colors.textSecondary,
                            )
                        }
                        IconButton(onClick = { onForget(saved) }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.transit_forget),
                                Modifier.size(18.dp),
                                tint = Eclipse.colors.textSecondary,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.transit_stops_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Eclipse.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (state.stops.isEmpty() && state.places.isEmpty() && !state.locating) {
            item { EmptyState(stringResource(R.string.transit_no_stops)) }
        }
        items(state.stops, key = { it.id }) { stop ->
            EclipseCard(onClick = { onPick(stop) }, padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stop.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Eclipse.colors.text,
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    )
                    IconButton(onClick = { saving = stop }) {
                        Icon(
                            painterResource(R.drawable.ic_star),
                            stringResource(R.string.transit_save_place),
                            Modifier.size(18.dp),
                            tint = Eclipse.colors.accentText,
                        )
                    }
                }
            }
        }
        if (state.places.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.transit_places_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Eclipse.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(state.places, key = { it.name + it.lat }) { place ->
            EclipseCard(onClick = { onPlace(place) }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Text(place.name, style = MaterialTheme.typography.bodyLarge, color = Eclipse.colors.text, maxLines = 2)
                placeKind(place.kind)?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = Eclipse.colors.textSecondary)
                }
            }
        }
    }
    saving?.let { stop ->
        SavePlaceDialog(stop, onSave = { onSave(it, stop.name); saving = null }, onDismiss = { saving = null })
    }
}

/** Nadanie nazwy zapisanemu miejscu: „Dom”, „Szkoła”. */  
@Composable
private fun SavePlaceDialog(stop: TransitNode, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.transit_save_title), color = Eclipse.colors.text) },
        text = {
            Column {
                Text(stop.name, style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.transit_save_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTED.forEach { suggestion ->
                        TextButton(onClick = { label = suggestion }) {
                            Text(suggestion, color = Eclipse.colors.accentText)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onSave(label) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val SUGGESTED = listOf("Dom", "Szkoła", "Praca")

/** Najczęstsze rodzaje miejsc po polsku; reszta zostaje bez podpisu. */
private fun placeKind(kind: String): Int? = when (kind) {
    "street", "residential", "living_street", "pedestrian", "primary", "secondary", "tertiary", "unclassified" -> R.string.place_street
    "school", "college", "university", "kindergarten" -> R.string.place_school
    "hospital", "clinic", "doctors", "pharmacy" -> R.string.place_health
    "mall", "supermarket", "marketplace" -> R.string.place_shop
    "museum", "attraction", "artwork", "castle", "monument", "memorial" -> R.string.place_sight
    "park", "garden", "pitch", "sports_centre", "stadium", "swimming_pool" -> R.string.place_sport
    "restaurant", "cafe", "bar", "fast_food", "pub" -> R.string.place_food
    "suburb", "neighbourhood", "quarter", "city_block" -> R.string.place_area
    "station", "halt" -> R.string.place_station
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPane(
    state: TransitState,
    contentPadding: PaddingValues,
    onPick: (StopField) -> Unit,
    onSwap: () -> Unit,
    onTime: (LocalTime?) -> Unit,
    onArriveBy: (Boolean) -> Unit,
    onAddTile: () -> Unit,
    onDeleteTile: (RouteTileEntity) -> Unit,
    onRefresh: () -> Unit,
) {
    var clock by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            EclipseCard(padding = PaddingValues(start = 16.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        StopRow(R.string.transit_from, state.from) { onPick(StopField.FROM) }
                        HorizontalDivider(color = Eclipse.colors.border.copy(alpha = 0.3f))
                        StopRow(R.string.transit_to, state.to) { onPick(StopField.TO) }
                    }
                    IconButton(onClick = onSwap, enabled = state.from != null || state.to != null) {
                        Icon(painterResource(R.drawable.ic_swap_vert), stringResource(R.string.transit_swap), tint = Eclipse.colors.accentText)
                    }
                }
            }
        }
        item {
            ChoiceChips(
                listOf(false to stringResource(R.string.transit_depart_at), true to stringResource(R.string.transit_arrive_by)),
                state.arriveBy,
                { arriveBy ->
                    onArriveBy(arriveBy)
                    // bez godziny pytanie „chcę być na miejscu” nie ma sensu, więc od razu pokazujemy zegar
                    if (arriveBy && state.time == null) clock = true
                },
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { clock = true }) {
                    Text(
                        when {
                            state.time == null && !state.arriveBy -> stringResource(R.string.transit_now)
                            state.time == null -> stringResource(R.string.transit_arrive_pick)
                            state.arriveBy -> stringResource(R.string.transit_arrive_at, clockText(state.time))
                            else -> stringResource(R.string.transit_at, clockText(state.time))
                        },
                        color = Eclipse.colors.accentText,
                    )
                }
                if (state.time != null) {
                    TextButton(onClick = { onTime(null) }) {
                        Text(stringResource(R.string.transit_back_to_now), color = Eclipse.colors.textSecondary)
                    }
                }
            }
        }
        if (state.searching) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Eclipse.colors.accent) }
        items(state.journeys) { JourneyCard(it) }
        if (state.journeys.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.transit_schedule_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = Eclipse.colors.textSecondary,
                )
            }
        }
        if (state.searched && !state.searching && state.journeys.isEmpty()) {
            item { EmptyState(stringResource(R.string.transit_no_journeys)) }
        }
        if (state.from != null && state.to != null) {
            item {
                val exists = state.tiles.any { it.fromName == state.from.name && it.toName == state.to.name }
                Button(
                    onClick = onAddTile,
                    enabled = !exists,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Eclipse.colors.accent,
                        contentColor = Eclipse.colors.onAccent,
                        disabledContainerColor = Eclipse.colors.textSecondary.copy(alpha = 0.18f),
                        disabledContentColor = Eclipse.colors.textSecondary,
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                    Text(
                        stringResource(if (exists) R.string.transit_tile_exists else R.string.transit_add_tile),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (state.tiles.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.transit_tiles_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = Eclipse.colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.tiles, key = { "tile-" + it.id }) { tile ->
                EclipseCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val labels = state.saved.associate { it.stopName to it.label }
                        Text(
                            (labels[tile.fromName] ?: tile.fromName) + " → " + (labels[tile.toName] ?: tile.toName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Eclipse.colors.text,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        )
                        IconButton(onClick = { onDeleteTile(tile) }) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.transit_delete_tile),
                                Modifier.size(18.dp),
                                tint = Eclipse.colors.textSecondary,
                            )
                        }
                    }
                }
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
    if (clock) {
        val current = state.time ?: LocalTime.now(WARSAW)
        val picker = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { clock = false },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(picker.hour, picker.minute))
                    clock = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { clock = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(picker) },
        )
    }
}

@Composable
private fun StopRow(label: Int, stop: TransitNode?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Eclipse.colors.textSecondary)
        Text(
            stop?.name ?: stringResource(R.string.transit_pick_stop),
            style = MaterialTheme.typography.bodyLarge,
            color = if (stop == null) Eclipse.colors.textSecondary else Eclipse.colors.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun JourneyCard(item: TransitJourney) {
    val journey = item.journey
    EclipseCard(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clockText(journey.departure) + " – " + clockText(journey.arrival),
                style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                color = Eclipse.colors.text,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.transit_minutes, journey.minutes),
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = Eclipse.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val first = journey.legs.first()
            Tag(first.line, lineColor(first.tram))
            if (journey.legs.size == 1) {
                Text(first.head, style = MaterialTheme.typography.bodySmall, color = Eclipse.colors.textSecondary, maxLines = 1)
            } else {
                Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(14.dp), tint = Eclipse.colors.textSecondary)
                Tag(journey.legs[1].line, lineColor(journey.legs[1].tram))
            }
        }
        journey.transferMinutes?.let { wait ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (journey.changesPlatform) R.string.transit_transfer_walk else R.string.transit_transfer_same,
                    item.transferName.orEmpty(),
                    wait,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Eclipse.colors.textSecondary,
            )
        }
    }
}

/** Tramwaj i autobus różnymi kolorami (SPEC 17.3). */
internal fun lineColor(tram: Boolean): Color = if (tram) Color(0xFF3FBF8F) else Color(0xFF5B8DEF)

/** Minuty od północy na godzinę; kurs po północy ma w rozkładzie wartość powyżej 1440. */
private fun clockText(minutes: Int): String {
    val value = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(value / 60, value % 60)
}

private fun clockText(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/** „20260930” na „30.09.2026”; gdy rozkład nie podaje wersji, zostaje myślnik. */
private fun formatFeedVersion(version: String): String =
    if (version.length == 8 && version.all { it.isDigit() }) {
        version.substring(6) + "." + version.substring(4, 6) + "." + version.substring(0, 4)
    } else {
        version.ifBlank { "—" }
    }
