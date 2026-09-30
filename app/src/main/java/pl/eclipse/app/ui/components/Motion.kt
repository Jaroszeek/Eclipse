package pl.eclipse.app.ui.components

import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import pl.eclipse.app.ui.theme.Eclipse
import kotlin.math.hypot

// Ruch „Zaćmienie” (SPEC 11.8): animacje tylko jako odpowiedź na działanie; gdy system je wyłączył — bez nich.

private const val SHORT = 300
private const val BACK = 350
private const val REVEAL = 600
private val BLUR = 12.dp
private val CARD_CORNER = 28.dp
private val GLOW_WIDTH = 32.dp

object EclipseMotion {
    /** Menu i widoki kalendarza: stary ekran szybko znika z rozmyciem, nowy wyostrza się z lekkim przybliżeniem. */
    val throughEnter: EnterTransition = fadeIn(tween(220, delayMillis = 80)) + scaleIn(tween(SHORT, easing = LinearOutSlowInEasing), initialScale = 0.96f)
    val throughExit: ExitTransition = fadeOut(tween(120))

    /** Wejście głębiej (przedmiot, ustawienia): nowy ekran wsuwa się z prawej, poprzedni odsuwa się w lewo. */
    val forwardEnter: EnterTransition = slideInHorizontally(tween(SHORT, easing = FastOutSlowInEasing)) { it / 10 } + fadeIn(tween(210, delayMillis = 90))
    val forwardExit: ExitTransition = slideOutHorizontally(tween(SHORT, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(90))

    /** Powrót: pod spodem pojawia się poprzedni ekran, a bieżący maleje jak karta — przy geście w stronę palca. */
    val backEnter: EnterTransition = fadeIn(tween(BACK))

    fun backExit(swipeEdge: Int? = null): ExitTransition {
        val origin = when (swipeEdge) {
            BackEventCompat.EDGE_LEFT -> TransformOrigin(1f, 0.5f)
            BackEventCompat.EDGE_RIGHT -> TransformOrigin(0f, 0.5f)
            else -> TransformOrigin.Center
        }
        // Przy geście „wstecz” pierwsza część ruchu to samo zmniejszanie; ekran znika dopiero po puszczeniu.
        return scaleOut(tween(BACK, easing = FastOutSlowInEasing), targetScale = 0.9f, transformOrigin = origin) +
            fadeOut(tween(150, delayMillis = BACK - 150))
    }

    fun through(reduceMotion: Boolean): ContentTransform =
        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None else throughEnter togetherWith throughExit

    /** Logowanie i otwarcie aplikacji: nowy ekran odsłania koło rosnące od tarczy ([RevealScreen]), stary cofa się pod spodem. */
    fun reveal(reduceMotion: Boolean): ContentTransform =
        if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
        else EnterTransition.None togetherWith (scaleOut(tween(REVEAL), targetScale = 0.94f) + fadeOut(tween(REVEAL), targetAlpha = 0.3f))
}

/** 0 — treść w pełni widoczna, 1 — na początku wejścia albo na końcu wyjścia. */
@Composable
private fun AnimatedVisibilityScope.movement() =
    transition.animateFloat(transitionSpec = { tween(SHORT) }, label = "movement") { if (it == EnterExitState.Visible) 0f else 1f }

private fun GraphicsLayerScope.applyBlur(amount: Float) {
    val radius = BLUR.toPx() * amount
    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius) else null
}

/**
 * Ekran w nawigacji. Ma własne tło, żeby przy powrocie malejący ekran wyglądał jak karta nad poprzednim,
 * a w ruchu zaokrąglone rogi. Ekrany z menu ([blur]) rozmywają się przy wejściu i wyjściu.
 */
@Composable
fun AnimatedVisibilityScope.NavScreen(blur: Boolean, content: @Composable () -> Unit) {
    val moving = movement()
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val m = moving.value
            clip = m > 0f
            shape = if (clip) RoundedCornerShape(CARD_CORNER * m) else RectangleShape
            if (blur) applyBlur(m) else renderEffect = null
        },
    ) {
        EclipseBackground()
        content()
    }
}

/** Treść, która przy zmianie rozmywa się i wyostrza (widoki kalendarza). */
@Composable
fun AnimatedVisibilityScope.BlurWhileMoving(content: @Composable () -> Unit) {
    val moving = movement()
    Box(Modifier.fillMaxSize().graphicsLayer { applyBlur(moving.value) }) { content() }
}

/**
 * Nowy ekran odsłania koło rosnące od [origin] (środek tarczy poprzedniego ekranu; bez tarczy — środek ekranu)
 * ze świecącą krawędzią, jak korona wychodząca zza księżyca.
 */
@Composable
fun AnimatedVisibilityScope.RevealScreen(enabled: Boolean, origin: () -> Offset?, content: @Composable () -> Unit) {
    val progress = transition.animateFloat(transitionSpec = { tween(REVEAL, easing = FastOutSlowInEasing) }, label = "reveal") {
        if (it == EnterExitState.PreEnter && enabled) 0f else 1f
    }
    val glow = Eclipse.colors.accent
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                clip = p < 1f
                shape = if (clip) {
                    val center = origin() ?: size.center
                    RevealShape(center, revealRadius(center, size) * p)
                } else RectangleShape
            }
            .drawWithContent {
                drawContent()
                val p = progress.value
                if (p > 0f && p < 1f) {
                    val center = origin() ?: size.center
                    val radius = revealRadius(center, size) * p
                    val inner = ((radius - GLOW_WIDTH.toPx()) / radius).coerceIn(0f, 1f)
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color.Transparent, inner to Color.Transparent, 1f to glow.copy(alpha = 0.6f * (1f - p)),
                            center = center, radius = radius,
                        ),
                        radius, center,
                    )
                }
            },
    ) { content() }
}

/** Odległość od środka koła do najdalszego rogu — wtedy koło zakrywa cały ekran. */
private fun revealRadius(center: Offset, size: Size): Float = maxOf(
    hypot(center.x, center.y),
    hypot(size.width - center.x, center.y),
    hypot(center.x, size.height - center.y),
    hypot(size.width - center.x, size.height - center.y),
)

/** Koło jako zaokrąglony prostokąt — takie przycinanie Android robi sprzętowo na każdej wersji. */
private class RevealShape(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(Rect(center, radius), CornerRadius(radius)))
}
