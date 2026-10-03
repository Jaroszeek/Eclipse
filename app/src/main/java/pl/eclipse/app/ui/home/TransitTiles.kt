package pl.eclipse.app.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.TransitJourney
import pl.eclipse.app.ui.WARSAW
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.transit.lineColor
import java.time.LocalDate
import java.time.LocalTime

// SPEC 17.5. Panele dojazdu na Pulpicie: stała trasa i najbliższe odjazdy.
// Połączenia liczymy raz przy otwarciu Pulpitu, a potem już tylko odliczamy minuty — przeszukanie
// rozkładu jest zbyt kosztowne, żeby powtarzać je co minutę.

/** Jeden panel: podpis trasy i najbliższe połączenia. */
data class RouteTileState(val id: Long, val from: String, val to: String, val journeys: List<TransitJourney>, val known: Boolean)

class TransitTilesViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.container
    private val _state = MutableStateFlow<List<RouteTileState>>(emptyList())
    val state: StateFlow<List<RouteTileState>> = _state.asStateFlow()

    init {
        viewModelScope.launch { container.database.user().routeTiles().collect { refresh() } }
    }

    /** Przelicza wszystkie panele od bieżącej godziny. */
    fun refresh() {
        viewModelScope.launch {
            val tiles = container.database.user().routeTilesOnce()
            // panel podpisujemy nazwą zapisanego miejsca („Dom → Szkoła”), a nie nazwą przystanku
            val labels = container.database.user().savedPlacesOnce().associate { it.stopName to it.label }
            val now = LocalTime.now(WARSAW)
            val minute = now.hour * 60 + now.minute
            val today = LocalDate.now(WARSAW)
            _state.value = tiles.map { tile ->
                val from = container.transit.nodeByName(tile.fromName)
                val to = container.transit.nodeByName(tile.toName)
                val found = if (from == null || to == null) {
                    emptyList()
                } else {
                    container.transit.journeys(from.id, to.id, today, minute).take(2)
                }
                RouteTileState(
                    tile.id,
                    labels[tile.fromName] ?: tile.fromName,
                    labels[tile.toName] ?: tile.toName,
                    found,
                    from != null && to != null,
                )
            }
        }
    }
}

/** Panele dojazdu na Pulpicie (SPEC 17.5). */
@Composable
fun TransitTiles(tiles: List<RouteTileState>, onOpen: () -> Unit, onTick: () -> Unit) {
    // minuty odliczamy w interfejsie; gdy pierwszy odjazd minie, prosimy o przeliczenie na nowo
    var now by remember { mutableStateOf(nowMinutes()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val minute = nowMinutes()
            if (minute != now) now = minute
        }
    }
    LaunchedEffect(now, tiles) {
        val first = tiles.flatMap { it.journeys }.minOfOrNull { it.journey.departure }
        if (first != null && first < now) onTick()
    }
    // panele są jednym kafelkiem Pulpitu, więc odstęp między nimi musimy dać sami
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.forEach { tile -> RouteTile(tile, now, onOpen) }
    }
}

@Composable
private fun RouteTile(tile: RouteTileState, now: Int, onOpen: () -> Unit) {
    val c = Eclipse.colors
    EclipseCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_tram), null, Modifier.size(18.dp), tint = c.accentText)
            Text(
                tile.from + " → " + tile.to,
                style = MaterialTheme.typography.titleSmall,
                color = c.text,
                maxLines = 2,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        when {
            !tile.known -> Note(stringResource(R.string.home_transit_no_data))
            tile.journeys.isEmpty() -> Note(stringResource(R.string.home_transit_none))
            else -> tile.journeys.forEach { item ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val away = item.journey.departure - now
                    Text(
                        if (away <= 0) stringResource(R.string.home_transit_now) else stringResource(R.string.home_transit_in, away),
                        style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                        color = c.accentText,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            clock(item.journey.departure) + " – " + clock(item.journey.arrival),
                            style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                            color = c.text,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            item.journey.legs.forEach { leg -> Tag(leg.line, lineColor(leg.tram), compact = true) }
                            item.transferName?.let {
                                Text(
                                    stringResource(R.string.home_transit_change, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.transit_minutes, item.journey.minutes),
                        style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Eclipse.colors.textSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun nowMinutes(): Int = LocalTime.now(WARSAW).let { it.hour * 60 + it.minute }

private fun clock(minutes: Int): String {
    val value = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(value / 60, value % 60)
}
