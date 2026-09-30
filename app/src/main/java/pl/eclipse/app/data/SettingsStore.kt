package pl.eclipse.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import pl.eclipse.core.calc.DEFAULT_COUNTED_ABSENCES
import pl.eclipse.core.calc.GradingRules
import pl.eclipse.core.calc.ImportantSettings
import pl.eclipse.core.model.AttendanceCategory

/** Ustawienia z wartościami domyślnymi (SPEC 12.8). Wygląd dojdzie w Etapie 2. */
@Serializable
data class AppSettings(
    val demoMode: Boolean = false,
    val myDiaryNumber: Int? = null,
    val syncIntervalHours: Int = 3,
    val syncFromHour: Int = 6,
    val syncToHour: Int = 22,
    val quietFromHour: Int = 22,
    val quietToHour: Int = 7,
    val reminderHour: Int = 18,
    /** Wyłączone typy powiadomień (domyślnie wszystkie włączone). */
    val disabledNotifications: Set<NotificationType> = emptySet(),
    val grading: GradingRules = GradingRules(),
    val important: ImportantSettings = ImportantSettings(),
    val countedAbsences: Set<AttendanceCategory> = DEFAULT_COUNTED_ABSENCES,
)

enum class NotificationType { GRADE, TEST, REMINDER, PLAN_CHANGE, IMPORTANT, LUCKY_NUMBER, INBOX, SYNC_PROBLEM }

private val Context.settingsDataStore by preferencesDataStore("settings")
private val SETTINGS = stringPreferencesKey("settings_json")

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[SETTINGS]?.let { runCatching { JSON.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val now = prefs[SETTINGS]?.let { runCatching { JSON.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
            prefs[SETTINGS] = JSON.encodeToString(AppSettings.serializer(), transform(now))
        }
    }
}
