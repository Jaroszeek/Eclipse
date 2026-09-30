package pl.eclipse.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import pl.eclipse.app.R

// Typografia (SPEC 11.6): Unbounded tylko dla logo, tytułów ekranów i dużych liczb; reszta Manrope.
// Skala o stałym stosunku 1,25 od 14 sp: 11,2 · 14 · 17,5 · 21,9 · 27,3 · 34,2 · 42,7.

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

val Unbounded = FontFamily(
    Font(R.font.unbounded_medium, FontWeight.Medium),
    Font(R.font.unbounded_bold, FontWeight.Bold),
)

private fun manrope(size: Float, weight: FontWeight, line: Float = 1.4f) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = (size * line).sp)

private fun unbounded(size: Float, weight: FontWeight) =
    TextStyle(fontFamily = Unbounded, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.2f).sp, letterSpacing = (-0.01).em)

val EclipseTypography = Typography(
    displayLarge = unbounded(42.7f, FontWeight.Bold),
    displayMedium = unbounded(34.2f, FontWeight.Bold),
    displaySmall = unbounded(27.3f, FontWeight.Medium),
    headlineLarge = unbounded(21.9f, FontWeight.Medium),
    headlineMedium = manrope(21.9f, FontWeight.Bold, 1.3f),
    headlineSmall = manrope(17.5f, FontWeight.Bold, 1.3f),
    titleLarge = manrope(17.5f, FontWeight.SemiBold, 1.3f),
    titleMedium = manrope(14f, FontWeight.Bold),
    titleSmall = manrope(14f, FontWeight.SemiBold),
    bodyLarge = manrope(14f, FontWeight.Normal, 1.5f),
    bodyMedium = manrope(14f, FontWeight.Normal, 1.45f),
    bodySmall = manrope(11.2f, FontWeight.Medium, 1.45f),
    labelLarge = manrope(14f, FontWeight.SemiBold),
    labelMedium = manrope(11.2f, FontWeight.SemiBold),
    labelSmall = manrope(11.2f, FontWeight.Medium),
)

/** Cyfry o stałej szerokości do tabel, procentów i statystyk. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
