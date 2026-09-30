package pl.eclipse.core.recon

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pl.eclipse.core.source.librus.LibrusClient
import pl.eclipse.core.source.librus.LibrusException
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Rekonesans (SPEC 4.2): loguje się i opisuje strukturę każdego zasobu Librusa.
 * Raport zawiera nazwy pól, typy i liczby — treść tekstów jest zamaskowana,
 * a wartości pokazujemy tylko dla pól bez danych osobowych (symbole ocen, kategorie, dzwonki).
 */
object Recon {
    fun run(login: String, password: String, today: LocalDate = LocalDate.now(ZoneId.of("Europe/Warsaw"))): String =
        buildString {
            appendLine("Rekonesans Librusa, $today")
            val client = LibrusClient()
            try {
                client.login(login, password)
            } catch (e: LibrusException) {
                appendLine("Logowanie: nie udało się. ${e.message}")
                return@buildString
            } catch (e: IOException) {
                appendLine("Logowanie: brak połączenia z Librusem (${e.javaClass.simpleName}).")
                return@buildString
            }
            appendLine("Logowanie: OK")

            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            for (resource in GATEWAY_RESOURCES + "Timetables?weekStart=$monday") {
                call(resource) { client.gateway(resource) }
            }
            call("Wiadomości: wejście przez Synergię") { client.fetch("https://synergia.librus.pl/wiadomosci3") }
            call("Wiadomości: inbox/messages") { client.fetch("https://wiadomosci.librus.pl/api/inbox/messages?page=1&limit=10") }
        }

    private fun StringBuilder.call(name: String, request: () -> Pair<Int, String>) {
        Thread.sleep(PAUSE_MS) // zapytania po kolei, z przerwą — szanujemy serwery Librusa
        val (code, body) = try {
            request()
        } catch (e: IOException) {
            appendLine("\n## $name: błąd połączenia (${e.javaClass.simpleName})")
            return
        }
        append(describe(name, code, body))
    }
}

internal fun describe(resource: String, code: Int, body: String): String = buildString {
    val name = resource.substringBefore('?')
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull()
    if (json !is JsonObject) {
        appendLine("\n## $resource: HTTP $code, odpowiedź nie jest JSON-em (${body.length} znaków)")
        return@buildString
    }
    val main = json.filterKeys { it !in SKIPPED_KEYS }
    appendLine("\n## $resource: HTTP $code; " + main.entries.joinToString { (k, v) -> "$k: ${sizeOf(v)}" })
    if (code !in 200..299) {
        // Komunikaty błędów Librusa są ogólne (bez danych ucznia), więc je pokazujemy.
        listOf("Code", "Message").forEach { k ->
            (json[k] as? JsonPrimitive)?.let { appendLine("$k = ${it.content.take(120)}") }
        }
        return@buildString
    }
    val stats = linkedMapOf<String, FieldStats>()
    main.forEach { (k, v) -> collect(name, v, k, k, stats) }
    stats.forEach { (path, s) -> appendLine(s.line(path)) }
    main.values.firstNotNullOfOrNull { sampleOf(it) }?.let {
        appendLine("Przykład: " + mask(name, it, "").toString().take(1500))
    }
}

private class FieldStats {
    val types = sortedSetOf<String>()
    var present = 0
    var nonNull = 0
    val values = mutableMapOf<String, Int>()
    var withPercent = 0
    var withPoints = 0

    fun line(path: String) = buildString {
        append("$path: ${types.joinToString("/")} ($nonNull/$present)")
        if (values.isNotEmpty()) {
            val top = values.entries.sortedByDescending { it.value }.take(MAX_VALUES)
            append(" — " + top.joinToString { "${it.key} ×${it.value}" })
            if (values.size > MAX_VALUES) append(", … (${values.size} różnych)")
        }
        if (withPercent > 0) append(" — $withPercent z „%”")
        if (withPoints > 0) append(" — $withPoints z punktami")
    }
}

private fun collect(resource: String, element: JsonElement, path: String, key: String, stats: MutableMap<String, FieldStats>) {
    when (element) {
        is JsonObject -> element.forEach { (k, v) -> collect(resource, v, "$path.${pathKey(k)}", k, stats) }
        is JsonArray -> element.forEach { collect(resource, it, "$path[]", key, stats) }
        is JsonPrimitive -> {
            val s = stats.getOrPut(path) { FieldStats() }
            s.present++
            if (element is JsonNull) {
                s.types += "null"
                return
            }
            s.nonNull++
            s.types += kindOf(element)
            val isBoolean = !element.isString && element.booleanOrNull != null
            when {
                isBoolean || isShown(resource, key) -> s.values.merge(element.content.take(30), 1, Int::plus)
                element.isString -> {
                    if (PERCENT.containsMatchIn(element.content)) s.withPercent++
                    if (POINTS.containsMatchIn(element.content)) s.withPoints++
                }
            }
        }
    }
}

private fun mask(resource: String, element: JsonElement, key: String): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.mapValues { (k, v) -> mask(resource, v, k) })
    is JsonArray -> JsonArray(element.take(1).map { mask(resource, it, key) })
    is JsonNull -> element
    is JsonPrimitive -> if (!element.isString || isShown(resource, key)) element else JsonPrimitive("‹${kindOf(element)}›")
}

/** Pola, których wartości nie są danymi osobowymi i są potrzebne do mapowania. */
private fun isShown(resource: String, key: String): Boolean = when (key) {
    "Name" -> resource == "Subjects" || resource.endsWith("Categories") || resource.endsWith("Types")
    "Grade" -> resource == "Grades" || resource == "PointGrades" || resource == "BehaviourGrades"
    else -> key in SHOWN_KEYS
}

private fun kindOf(p: JsonPrimitive): String = when {
    !p.isString -> if (p.booleanOrNull != null) "tak/nie" else "liczba"
    DATE.matches(p.content) -> "data"
    TIME.matches(p.content) -> "godzina"
    else -> "tekst"
}

private fun pathKey(key: String) = when {
    DATE_KEY.matches(key) -> "{data}"
    key.isNotEmpty() && key.all(Char::isDigit) -> "{id}"
    else -> key
}

private fun sizeOf(v: JsonElement) = when (v) {
    is JsonArray -> "tablica, ${v.size} el."
    is JsonObject -> "obiekt, ${v.size} kluczy"
    else -> "wartość"
}

private fun sampleOf(v: JsonElement): JsonElement? = when (v) {
    is JsonArray -> v.firstOrNull()
    is JsonObject -> v
    else -> null
}

private const val PAUSE_MS = 400L
private const val MAX_VALUES = 25
private val SKIPPED_KEYS = setOf("Resources", "Url")
private val SHOWN_KEYS = setOf(
    "Short", "HourFrom", "HourTo", "LessonNo", "LuckyNumber", "LuckyNumberDay",
    "BeginSchoolYear", "EndFirstSemester", "EndSchoolYear",
    "Weight", "CountToTheAverage", "Points", "MaxPoints", "GradeValue", "Semester",
)
private val DATE = Regex("""\d{4}-\d{2}-\d{2}([ T]\d{2}:\d{2}(:\d{2})?)?""")
private val TIME = Regex("""\d{2}:\d{2}(:\d{2})?""")
private val DATE_KEY = Regex("""\d{4}-\d{2}-\d{2}""")
private val PERCENT = Regex("""\d\s*%""")
private val POINTS = Regex("""\d+([.,]\d+)?\s*/\s*\d+|pkt|punkt""", RegexOption.IGNORE_CASE)

private val GATEWAY_RESOURCES = listOf(
    "Auth/TokenInfo", "Me", "Classes", "Schools", "Subjects", "Lessons", "Users", "Classrooms",
    "Grades", "Grades/Categories", "Grades/Comments", "Grades/Averages",
    "PointGrades", "PointGrades/Categories", "PointGrades/Averages",
    "DescriptiveGrades", "TextGrades", "BehaviourGrades",
    "Attendances", "Attendances/Types",
    "Calendars/Substitutions", "HomeWorks", "HomeWorks/Categories", "HomeWorkAssignments",
    "SchoolFreeDays", "ClassFreeDays", "TeacherFreeDays",
    "LuckyNumbers", "Notes", "SchoolNotices",
)
