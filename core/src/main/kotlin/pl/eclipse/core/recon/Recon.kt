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
    fun run(email: String, password: String, today: LocalDate = LocalDate.now(ZoneId.of("Europe/Warsaw"))): String =
        buildString {
            appendLine("Rekonesans Librusa, $today")
            val client = LibrusClient()
            try {
                client.login(email, password)
            } catch (e: LibrusException) {
                appendLine("Logowanie: nie udało się. ${e.message}")
                appendTrace(client)
                return@buildString
            } catch (e: IOException) {
                appendLine("Logowanie: brak połączenia z Librusem (${e.javaClass.simpleName}).")
                appendTrace(client)
                return@buildString
            }
            appendLine("Logowanie: OK")
            appendTrace(client)
            appendLine("Konta w Koncie LIBRUS: ${client.accounts.size}")
            client.accounts.forEach { appendLine("  grupa: ${it.group}, stan: ${it.state}, ma token: ${it.accessToken != null}") }

            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val first = "Me"
            call(first) { client.gateway(first) }
            if (!lastOk) {
                appendLine("\nAPI nie odpowiada — dalsze zasoby pominięte.")
                return@buildString
            }
            for (resource in GATEWAY_RESOURCES + "Timetables?weekStart=$monday") {
                call(resource) { client.gateway(resource) }
            }
            appendLine("\nWiadomości sprawdza osobny przycisk „Sprawdź wiadomości”.")
        }

    /**
     * Rekonesans serwisu wiadomości (wiadomosci.librus.pl): droga logowania przez Synergię, adresy API
     * zapisane w kodzie strony i struktura odpowiedzi. Treść, tematy i nadawcy są zamaskowani, tokeny ukryte.
     */
    fun messages(email: String, password: String, today: LocalDate = LocalDate.now(ZoneId.of("Europe/Warsaw"))): String =
        buildString {
            appendLine("Rekonesans wiadomości Librusa, $today")
            val client = LibrusClient()
            try {
                client.login(email, password)
            } catch (e: LibrusException) {
                appendLine("Logowanie: nie udało się. ${e.message}")
                return@buildString
            } catch (e: IOException) {
                appendLine("Logowanie: brak połączenia z Librusem (${e.javaClass.simpleName}).")
                return@buildString
            }
            appendLine("Logowanie: OK")
            try {
                val (code, body) = client.autoLoginToken()
                val json = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                val token = (json?.get("Token") as? JsonPrimitive)?.content
                appendLine("\n## AutoLoginToken: HTTP $code; pola: ${json?.keys.orEmpty()}; token: ${if (token != null) "jest" else "brak"}")
                if (token == null) return@buildString
                pause()
                appendPage("Synergia (wejście tokenem)", client.page("https://synergia.librus.pl/loguj/token/$token/przenies"))
                pause()
                val landing = client.page("https://synergia.librus.pl/wiadomosci3")
                appendPage("Wiadomości", landing)

                val origin = landing.url.substringBefore("://") + "://" + landing.url.substringAfter("://").substringBefore('/')
                val paths = sortedSetOf<String>().apply { addAll(apiPaths(landing.body)) }
                SCRIPT_SRC.findAll(landing.body).map { it.groupValues[1] }.take(MAX_SCRIPTS).forEach { src ->
                    pause()
                    val script = client.page(resolve(landing.url, src))
                    appendLine("Skrypt ${script.url.substringAfterLast('/').substringBefore('?').take(40)}: HTTP ${script.code}, ${script.body.length} znaków")
                    paths += apiPaths(script.body)
                }
                appendLine("\nAdresy API w kodzie strony (${paths.size}):")
                paths.forEach { appendLine("  $it") }

                val probes = (paths.filter { '{' !in it && ':' !in it && '$' !in it } + PROBES).distinct().take(MAX_PROBES)
                for (path in probes) call(path) { client.page(origin + path).let { it.code to it.body } }
            } catch (e: IOException) {
                appendLine("\nBłąd połączenia (${e.javaClass.simpleName}).")
            } catch (e: LibrusException) {
                appendLine("\n${e.message}")
            }
            appendLine("\nPrzebieg (bez parametrów, tokeny ukryte):")
            client.trace.forEach { appendLine("  $it") }
            appendLine("Ciasteczka: ${client.cookieNames()}")
        }

    private fun pause() = Thread.sleep(PAUSE_MS)

    private fun StringBuilder.appendPage(name: String, page: LibrusClient.Page) {
        appendLine("\n## $name")
        page.hops.forEach { appendLine("  $it") }
        // Tytuł może zawierać imię i nazwisko — pokazujemy tylko, które znane słowa w nim są.
        val title = TITLE.find(page.body)?.groupValues?.get(1).orEmpty()
        val words = TITLE_WORDS.filter { title.contains(it, ignoreCase = true) }.ifEmpty { listOf("—") }
        appendLine(
            "Koniec: HTTP ${page.code}, ${page.body.length} znaków, tytuł zawiera: $words, " +
                "formularze: ${FORM_TAG.findAll(page.body).count()}, skrypty: ${SCRIPT_SRC.findAll(page.body).count()}",
        )
    }

    private var lastOk = false

    private fun StringBuilder.appendTrace(client: LibrusClient) {
        appendLine("\nPrzebieg logowania:")
        client.trace.forEach { appendLine("  $it") }
        client.diagnostics.forEach { appendLine(it) }
        appendLine("Ciasteczka: ${client.cookieNames()}")
    }

    private fun StringBuilder.call(name: String, request: () -> Pair<Int, String>) {
        Thread.sleep(PAUSE_MS) // zapytania po kolei, z przerwą — szanujemy serwery Librusa
        val (code, body) = try {
            request()
        } catch (e: IOException) {
            appendLine("\n## $name: błąd połączenia (${e.javaClass.simpleName})")
            return
        }
        lastOk = code in 200..299
        append(describe(name, code, body))
    }
}

internal fun describe(resource: String, code: Int, body: String): String = buildString {
    val name = resource.substringBefore('?')
    // odpowiedź będąca samą listą opisujemy jak obiekt z jednym polem „lista”
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull().let { if (it is JsonArray) JsonObject(mapOf("lista" to it)) else it }
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
private const val MAX_SCRIPTS = 3
private const val MAX_PROBES = 10

/** Ścieżki „/api/…” zapisane w kodzie strony (bez danych — to tylko adresy). */
internal fun apiPaths(text: String): Set<String> = API_PATH.findAll(text).map { it.groupValues[1].trimEnd('/') }.toSortedSet()

private fun resolve(base: String, src: String): String = when {
    src.startsWith("http") -> src
    src.startsWith("//") -> "https:$src"
    src.startsWith("/") -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + src
    else -> base.substringBeforeLast('/') + "/" + src
}

private val API_PATH = Regex("""["'`](/api/[A-Za-z0-9_\-/{}:$.]+)["'`?]""")
private val SCRIPT_SRC = Regex("""<script[^>]+src="([^"]+)"""")
private val TITLE = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
private val FORM_TAG = Regex("""<form\b""", RegexOption.IGNORE_CASE)
private val TITLE_WORDS = listOf("Wiadomości", "Librus", "Synergia", "Zaloguj", "Logowanie", "Błąd")
private val PROBES = listOf("/api/me", "/api/inbox/messages?page=1&limit=5", "/api/outbox/messages?page=1&limit=5", "/api/receivers")
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
    "Classes", "Schools", "Subjects", "Lessons", "Users", "Classrooms",
    "Grades", "Grades/Categories", "Grades/Comments", "Grades/Averages",
    "PointGrades", "PointGrades/Categories", "PointGrades/Averages",
    "DescriptiveGrades", "TextGrades", "BehaviourGrades",
    "Attendances", "Attendances/Types",
    "Calendars/Substitutions", "HomeWorks", "HomeWorks/Categories", "HomeWorkAssignments",
    "SchoolFreeDays", "ClassFreeDays", "TeacherFreeDays",
    "LuckyNumbers", "Notes", "SchoolNotices",
)
