package pl.eclipse.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Wspólne formatowanie po polsku (pl-PL): „78,4%”, „wt 14.10”.

private val POLISH: Locale = Locale.forLanguageTag("pl-PL")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.MM", POLISH)

/** Jedno miejsce po przecinku; pełne wartości bez przecinka („75%”). */
fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) "${value.toInt()}%" else String.format(POLISH, "%.1f%%", value)

fun formatDate(date: LocalDate): String = date.format(SHORT_DATE)

/** Punkty bez zbędnego zera po przecinku („30”, „7,5”). */
fun formatPoints(value: Double): String =
    value.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')
