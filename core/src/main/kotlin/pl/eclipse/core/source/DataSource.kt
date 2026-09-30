package pl.eclipse.core.source

import pl.eclipse.core.model.Announcement
import pl.eclipse.core.model.Attendance
import pl.eclipse.core.model.Grade
import pl.eclipse.core.model.Homework
import pl.eclipse.core.model.Lesson
import pl.eclipse.core.model.LuckyNumber
import pl.eclipse.core.model.Message
import pl.eclipse.core.model.Note
import pl.eclipse.core.model.SchoolEvent
import pl.eclipse.core.model.StudentInfo
import pl.eclipse.core.model.Subject
import java.time.Instant
import java.time.LocalDate

/**
 * Jedyne miejsce, przez które aplikacja pobiera dane szkolne (SPEC 10.1).
 * Funkcje blokują wątek — wywołuj je w tle (SyncWorker).
 */
interface DataSource {
    fun login(email: String, password: String)
    fun student(): StudentInfo
    fun subjects(): List<Subject>
    fun timetable(weekStart: LocalDate): List<Lesson>
    fun events(from: LocalDate, to: LocalDate): List<SchoolEvent>
    fun homework(from: LocalDate, to: LocalDate): List<Homework>
    fun grades(): List<Grade>
    fun attendance(): List<Attendance>
    fun notes(since: Instant?): List<Note>
    fun announcements(since: Instant?): List<Announcement>
    fun messages(since: Instant?): List<Message>
    fun luckyNumber(): LuckyNumber?
}
