package pl.eclipse.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pl.eclipse.app.R
import pl.eclipse.app.ui.theme.Eclipse

/** Od którego miejsca szerokości zaczyna się gaśnięcie prawej krawędzi. */
private const val FADE_FROM = 0.87f

/**
 * Wybór jednej opcji jako chipy w jednym wierszu — zamiast przycisków segmentowych, które przy wąskim ekranie
 * (360 dp) i dużej czcionce ucinały napisy. Gdy opcje się nie mieszczą, wiersz przewija się w bok; wcześniej
 * zawijał się i ostatni chip zostawał sam pod spodem. Wybrana opcja ma też znacznik ✓, więc kolor nie jest
 * jedynym nośnikiem informacji (SPEC 11.5). [badges] to liczby nieprzeczytanych przy opcji.
 */
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<T, Int> = emptyMap(),
) {
    val state = rememberLazyListState()
    val index = options.indexOfFirst { it.first == selected }
    // Wybrany chip ma być widoczny, ale nie przesuwamy wiersza, gdy i tak go widać.
    LaunchedEffect(index) {
        if (index < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (shown == null || shown.offset < 0 || shown.offset + shown.size > info.viewportEndOffset) {
            state.animateScrollToItem(index)
        }
    }
    // Gdy opcje się nie mieszczą, prawa krawędź gaśnie — widać, że dalej coś jest.
    val more by remember { derivedStateOf { state.canScrollForward } }
    LazyRow(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (more) {
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Black, FADE_FROM to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(options) { _, option ->
            val (value, label) = option
            val isSelected = value == selected
            val count = badges[value] ?: 0
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1) },
                leadingIcon = if (isSelected) ({ Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp)) }) else null,
                trailingIcon = if (count > 0) ({ CountBadge(count, color = Eclipse.colors.accent) }) else null,
            )
        }
    }
}
