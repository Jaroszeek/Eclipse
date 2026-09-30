package pl.eclipse.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import pl.eclipse.app.data.ThemeMode

val LocalEclipseColors = staticCompositionLocalOf { eclipseColors(true, Palette.Accents.first().second, false) }

/** Czy w systemie wyłączono animacje — wtedy aplikacja też ich nie pokazuje (SPEC 11.8). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Eclipse {
    val colors: EclipseColors
        @Composable @ReadOnlyComposable get() = LocalEclipseColors.current
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun EclipseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: Color = Palette.Accents.first().second,
    lessTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val colors = remember(dark, accent, lessTransparency) { eclipseColors(dark, accent, lessTransparency) }
    val resolver = LocalContext.current.contentResolver
    val reduceMotion = remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    CompositionLocalProvider(LocalEclipseColors provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = colorScheme(colors), typography = EclipseTypography, content = content)
    }
}

/**
 * Schemat Material 3 z akcentu i własnych tokenów (SPEC 11.2). Bez dynamic color i bez barwienia powierzchni
 * akcentem (`surfaceTint` przezroczysty) — tła zostają granatowe, akcent tylko na kontrolkach.
 */
private fun colorScheme(c: EclipseColors): ColorScheme {
    val surface = c.panel.compositeOver(c.backgroundBottom)
    val raised = c.dialog.compositeOver(c.backgroundBottom)
    val container = c.accent.copy(alpha = 0.22f).compositeOver(surface)
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accentText,
        onPrimary = if (c.isDark) c.onAccent else Color.White,
        primaryContainer = container,
        onPrimaryContainer = c.text,
        secondary = c.accentText,
        onSecondary = if (c.isDark) c.onAccent else Color.White,
        secondaryContainer = container,
        onSecondaryContainer = c.text,
        tertiary = c.accentText,
        background = c.backgroundTop,
        onBackground = c.text,
        surface = surface,
        onSurface = c.text,
        surfaceVariant = c.card.compositeOver(c.backgroundBottom),
        onSurfaceVariant = c.textSecondary,
        surfaceTint = Color.Transparent,
        surfaceBright = raised,
        surfaceDim = c.backgroundTop,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
        inverseSurface = c.text,
        inverseOnSurface = c.backgroundTop,
        outline = c.textSecondary.copy(alpha = 0.6f),
        outlineVariant = c.border,
        error = if (c.isDark) Color(0xFFF87171) else Color(0xFFDC2626),
        scrim = Color.Black.copy(alpha = 0.55f),
    )
}
