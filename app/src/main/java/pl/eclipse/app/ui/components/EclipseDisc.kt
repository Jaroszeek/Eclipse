package pl.eclipse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers

/**
 * Tarcza zaćmienia (SPEC 11.7): ciemna tarcza z koroną w kolorze akcentu i pierścieniem postępu.
 * [coverage] 0 = pełna korona (brak sprawdzianów), 1 = tarcza całkiem zakrywa koronę (sprawdzian teraz).
 * [animateIn] — jedyna animacja wejścia w aplikacji: korona rozjaśnia się przy otwarciu Pulpitu (SPEC 11.8).
 */
@Composable
fun EclipseDisc(
    coverage: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    showProgress: Boolean = true,
    animateIn: Boolean = false,
    accent: Color = Eclipse.colors.accent,
    center: @Composable () -> Unit = {},
) {
    val reduceMotion = Eclipse.reduceMotion
    val brightness = remember { Animatable(if (animateIn && !reduceMotion) 0f else 1f) }
    LaunchedEffect(animateIn) {
        if (animateIn && !reduceMotion) brightness.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val track = Eclipse.colors.textSecondary.copy(alpha = 0.18f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension * 0.30f
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val visible = (1f - coverage.coerceIn(0f, 1f)) * brightness.value
            // korona: poświata i jasny pierścień wokół tarczy, słabnące w miarę zakrywania
            val glowRadius = r * (1.18f + 0.55f * visible)
            drawCircle(
                Brush.radialGradient(
                    0f to accent.copy(alpha = 0.95f * (0.25f + 0.75f * visible)),
                    (r / glowRadius) to accent.copy(alpha = 0.75f * (0.2f + 0.8f * visible)),
                    1f to Color.Transparent,
                    center = c, radius = glowRadius,
                ),
                glowRadius, c,
            )
            drawCircle(accent.copy(alpha = 0.35f + 0.65f * visible), r * 1.015f, c)
            // tarcza (księżyc): zawsze ciemna, z lekkim światłem na krawędzi
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF1B2150), Palette.Night1), center = c + Offset(-r * 0.3f, -r * 0.35f), radius = r * 1.3f),
                r, c,
            )
            if (showProgress) {
                val ringR = r * 1.62f
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val topLeft = Offset(c.x - ringR, c.y - ringR)
                drawArc(track, 0f, 360f, false, topLeft, Size(ringR * 2, ringR * 2), style = stroke)
                if (coverage > 0f) {
                    drawArc(accent, -90f, 360f * coverage.coerceIn(0f, 1f), false, topLeft, Size(ringR * 2, ringR * 2), style = stroke)
                }
            }
        }
        center()
    }
}

/** Środek tarczy: duże odliczanie i podpis (np. przedmiot) — zawsze jasny tekst, bo tarcza jest ciemna. */
@Composable
fun DiscLabel(value: String, caption: String, detail: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.displayMedium.merge(TabularNumbers),
            color = Color(0xFFE8EAF6),
            textAlign = TextAlign.Center,
        )
        Text(caption, style = MaterialTheme.typography.labelMedium, color = Color(0xFFA3A9CC), textAlign = TextAlign.Center)
        detail?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF6), textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
