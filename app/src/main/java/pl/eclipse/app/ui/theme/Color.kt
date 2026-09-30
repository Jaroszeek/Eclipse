package pl.eclipse.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import pl.eclipse.core.calc.WarningLevel
import pl.eclipse.core.model.EventType

// Tokeny kolorów (SPEC 11). Neutralne tła są stałe (granat), akcent pojawia się tylko tam, gdzie SPEC każe.

object Palette {
    val Night1 = Color(0xFF0D1230)
    val Night2 = Color(0xFF1B2150)
    val Dawn1 = Color(0xFFEEF1FA)
    val Dawn2 = Color(0xFFF8F9FD)
    val Ink = Color(0xFF141A3A)

    val Test = Color(0xFFEF4444)
    val Quiz = Color(0xFFF97316)
    val Homework = Color(0xFF3B82F6)
    val SchoolEvent = Color(0xFF14B8A6)
    val DayOff = Color(0xFF22C55E)
    val Custom = Color(0xFFA78BFA)

    val Critical = Color(0xFFEF4444)
    val Warning = Color(0xFFF97316)
    val Watch = Color(0xFFEAB308)

    /** Presety akcentu (SPEC 11.2). */
    val Accents = listOf(
        "Korona" to Color(0xFFE9B949),
        "Fiolet" to Color(0xFF8B5CF6),
        "Błękit" to Color(0xFF3B82F6),
        "Morski" to Color(0xFF14B8A6),
        "Róż" to Color(0xFFEC4899),
        "Zieleń" to Color(0xFF22C55E),
    )

    /** 14 barw przedmiotów — żadna nie jest czerwienią sprawdzianu (SPEC 11.4). */
    val Subjects = listOf(
        0xFF5B8DEF, 0xFFE879A6, 0xFF3FBF8F, 0xFFD9A441, 0xFF36B5D1, 0xFF8B7CF6, 0xFFE07A5F,
        0xFF9CCB5A, 0xFFC06FD6, 0xFF4F9D9A, 0xFFB08D6E, 0xFF6FA8DC, 0xFFD46A8C, 0xFF7E8FB0,
    ).map(::Color)
}

fun eventColor(type: EventType): Color = when (type) {
    EventType.TEST -> Palette.Test
    EventType.QUIZ -> Palette.Quiz
    EventType.TRIP, EventType.OTHER -> Palette.SchoolEvent
    EventType.DAY_OFF -> Palette.DayOff
}

fun levelColor(level: WarningLevel): Color = when (level) {
    WarningLevel.CRITICAL -> Palette.Critical
    WarningLevel.WARNING -> Palette.Warning
    WarningLevel.WATCH -> Palette.Watch
}

/** Kolor oceny według progu (SPEC 6.4): 6 i 5 zielenie, 4 morski, 3 żółty, 2 pomarańczowy, 1 czerwony. */
fun gradeColor(grade: Int): Color = when (grade) {
    6 -> Color(0xFF22C55E)
    5 -> Color(0xFF4ADE80)
    4 -> Color(0xFF14B8A6)
    3 -> Color(0xFFEAB308)
    2 -> Color(0xFFF97316)
    else -> Color(0xFFEF4444)
}

@Immutable
data class EclipseColors(
    val isDark: Boolean,
    val lessTransparency: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    /** Tint szkła dużych paneli (z rozmyciem) albo pełny kolor przy „Mniej przezroczystości”. */
    val panel: Color,
    val card: Color,
    val dialog: Color,
    val border: Color,
    val cardBorder: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    /** Akcent na tekście i ikonach — w jasnym motywie przyciemniony do kontrastu ≥ 4,5:1. */
    val accentText: Color,
    val onAccent: Color,
    val glow: Float,
    val lessonTint: Color,
) {
    /** Kolor czytelny na danym tle koloru (tekst na kafelku oceny itp.). */
    fun textOn(color: Color): Color = if (contrast(Palette.Ink, color) >= contrast(Color.White, color)) Palette.Ink else Color.White

    /** Kolor typu lub oceny jako tekst na tle aplikacji — dobrany do kontrastu ≥ 4,5:1 (rozjaśniony nocą, przyciemniony za dnia). */
    fun readable(color: Color): Color = if (isDark) lightenForContrast(color, backgroundBottom) else darkenForContrast(color, backgroundBottom)
}

fun eclipseColors(dark: Boolean, accent: Color, lessTransparency: Boolean): EclipseColors {
    val bottom = if (dark) Palette.Night2 else Palette.Dawn2
    val accentText = if (dark) lightenForContrast(accent, Palette.Night1) else darkenForContrast(accent, bottom)
    return if (dark) {
        EclipseColors(
            isDark = true,
            lessTransparency = lessTransparency,
            backgroundTop = Palette.Night1,
            backgroundBottom = Palette.Night2,
            panel = if (lessTransparency) Color(0xFF1A2048) else Color.White.copy(alpha = 0.08f),
            card = if (lessTransparency) Color(0xFF161C40) else Color.White.copy(alpha = 0.05f),
            dialog = Color(0xFF1E2554).copy(alpha = 0.97f),
            border = Color.White.copy(alpha = 0.12f),
            cardBorder = Color.White.copy(alpha = if (lessTransparency) 0.10f else 0.08f),
            text = Color(0xFFE8EAF6),
            textSecondary = Color(0xFFA3A9CC),
            accent = accent,
            accentText = accentText,
            onAccent = onColor(accent),
            glow = if (lessTransparency) 0.18f else 0.30f,
            lessonTint = Color.White.copy(alpha = 0.06f),
        )
    } else {
        EclipseColors(
            isDark = false,
            lessTransparency = lessTransparency,
            backgroundTop = Palette.Dawn1,
            backgroundBottom = Palette.Dawn2,
            panel = if (lessTransparency) Color.White else Color.White.copy(alpha = 0.62f),
            card = if (lessTransparency) Color(0xFFF3F5FB) else Color.White.copy(alpha = 0.72f),
            dialog = Color.White.copy(alpha = 0.98f),
            border = Palette.Ink.copy(alpha = 0.08f),
            cardBorder = Palette.Ink.copy(alpha = 0.06f),
            text = Palette.Ink,
            textSecondary = Color(0xFF50577A),
            accent = accent,
            accentText = accentText,
            onAccent = onColor(accent),
            glow = if (lessTransparency) 0.10f else 0.16f,
            lessonTint = Palette.Ink.copy(alpha = 0.05f),
        )
    }
}

/** Współczynnik kontrastu WCAG między dwoma kolorami. */
fun contrast(a: Color, b: Color): Float {
    val la = a.compositeOver(Color.White).luminance() + 0.05f
    val lb = b.compositeOver(Color.White).luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

private fun onColor(background: Color) = if (contrast(Palette.Ink, background) >= contrast(Color.White, background)) Palette.Ink else Color.White

private fun darkenForContrast(color: Color, background: Color, target: Float = 4.5f): Color {
    var result = color
    var step = 0
    while (contrast(result, background) < target && step < 20) {
        result = lerp(color, Color.Black, ++step * 0.05f)
    }
    return result
}

private fun lightenForContrast(color: Color, background: Color, target: Float = 4.5f): Color {
    var result = color
    var step = 0
    while (contrast(result, background) < target && step < 20) {
        result = lerp(color, Color.White, ++step * 0.05f)
    }
    return result
}

/** Kolor przedmiotu: przypisany deterministycznie po nazwie (kolejność alfabetyczna), nadpisywalny w ustawieniach. */
fun subjectColors(names: Map<String, String>, overrides: Map<String, Int>): Map<String, Color> =
    names.entries.sortedBy { it.value.lowercase() }.mapIndexed { index, (key, _) ->
        key to (overrides[key]?.let(::Color) ?: Palette.Subjects[index % Palette.Subjects.size])
    }.toMap()
