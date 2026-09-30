@file:OptIn(ExperimentalLayoutApi::class)

package pl.eclipse.app.ui.settings

import pl.eclipse.app.ui.components.ChoiceChips
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.eclipse.app.BuildConfig
import pl.eclipse.app.R
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.NotificationType
import pl.eclipse.app.data.ThemeMode
import pl.eclipse.app.formatDate
import pl.eclipse.app.ui.TYPE_KEYS
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.defaultTypeColor
import pl.eclipse.app.ui.formatSyncTime
import pl.eclipse.app.ui.style.parseHex
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.core.calc.GradingRules
import pl.eclipse.core.calc.WarningRule
import pl.eclipse.core.model.AttendanceCategory

private val PICKER = listOf(
    Palette.Test, Palette.Quiz, Palette.Homework, Palette.SchoolEvent, Palette.DayOff, Palette.Custom,
    Palette.Subjects[1], Palette.Subjects[3], Palette.Subjects[5], Palette.Subjects[7],
)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenSubject: (String) -> Unit,
    onOpenStyle: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    fun set(block: (AppSettings) -> AppSettings) = viewModel.update(block)
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        item { AccountSection(state, viewModel) }
        item { AppearanceSection(s, ::set) }
        item { ColorsSection(s, state, ::set, onOpenSubject) }
        item { LabelsSection(state, viewModel) }
        item { GradingSection(s, ::set) }
        item { AttendanceSection(s, state, ::set) }
        item { ImportantSection(s, ::set) }
        item { NotificationsSection(s, ::set, viewModel) }
        item { SyncSection(s, state, ::set, viewModel) }
        item { DataSection(s, ::set, viewModel) }
        // Diagnostyka także w wersji release: awarie, synchronizacje i rekonesans są potrzebne na telefonie.
        item {
            Section(stringResource(R.string.settings_developer)) {
                if (BuildConfig.DEBUG) TextButton(onClick = onOpenStyle) { Text(stringResource(R.string.nav_style)) }
                TextButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.diag_title)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    EclipseCard {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Eclipse.colors.text)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    val c = Eclipse.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Liczba z przyciskami − / + (cele dotyku 48 dp). */
@Composable
private fun Stepper(label: String, value: Int, range: IntRange, format: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    val c = Eclipse.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Text("−", style = MaterialTheme.typography.titleLarge, color = c.text)
        }
        Text(format(value), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.increase), tint = c.text)
        }
    }
}

@Composable
private fun AccountSection(state: SettingsState, viewModel: SettingsViewModel) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_account)) {
        Text(
            when {
                state.settings.demoMode -> stringResource(R.string.account_demo)
                state.email != null -> stringResource(R.string.account_logged, state.email)
                else -> stringResource(R.string.account_none)
            },
            style = MaterialTheme.typography.bodyLarge, color = c.text,
        )
        SecondaryButton(stringResource(R.string.logout), viewModel::logout, Modifier.fillMaxWidth())
        Text(stringResource(R.string.logout_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun AppearanceSection(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_appearance)) {
        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
        ChoiceChips(modes.map { (mode, label) -> mode to stringResource(label) }, s.themeMode, { mode -> set { it.copy(themeMode = mode) } })
        Text(stringResource(R.string.accent), style = MaterialTheme.typography.titleSmall, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palette.Accents.forEach { (name, color) ->
                val argb = color.toArgb().toLong() and 0xFFFFFFFF
                val selected = argb == (s.accent and 0xFFFFFFFF)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(color)
                            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
                            .clickable { set { it.copy(accent = argb) } }
                            .semantics { contentDescription = name },
                    )
                    Text(name, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
        var hex by remember { mutableStateOf("") }
        OutlinedTextField(hex, { v -> hex = v; parseHex(v)?.let { argb -> set { it.copy(accent = argb) } } }, label = { Text(stringResource(R.string.accent_custom)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SwitchRow(stringResource(R.string.less_transparency), s.lessTransparency, { v -> set { it.copy(lessTransparency = v) } }, stringResource(R.string.less_transparency_hint))
    }
}

@Composable
private fun ColorsSection(s: AppSettings, state: SettingsState, set: ((AppSettings) -> AppSettings) -> Unit, onOpenSubject: (String) -> Unit) {
    val c = Eclipse.colors
    var editing by remember { mutableStateOf<String?>(null) }
    Section(stringResource(R.string.settings_colors)) {
        Text(stringResource(R.string.type_colors), style = MaterialTheme.typography.titleSmall, color = c.text)
        TYPE_KEYS.forEach { key ->
            val color = s.typeColors[key]?.let { Color(it) } ?: defaultTypeColor(key)
            Row(Modifier.fillMaxWidth().clip(CircleShape).clickable { editing = if (editing == key) null else key }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(color))
                Text(stringResource(typeLabel(key)), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text(stringResource(R.string.change), style = MaterialTheme.typography.labelLarge, color = c.accentText)
            }
            if (editing == key) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PICKER.forEach { col ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(col).border(2.dp, if (col == color) c.text else Color.Transparent, CircleShape)
                                .clickable { set { it.copy(typeColors = it.typeColors + (key to (col.toArgb().toLong() and 0xFFFFFFFF))) }; editing = null },
                        )
                    }
                }
                TextButton(onClick = { set { it.copy(typeColors = it.typeColors - key) }; editing = null }) { Text(stringResource(R.string.restore_default)) }
            }
        }
        HorizontalDivider(color = c.border)
        Text(stringResource(R.string.subject_colors), style = MaterialTheme.typography.titleSmall, color = c.text)
        Text(stringResource(R.string.subject_colors_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.subjects.forEach { subject ->
                TextButton(onClick = { onOpenSubject(subject.sourceKey) }) { Text(subject.name) }
            }
        }
    }
}

private fun typeLabel(key: String) = when (key) {
    "TEST" -> R.string.word_test_cap
    "QUIZ" -> R.string.word_quiz_cap
    "HOMEWORK" -> R.string.kind_homework
    "EVENT" -> R.string.kind_event
    "DAY_OFF" -> R.string.kind_day_off
    else -> R.string.kind_custom
}

@Composable
private fun LabelsSection(state: SettingsState, viewModel: SettingsViewModel) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_labels)) {
        state.labels.forEach { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(label.color)))
                Text(label.name, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                IconButton(onClick = { viewModel.deleteLabel(label) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_named, label.name), tint = c.textSecondary) }
            }
        }
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(PICKER.first()) }
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PICKER.forEach { col ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(col).border(2.dp, if (col == color) c.text else Color.Transparent, CircleShape).clickable { color = col })
            }
        }
        TextButton(onClick = { viewModel.addLabel(name, color.toArgb()); name = "" }, enabled = name.isNotBlank()) { Text(stringResource(R.string.create_label)) }

        HorizontalDivider(color = c.border)
        Text(stringResource(R.string.toolbox_templates), style = MaterialTheme.typography.titleSmall, color = c.text)
        state.templates.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.template_chip, t.name, t.durationMinutes), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.deleteTemplate(t) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete_named, t.name), tint = c.textSecondary) }
            }
        }
        var templateName by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf(60) }
        OutlinedTextField(templateName, { templateName = it }, label = { Text(stringResource(R.string.template_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Stepper(stringResource(R.string.template_duration), minutes, 15..240, { "$it min" }) { minutes = (it / 15).coerceAtLeast(1) * 15 }
        TextButton(onClick = { viewModel.addTemplate(templateName, minutes); templateName = "" }, enabled = templateName.isNotBlank()) { Text(stringResource(R.string.add_template)) }

        if (state.subjectRules.isNotEmpty()) {
            HorizontalDivider(color = c.border)
            Text(stringResource(R.string.subject_rules), style = MaterialTheme.typography.titleSmall, color = c.text)
            state.subjectRules.forEach { (rule, subject, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.subject_rule, label?.name.orEmpty(), subject), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteRule(rule) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete), tint = c.textSecondary) }
                }
            }
        }
    }
}

@Composable
private fun GradingSection(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Eclipse.colors
    val rules = s.grading
    Section(stringResource(R.string.settings_grading)) {
        Text(stringResource(R.string.grading_table), style = MaterialTheme.typography.titleSmall, color = c.text)
        (6 downTo 1).forEach { grade ->
            val value = rules.table[grade] ?: 0.0
            Stepper(stringResource(R.string.grading_table_row, grade), value.toInt(), 0..100, { "$it%" }) { v ->
                set { it.copy(grading = it.grading.copy(table = it.grading.table + (grade to v.toDouble()))) }
            }
        }
        Text(stringResource(R.string.grading_thresholds), style = MaterialTheme.typography.titleSmall, color = c.text)
        (6 downTo 2).forEach { grade ->
            val value = rules.thresholds[grade] ?: 0.0
            Stepper(stringResource(R.string.grading_threshold_row, grade), value.toInt(), 1..100, { "≥ $it%" }) { v ->
                set { it.copy(grading = it.grading.copy(thresholds = it.grading.thresholds + (grade to v.toDouble()))) }
            }
        }
        TextButton(onClick = { set { it.copy(grading = GradingRules()) } }) { Text(stringResource(R.string.restore_defaults)) }
        SwitchRow(stringResource(R.string.grading_round), rules.roundBeforeThreshold, { v -> set { it.copy(grading = it.grading.copy(roundBeforeThreshold = v)) } })
        val plus = rules.symbolOverrides["+"]
        SwitchRow(
            stringResource(R.string.grading_plus_minus), plus != null,
            { v ->
                set {
                    it.copy(grading = it.grading.copy(symbolOverrides = if (v) it.grading.symbolOverrides + ("+" to 100.0) + ("-" to 0.0) else it.grading.symbolOverrides - "+" - "-"))
                }
            },
            stringResource(R.string.grading_plus_minus_hint),
        )
        if (plus != null) {
            listOf("+" to R.string.grading_plus_value, "-" to R.string.grading_minus_value).forEach { (symbol, label) ->
                val value = rules.symbolOverrides[symbol] ?: 0.0
                Stepper(stringResource(label), value.toInt(), 0..100, { "$it%" }) { v ->
                    set { it.copy(grading = it.grading.copy(symbolOverrides = it.grading.symbolOverrides + (symbol to v.toDouble()))) }
                }
            }
        }
        Text(stringResource(R.string.grading_description_off), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun AttendanceSection(s: AppSettings, state: SettingsState, set: ((AppSettings) -> AppSettings) -> Unit) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_attendance)) {
        Text(stringResource(R.string.attendance_counted), style = MaterialTheme.typography.titleSmall, color = c.text)
        listOf(
            AttendanceCategory.ABSENT to R.string.cat_absent, AttendanceCategory.ABSENT_EXCUSED to R.string.cat_excused,
            AttendanceCategory.RELEASED to R.string.cat_released, AttendanceCategory.SCHOOL_DUTY to R.string.cat_school,
            AttendanceCategory.OTHER to R.string.cat_other,
        ).forEach { (cat, label) ->
            SwitchRow(stringResource(label), cat in s.countedAbsences, { v -> set { it.copy(countedAbsences = if (v) it.countedAbsences + cat else it.countedAbsences - cat) } })
        }
        if (state.attendanceTypes.isNotEmpty()) {
            Text(stringResource(R.string.attendance_mapping), style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(state.attendanceTypes.joinToString(" · ") { (short, cat) -> "$short → ${cat.name.lowercase()}" }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        state.student?.let { st ->
            Text(
                stringResource(R.string.semester_dates, formatDate(st.schoolYearStart), formatDate(st.firstSemesterEnd), formatDate(st.schoolYearEnd)),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun ImportantSection(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val i = s.important
    Section(stringResource(R.string.nav_important)) {
        Stepper(stringResource(R.string.imp_above3), i.justAbove3Max.toInt(), 50..60, { "$it%" }) { v -> set { it.copy(important = it.important.copy(justAbove3Max = v.toDouble())) } }
        Stepper(stringResource(R.string.imp_fresh_days), i.freshDays.toInt(), 3..30, { "$it dni" }) { v -> set { it.copy(important = it.important.copy(freshDays = v.toLong())) } }
        Stepper(stringResource(R.string.imp_attendance_near), i.attendanceNear.toInt(), 51..80, { "$it%" }) { v -> set { it.copy(important = it.important.copy(attendanceNear = v.toDouble())) } }
        Stepper(stringResource(R.string.imp_reserve_near), i.reserveNear, 1..10) { v -> set { it.copy(important = it.important.copy(reserveNear = v)) } }
        Stepper(stringResource(R.string.imp_drop), i.dropPp.toInt(), 1..20, { "$it pp" }) { v -> set { it.copy(important = it.important.copy(dropPp = v.toDouble())) } }
        WarningRule.entries.forEach { rule ->
            SwitchRow(stringResource(rule.label), rule !in i.disabled, { v ->
                set { it.copy(important = it.important.copy(disabled = if (v) it.important.disabled - rule else it.important.disabled + rule)) }
            })
        }
    }
}

private val WarningRule.label
    get() = when (this) {
        WarningRule.AVERAGE_BELOW_2 -> R.string.rule_below_2
        WarningRule.PROPOSED_ONE -> R.string.rule_proposed_one
        WarningRule.ATTENDANCE_CRITICAL -> R.string.rule_attendance_critical
        WarningRule.AVERAGE_IN_2 -> R.string.rule_in_2
        WarningRule.FRESH_LOW_GRADE -> R.string.rule_fresh
        WarningRule.ATTENDANCE_NEAR -> R.string.rule_attendance_near
        WarningRule.AVERAGE_DROP -> R.string.rule_drop
        WarningRule.JUST_ABOVE_3 -> R.string.rule_above_3
        WarningRule.MARKED_DIFFICULT -> R.string.rule_difficult
        WarningRule.UPCOMING_TEST -> R.string.rule_upcoming
    }

@Composable
private fun NotificationsSection(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, viewModel: SettingsViewModel) {
    val c = Eclipse.colors
    val context = LocalContext.current
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    Section(stringResource(R.string.nav_notifications)) {
        Text(stringResource(if (enabled) R.string.notif_permission_on else R.string.notif_permission_off), style = MaterialTheme.typography.bodyMedium, color = if (enabled) c.text else MaterialTheme.colorScheme.error)
        if (!enabled) {
            SecondaryButton(stringResource(R.string.open_system_settings), {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }, Modifier.fillMaxWidth())
        }
        NotificationType.entries.forEach { type ->
            SwitchRow(stringResource(type.label), type !in s.disabledNotifications, { v ->
                set { it.copy(disabledNotifications = if (v) it.disabledNotifications - type else it.disabledNotifications + type) }
            })
        }
        Stepper(stringResource(R.string.quiet_from), s.quietFromHour, 0..23, { "$it:00" }) { v -> set { it.copy(quietFromHour = v) } }
        Stepper(stringResource(R.string.quiet_to), s.quietToHour, 0..23, { "$it:00" }) { v -> set { it.copy(quietToHour = v) } }
        Stepper(stringResource(R.string.reminder_hour), s.reminderHour, 6..22, { "$it:00" }) { v -> set { it.copy(reminderHour = v) } }
        SecondaryButton(stringResource(R.string.send_test_notification), viewModel::sendTest, Modifier.fillMaxWidth())
    }
}

private val NotificationType.label
    get() = when (this) {
        NotificationType.GRADE -> R.string.nt_grade
        NotificationType.TEST -> R.string.nt_test
        NotificationType.REMINDER -> R.string.nt_reminder
        NotificationType.PLAN_CHANGE -> R.string.nt_plan
        NotificationType.IMPORTANT -> R.string.nt_important
        NotificationType.LUCKY_NUMBER -> R.string.nt_lucky
        NotificationType.INBOX -> R.string.nt_inbox
        NotificationType.SYNC_PROBLEM -> R.string.nt_sync
    }

@Composable
private fun SyncSection(s: AppSettings, state: SettingsState, set: ((AppSettings) -> AppSettings) -> Unit, viewModel: SettingsViewModel) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_sync)) {
        Stepper(stringResource(R.string.sync_interval), s.syncIntervalHours, 1..12, { "$it h" }) { v -> set { it.copy(syncIntervalHours = v) } }
        Stepper(stringResource(R.string.sync_from), s.syncFromHour, 0..23, { "$it:00" }) { v -> set { it.copy(syncFromHour = v) } }
        Stepper(stringResource(R.string.sync_to), s.syncToHour, 1..24, { "$it:00" }) { v -> set { it.copy(syncToHour = v) } }
        Text(stringResource(if (state.periodicScheduled) R.string.diag_periodic_on else R.string.diag_periodic_off), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        PrimaryButton(stringResource(R.string.sync_now), viewModel::syncNow, Modifier.fillMaxWidth())
        Text(stringResource(R.string.diag_runs), style = MaterialTheme.typography.titleSmall, color = c.text)
        if (state.runs.isEmpty()) Text(stringResource(R.string.diag_no_runs), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        state.runs.forEach { run ->
            Text(
                formatSyncTime(run.startedAt) + " — " + stringResource(if (run.success) R.string.diag_run_ok else R.string.diag_run_error),
                style = MaterialTheme.typography.labelLarge, color = if (run.success) c.text else MaterialTheme.colorScheme.error,
            )
            if (run.errors.isNotBlank()) Text(run.errors, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun DataSection(s: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, viewModel: SettingsViewModel) {
    val c = Eclipse.colors
    Section(stringResource(R.string.settings_data)) {
        SwitchRow(stringResource(R.string.demo_mode), s.demoMode, viewModel::useDemo, stringResource(R.string.demo_hint))
        var number by remember(s.myDiaryNumber) { mutableStateOf(s.myDiaryNumber?.toString().orEmpty()) }
        OutlinedTextField(
            number,
            { v -> number = v.filter(Char::isDigit).take(2); set { it.copy(myDiaryNumber = number.toIntOrNull()) } },
            label = { Text(stringResource(R.string.my_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.my_number_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        SecondaryButton(stringResource(R.string.refetch), viewModel::refetch, Modifier.fillMaxWidth())
        Text(stringResource(R.string.refetch_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}
