package pl.eclipse.core.source.librus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import pl.eclipse.core.model.Announcement
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.AttendanceCategory
import pl.eclipse.core.model.EventType
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.GradeKind
import pl.eclipse.core.model.Homework
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LessonStatus
import pl.eclipse.core.model.LuckyNumber
import pl.eclipse.core.model.Message
import pl.eclipse.core.model.Note
import pl.eclipse.core.model.Recipient
import pl.eclipse.core.model.NoteKind
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.SentMessage
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import pl.eclipse.core.source.DataSource
import pl.eclipse.core.source.SendResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64

/**
 * Dane z API Librusa (`api.librus.pl/2.0`) według mapowania z `docs/librus-rekonesans.md`.
 * Słowniki (przedmioty, nauczyciele, kategorie…) pobierane raz na instancję — jedna instancja na synchronizację.
 */
class LibrusSource(
    private val client: LibrusClient = LibrusClient(),
    private val get: (String) -> Pair<Int, String> = client::gateway,
) : DataSource {
    private var credentials: Pair<String, String>? = null

    override fun login(email: String, password: String) {
        client.login(email, password)
        credentials = email to password
    }

    override fun student(): StudentInfo {
        val c = json("Classes")["Class"].obj
        return StudentInfo(c["BeginSchoolYear"].date, c["EndFirstSemester"].date, c["EndSchoolYear"].date)
    }

    override fun subjects() = subjectsById.values.toList()

    override fun timetable(weekStart: LocalDate): List<Lesson> =
        json("Timetables?weekStart=$weekStart")["Timetable"].obj.flatMap { (day, slots) ->
            slots.arr.flatMap { it.arr }.map { it.obj }.map { l ->
                val subject = l["Subject"].obj["Id"].str
                val no = l["LessonNo"].str.toInt()
                val teacher = l["Teacher"].obj.let { t -> listOfNotNull(t["FirstName"].strOrNull, t["LastName"].strOrNull).joinToString(" ") }
                Lesson(
                    sourceKey = "lesson-$day-$no-$subject",
                    date = LocalDate.parse(day),
                    lessonNo = no,
                    start = LocalTime.parse(l["HourFrom"].str),
                    end = LocalTime.parse(l["HourTo"].str),
                    subjectKey = subjectKey(subject),
                    teacher = teacher.ifBlank { null },
                    room = l["Classroom"].objOrNull?.get("Id")?.strOrNull?.let { classrooms[it] },
                    status = when {
                        l["IsCanceled"].bool -> LessonStatus.CANCELLED
                        l["IsSubstitutionClass"].bool -> LessonStatus.SUBSTITUTION
                        else -> LessonStatus.NORMAL
                    },
                    changeNote = l["SubstitutionNote"].strOrNull,
                )
            }
        }

    override fun events(from: LocalDate, to: LocalDate): List<SchoolEvent> {
        val categories = dictionary("HomeWorks/Categories", "Categories") { it["Name"].str }
        return json("HomeWorks")["HomeWorks"].arr.map { it.obj }.map { e ->
            val category = categories[e["Category"].obj["Id"].str].orEmpty()
            val lessonNo = e["LessonNo"].strOrNull?.toIntOrNull()
            SchoolEvent(
                sourceKey = "event-${e["Id"].str}",
                date = e["Date"].date,
                lessonNo = lessonNo,
                time = if (lessonNo == null) e["TimeFrom"].strOrNull?.let(LocalTime::parse) else null,
                subjectKey = e["Subject"].objOrNull?.get("Id")?.strOrNull?.let(::subjectKey),
                category = category,
                type = eventType(category),
                description = e["Content"].strOrNull.orEmpty(),
            )
        }.filter { it.date in from..to }
    }

    // Rekonesans: HomeWorkAssignments jest pusty w szkole użytkownika, więc nie znamy jego pól.
    // ponytail: brak zadań domowych; zmapować, gdy w Librusie pojawi się pierwszy wpis.
    override fun homework(from: LocalDate, to: LocalDate): List<Homework> = emptyList()

    override fun grades(): List<Grade> {
        val categories = json("Grades/Categories")["Categories"].arr.associate { c ->
            c.obj["Id"].str to (c.obj["Name"].strOrNull to c.obj["CountToTheAverage"].bool)
        }
        val comments = json("Grades/Comments")["Comments"].arr.groupBy(
            { it.obj["Grade"].obj["Id"].str }, { it.obj["Text"].str },
        )
        val regular = json("Grades")["Grades"].arr.map { it.obj }.map { g ->
            val id = g["Id"].str
            val (category, counts) = categories[g["Category"].obj["Id"].str] ?: (null to true)
            Grade(
                sourceKey = "grade-$id",
                subjectKey = subjectKey(g["Subject"].obj["Id"].str),
                semester = g["Semester"].int,
                kind = when {
                    g["IsFinal"].bool -> GradeKind.FINAL
                    g["IsSemester"].bool -> GradeKind.SEMESTER
                    g["IsFinalProposition"].bool || g["IsSemesterProposition"].bool -> GradeKind.PROPOSED
                    else -> GradeKind.REGULAR
                },
                symbol = g["Grade"].str,
                countsToAverage = counts,
                category = category,
                description = comments[id]?.joinToString("\n"),
                teacher = users[g["AddedBy"].obj["Id"].str],
                date = g["Date"].date,
            )
        }
        val pointCategories = json("PointGrades/Categories")["Categories"].arr.associate { c ->
            c.obj["Id"].str to Triple(c.obj["Name"].strOrNull, c.obj["CountToTheAverage"].bool, c.obj["ValueTo"].double)
        }
        val points = json("PointGrades")["Grades"].arr.map { it.obj }.map { g ->
            val (category, counts, max) = pointCategories[g["Category"].obj["Id"].str] ?: Triple(null, true, null)
            val value = g["GradeValue"].str.toDouble()
            Grade(
                sourceKey = "point-${g["Id"].str}",
                subjectKey = subjectKey(g["Subject"].obj["Id"].str),
                semester = g["Semester"].int,
                kind = GradeKind.POINT,
                symbol = value.toBigDecimal().stripTrailingZeros().toPlainString(),
                points = value,
                maxPoints = max,
                countsToAverage = counts,
                category = category,
                teacher = users[g["AddedBy"].obj["Id"].str],
                date = g["Date"].date,
            )
        }
        return regular + points
    }

    override fun attendance(): List<Attendance> {
        val types = json("Attendances/Types")["Types"].arr.associate { t ->
            t.obj["Id"].str to (t.obj["Short"].str to attendanceCategory(t.obj["Short"].str, t.obj["IsPresenceKind"].bool))
        }
        val lessonSubjects = json("Lessons")["Lessons"].arr.associate { l ->
            l.obj["Id"].str to l.obj["Subject"].obj["Id"].str
        }
        return json("Attendances")["Attendances"].arr.map { it.obj }.map { a ->
            val (short, category) = types[a["Type"].obj["Id"].str] ?: ("?" to AttendanceCategory.OTHER)
            Attendance(
                sourceKey = "attendance-${a["Id"].str}",
                date = a["Date"].date,
                lessonNo = a["LessonNo"].int,
                subjectKey = lessonSubjects[a["Lesson"].obj["Id"].str]?.let(::subjectKey),
                typeShort = short,
                category = category,
                semester = a["Semester"].int,
            )
        }
    }

    override fun notes(since: Instant?): List<Note> = json("Notes")["Notes"].arr.map { it.obj }.map { n ->
        Note(
            sourceKey = "note-${n["Id"].str}",
            date = n["Date"].date,
            teacher = users[n["Teacher"].obj["Id"].str],
            category = null,
            // ponytail: 1 = pozytywna, 0 = negatywna — do potwierdzenia przy kolejnych uwagach
            kind = when (n["Positive"].intOrNull) {
                1 -> NoteKind.POSITIVE
                0 -> NoteKind.NEGATIVE
                else -> NoteKind.NEUTRAL
            },
            text = n["Text"].strOrNull.orEmpty(),
        )
    }

    override fun announcements(since: Instant?): List<Announcement> =
        json("SchoolNotices")["SchoolNotices"].arr.map { it.obj }.map { a ->
            Announcement(
                sourceKey = "notice-${a["Id"].str}",
                date = a["StartDate"].date,
                author = users[a["AddedBy"].obj["Id"].str],
                title = a["Subject"].strOrNull.orEmpty(),
                content = a["Content"].strOrNull.orEmpty(),
            )
        }

    // Wiadomości: osobny serwis wiadomosci.librus.pl, sesja przez Synergię (rekonesans 2026-09-30).
    // ponytail: jedna strona najnowszych wiadomości na synchronizację — wystarcza przy synchronizacji co kilka godzin.
    override fun messages(since: Instant?): List<Message> {
        openMessagesOnce()
        val (code, body) = client.messagesApi("inbox/messages?page=1&limit=$MESSAGES_PAGE")
        if (code !in 200..299) {
            throw LibrusException("Librus nie oddał wiadomości (HTTP $code).", if (code >= 500) LibrusException.Kind.SERVER else LibrusException.Kind.OTHER)
        }
        return Json.parseToJsonElement(body).obj["data"].arr.map { it.obj }.map { m ->
            Message(
                sourceKey = "message-${m["messageId"].str}",
                sentAt = warsawInstant(m["sendDate"].str),
                sender = m["senderName"].strOrNull?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(m["senderFirstName"].strOrNull, m["senderLastName"].strOrNull).joinToString(" "),
                title = m["topic"].strOrNull.orEmpty(),
                content = m["content"].strOrNull?.let(::messageText),
                readAt = m["readDate"].strOrNull?.let(::warsawInstant),
                hasAttachment = m["isAnyFileAttached"].bool,
            )
        }
    }

    private var messagesOpen = false

    /**
     * Wysłane wiadomości. Librus nie pokazał ich przy rekonesansie (skrzynka nadawcza była pusta), więc odbiorcę
     * czytamy z kilku możliwych pól — gdy żadnego nie ma, zostaje sam temat i data.
     */
    override fun sentMessages(): List<SentMessage> {
        openMessagesOnce()
        val (code, body) = client.messagesApi("outbox/messages?page=1&limit=$MESSAGES_PAGE")
        if (code !in 200..299) {
            throw LibrusException(
                "Librus nie oddał wysłanych wiadomości (HTTP $code).",
                if (code >= 500) LibrusException.Kind.SERVER else LibrusException.Kind.OTHER,
            )
        }
        return Json.parseToJsonElement(body).obj["data"].arr.map { it.obj }.mapNotNull { m ->
            val id = m["messageId"].strOrNull ?: return@mapNotNull null
            SentMessage(
                sourceKey = "sent-$id",
                sentAt = m["sendDate"].strOrNull?.let(::warsawInstant) ?: return@mapNotNull null,
                recipients = receiversOf(m),
                title = m["topic"].strOrNull.orEmpty(),
                content = m["content"].strOrNull?.let(::messageText),
            )
        }.sortedByDescending { it.sentAt }
    }

    private fun openMessagesOnce() {
        if (!messagesOpen) {
            client.openMessages()
            messagesOpen = true
        }
    }

    /**
     * Lista odbiorców z modułu wiadomości: najpierw rodzaje odbiorców, potem osoby w każdym rodzaju.
     * Rodzaj, którego Librus nie udostępnia uczniowi, po prostu pomijamy.
     */
    override fun messageRecipients(): List<Recipient> {
        openMessagesOnce()
        val (code, body) = client.messagesModule("Receivers/action/GetTypes", mapOf("includeClass" to "1"))
        if (code !in 200..299) {
            throw LibrusException(
                "Librus nie oddał listy odbiorców (HTTP $code).",
                if (code >= 500) LibrusException.Kind.SERVER else LibrusException.Kind.OTHER,
            )
        }
        moduleError(body)?.let { throw it }
        val types = xmlItems(body).mapNotNull { item ->
            val id = item["id"]?.takeIf { it.isNotBlank() && it !in SKIPPED_RECEIVER_TYPES } ?: return@mapNotNull null
            id to (item["name"]?.takeIf { it.isNotBlank() } ?: id)
        }
        return types.flatMap { (typeId, typeName) ->
            val (listCode, listBody) = client.messagesModule("Receivers/action/GetListForType", mapOf("receiverType" to typeId))
            if (listCode !in 200..299 || moduleError(listBody) != null) return@flatMap emptyList()
            xmlItems(listBody).mapNotNull { item ->
                val id = item["id"]?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return@mapNotNull null
                val label = item["label"]?.replace(SPACES, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                Recipient(id, label, typeName)
            }
        }.distinctBy { it.id }.sortedWith(compareBy({ it.group }, { it.name.lowercase() }))
    }

    /** Wysyła wiadomość przez moduł wiadomości. Temat i treść idą zakodowane base64, tak jak przy odczycie. */
    override fun sendMessage(recipientIds: List<String>, subject: String, text: String): SendResult {
        if (recipientIds.isEmpty()) return SendResult.Rejected("Nie wybrano odbiorcy.")
        openMessagesOnce()
        val (code, body) = client.messagesModule(
            "SendMessage",
            mapOf(
                "topic" to base64(subject),
                "message" to base64(text),
                "receivers" to recipientIds.joinToString(","),
                "actions" to base64("<Actions/>"),
            ),
        )
        return when {
            STATUS_OK.containsMatchIn(body) -> SendResult.Sent(MODULE_DATA.find(body)?.groupValues?.get(1))
            moduleError(body) != null -> SendResult.Rejected(moduleError(body)?.message.orEmpty())
            // Nieznana odpowiedź albo błąd serwera: wiadomość mogła pójść, więc nie namawiamy na ponowną próbę.
            else -> SendResult.Unknown("Librus nie potwierdził wysłania (HTTP $code).")
        }
    }

    override fun luckyNumber(): LuckyNumber? = json("LuckyNumbers")["LuckyNumber"].objOrNull?.let {
        LuckyNumber(it["LuckyNumberDay"].date, it["LuckyNumber"].int)
    }

    private val subjectsById: Map<String, Subject> by lazy {
        json("Subjects")["Subjects"].arr.associate { s ->
            val id = s.obj["Id"].str
            id to Subject(subjectKey(id), s.obj["Name"].str, s.obj["Short"].strOrNull ?: s.obj["Name"].str.take(3))
        }
    }

    private val users: Map<String, String> by lazy {
        json("Users")["Users"].arr.associate { u ->
            u.obj["Id"].str to listOfNotNull(u.obj["FirstName"].strOrNull, u.obj["LastName"].strOrNull).joinToString(" ")
        }
    }

    private val classrooms: Map<String, String> by lazy { dictionary("Classrooms", "Classrooms") { it["Symbol"].str } }

    private fun dictionary(path: String, key: String, value: (JsonObject) -> String) =
        json(path)[key].arr.associate { it.obj["Id"].str to value(it.obj) }

    /** Pobiera zasób; przy wygasłym tokenie (401) loguje się ponownie raz. */
    private fun json(path: String): JsonObject {
        var (code, body) = get(path)
        val saved = credentials
        if (code == 401 && saved != null) {
            client.login(saved.first, saved.second)
            val retry = get(path)
            code = retry.first
            body = retry.second
        }
        if (code !in 200..299) throw LibrusException("Librus nie oddał danych: $path (HTTP $code).", if (code >= 500) LibrusException.Kind.SERVER else LibrusException.Kind.OTHER)
        return Json.parseToJsonElement(body).obj
    }

    private fun subjectKey(librusId: String) = "subject-$librusId"
}

private const val MESSAGES_PAGE = 50
private val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
private val BASE64 = Regex("""[A-Za-z0-9+/]+={0,2}""")

/** Data z serwisu wiadomości („2026-09-30 14:03:12”, z „T” albo sama data) — czas polski. */
internal fun warsawInstant(text: String): Instant {
    val value = text.take(19).replace(' ', 'T')
    val local = if (value.length == 10) LocalDate.parse(value).atStartOfDay() else LocalDateTime.parse(value)
    return local.atZone(WARSAW).toInstant()
}

/** Treść wiadomości jako zwykły tekst: odkodowana z base64, jeśli tak przyszła, i bez znaczników HTML. */
internal fun messageText(raw: String): String {
    val decoded = raw.takeIf { it.length >= 8 && it.length % 4 == 0 && BASE64.matches(it) }
        ?.let { runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrNull() }
        ?.takeIf { text -> text.all { it == '\n' || it == '\r' || it == '\t' || (!it.isISOControl() && it != '\uFFFD') } }
    return htmlToText(decoded ?: raw)
}

internal fun htmlToText(html: String): String = unescapeEntities(
    html.replace(Regex("""(?i)<br\s*/?>|</p>|</div>|</li>"""), "\n").replace(Regex("""<[^>]+>"""), ""),
)
    .lines().joinToString("\n") { it.trim() }
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()

/** Odbiorcy wysłanej wiadomości — nazwa, lista nazw albo imię i nazwisko, zależnie od tego, co poda Librus. */
private fun receiversOf(message: JsonObject): String {
    RECEIVER_NAME_KEYS.forEach { key ->
        val element = message[key]
        val text = when (element) {
            is JsonArray -> element.mapNotNull { it.obj.let(::personName).takeIf(String::isNotBlank) }.joinToString(", ")
            else -> element.strOrNull
        }
        if (!text.isNullOrBlank()) return text
    }
    return personName(message)
}

private fun personName(o: JsonObject): String = o["receiverName"].strOrNull?.takeIf { it.isNotBlank() }
    ?: listOfNotNull(o["receiverFirstName"].strOrNull, o["receiverLastName"].strOrNull).joinToString(" ").trim()

private val RECEIVER_NAME_KEYS = listOf("receiverName", "receivers", "receiver", "receiversNames")

private fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

/** Rodzaje odbiorców, które nie są listą osób. */
private val SKIPPED_RECEIVER_TYPES = setOf("contactsGroups")
private val STATUS_OK = Regex("""<status>\s*ok\s*</status>""", RegexOption.IGNORE_CASE)
private val MODULE_DATA = Regex("""<data>\s*(\d+)\s*</data>""")
private val MODULE_MESSAGE = Regex("""<message>([^<]*)</message>""")
private val SPACES = Regex("""\s+""")

/** Znany błąd modułu wiadomości albo null. Komunikaty Librusa są ogólne, więc pokazujemy je użytkownikowi. */
internal fun moduleError(body: String): LibrusException? {
    val message = MODULE_MESSAGE.find(body)?.groupValues?.get(1)?.trim()
    return when {
        body.contains("Niepoprawny login", ignoreCase = true) ->
            LibrusException("Sesja wiadomości wygasła. Odśwież dane i spróbuj ponownie.", LibrusException.Kind.CREDENTIALS)
        "OffLine" in body ->
            LibrusException("Wiadomości Librusa mają przerwę techniczną. Spróbuj później.", LibrusException.Kind.MAINTENANCE)
        "eAccessDeny" in body || "eVarWhitThisNameNotExists" in body || "stop.png" in body ->
            LibrusException("Librus nie pozwolił na tę operację w wiadomościach.")
        STATUS_ERROR.containsMatchIn(body) || "<error>" in body ->
            LibrusException(message?.takeIf { it.isNotEmpty() } ?: "Librus odrzucił wiadomość.")
        else -> null
    }
}

private val STATUS_ERROR = Regex("""<status>\s*error\s*</status>""", RegexOption.IGNORE_CASE)

/**
 * Najgłębsze elementy `<ArrayItem>` z odpowiedzi modułu jako mapy pole → wartość.
 * Moduł zwraca XML, a potrzebujemy z niego tylko prostych pól, więc obchodzimy się bez biblioteki.
 */
internal fun xmlItems(body: String): List<Map<String, String>> = ARRAY_ITEM.findAll(body)
    .map { item -> SIMPLE_TAG.findAll(item.groupValues[1]).associate { it.groupValues[1] to unescapeEntities(it.groupValues[2]) } }
    .filter { it.isNotEmpty() }
    .toList()

private val ARRAY_ITEM = Regex("""<ArrayItem>((?:(?!<ArrayItem>)[\s\S])*?)</ArrayItem>""")
private val SIMPLE_TAG = Regex("""<([A-Za-z_][\w.:-]*)>([^<]*)</\1>""")

/** Kategoria terminarza → typ wydarzenia (zaakceptowane mapowanie, SPEC 11.3). */
internal fun eventType(category: String): EventType {
    val c = category.lowercase()
    return when {
        "sprawdzian" in c || "poprawa" in c || "praca klasowa" in c || c.startsWith("test") -> EventType.TEST
        "kartkówka" in c -> EventType.QUIZ
        "dzień bez zajęć" in c || "dzień wolny" in c -> EventType.DAY_OFF
        "zebranie" in c || "dzień otwarty" in c || "warsztaty" in c || "patronackie" in c || "wycieczka" in c -> EventType.TRIP
        else -> EventType.OTHER
    }
}

/** Encje HTML i XML na znaki („&amp;” → „&”). */
internal fun unescapeEntities(text: String): String = text
    .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")

/** Typ frekwencji → kategoria (zaakceptowane mapowanie, SPEC 7). */
internal fun attendanceCategory(short: String, isPresence: Boolean): AttendanceCategory = when (short.lowercase()) {
    "ob", "uo" -> AttendanceCategory.PRESENT
    "sp" -> AttendanceCategory.LATE
    "nb" -> AttendanceCategory.ABSENT
    "u" -> AttendanceCategory.ABSENT_EXCUSED
    "zw", "zu", "wf" -> AttendanceCategory.RELEASED
    "un" -> AttendanceCategory.OTHER
    else -> if (isPresence) AttendanceCategory.PRESENT else AttendanceCategory.OTHER
}

private val JsonElement?.obj: JsonObject get() = this as? JsonObject ?: throw LibrusException("Nieoczekiwana odpowiedź Librusa (brak obiektu).")
private val JsonElement?.objOrNull: JsonObject? get() = this as? JsonObject
private val JsonElement?.arr: JsonArray get() = this as? JsonArray ?: JsonArray(emptyList())
private val JsonElement?.strOrNull: String? get() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
private val JsonElement?.str: String get() = strOrNull ?: throw LibrusException("Nieoczekiwana odpowiedź Librusa (brak pola).")
private val JsonElement?.int: Int get() = str.toInt()
private val JsonElement?.intOrNull: Int? get() = strOrNull?.toIntOrNull()
private val JsonElement?.double: Double? get() = strOrNull?.toDoubleOrNull()
private val JsonElement?.bool: Boolean get() = (this as? JsonPrimitive)?.booleanOrNull ?: false
private val JsonElement?.date: LocalDate get() = LocalDate.parse(str.take(10))
