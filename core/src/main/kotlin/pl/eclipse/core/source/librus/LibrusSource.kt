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
import pl.eclipse.core.model.NoteKind
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import pl.eclipse.core.source.DataSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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

    // ponytail: wiadomości są w osobnym serwisie (wiadomosci.librus.pl), jeszcze niezbadanym — patrz PROGRESS.md.
    override fun messages(since: Instant?): List<Message> = emptyList()

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
        if (code !in 200..299) throw LibrusException("Librus nie oddał danych: $path (HTTP $code).")
        return Json.parseToJsonElement(body).obj
    }

    private fun subjectKey(librusId: String) = "subject-$librusId"
}

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
