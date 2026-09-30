package pl.eclipse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pl.eclipse.app.R

/**
 * Wybór jednej opcji jako chipy, które przenoszą się do nowej linii — zamiast przycisków segmentowych,
 * które przy wąskim ekranie (360 dp) i dużej czcionce ucinały napisy. Wybrana opcja ma też znacznik ✓,
 * więc kolor nie jest jedynym nośnikiem informacji (SPEC 11.5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                leadingIcon = if (isSelected) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}
