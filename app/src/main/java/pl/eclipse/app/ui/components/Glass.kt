package pl.eclipse.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import pl.eclipse.app.ui.theme.Eclipse

/** Stan szkła (Haze): łączy źródło — tło i treść ekranu — z panelami, które je rozmywają. */
@Stable
class GlassState(val haze: HazeState)

@Composable
fun rememberGlassState(): GlassState {
    val haze = rememberHazeState()
    return remember(haze) { GlassState(haze) }
}

/** Oznacza treść, która ma być widoczna (rozmyta) pod szklanymi panelami. */
fun Modifier.glassSource(state: GlassState): Modifier = hazeSource(state.haze)

/**
 * Duży szklany panel (pasek boczny, górny pasek, arkusze): rozmycie 20 dp + półprzezroczysty tint + szum + ramka 1 dp (SPEC 11.1).
 * Przy „Mniej przezroczystości” — matowa, mocniej kryjąca powierzchnia bez rozmycia. Rozmycie tylko na dużych panelach,
 * nigdy na blokach kalendarza.
 */
@Composable
fun Modifier.glass(state: GlassState, shape: Shape = RectangleShape, bordered: Boolean = true): Modifier {
    val c = Eclipse.colors
    val style = remember(c) {
        HazeBlurStyle {
            blurEnabled(!c.lessTransparency)
            blurRadius(20.dp)
            noiseFactor(0.06f)
            backgroundColor(c.backgroundBottom)
            colorEffects(listOf(HazeColorEffect.tint(c.panel)))
            fallbackColorEffect(HazeColorEffect.tint(c.panel.compositeOver(c.backgroundBottom)))
        }
    }
    return this
        .clip(shape)
        .hazeBlur(input = HazeInput.Sources(state.haze), style = style)
        .then(if (bordered) Modifier.border(1.dp, c.border, shape) else Modifier)
}
