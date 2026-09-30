package pl.eclipse.app.ui.style

import pl.eclipse.app.ui.components.ChoiceChips
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.eclipse.app.R
import pl.eclipse.app.container
import pl.eclipse.app.data.AppSettings
import pl.eclipse.app.data.ThemeMode
import pl.eclipse.app.ui.components.BlockKind
import pl.eclipse.app.ui.components.BlockStatus
import pl.eclipse.app.ui.components.DiscLabel
import pl.eclipse.app.ui.components.EclipseBackground
import pl.eclipse.app.ui.components.EclipseCard
import pl.eclipse.app.ui.components.EclipseDisc
import pl.eclipse.app.ui.components.GradeTile
import pl.eclipse.app.ui.components.PercentBar
import pl.eclipse.app.ui.components.PrimaryButton
import pl.eclipse.app.ui.components.ScheduleBlock
import pl.eclipse.app.ui.components.SecondaryButton
import pl.eclipse.app.ui.components.SectionTitle
import pl.eclipse.app.ui.components.Tag
import pl.eclipse.app.ui.components.glass
import pl.eclipse.app.ui.components.glassSource
import pl.eclipse.app.ui.components.rememberGlassState
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.levelColor
import pl.eclipse.core.calc.WarningLevel

/** Próbnik stylu (Etap 2a, tylko wersja debug): próbki wszystkich elementów w bieżącym motywie i akcencie. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StyleScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val store = context.container.settings
    val scope = context.container.scope
    val settings by store.settings.collectAsStateWithLifecycle(AppSettings())
    val c = Eclipse.colors
    fun update(block: (AppSettings) -> AppSettings) {
        scope.launch { store.update(block) }
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle(stringResource(R.string.style_theme))
        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
        ChoiceChips(modes.map { (mode, label) -> mode to stringResource(label) }, settings.themeMode, { mode -> update { it.copy(themeMode = mode) } })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.less_transparency), style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
            Switch(checked = settings.lessTransparency, onCheckedChange = { v -> update { it.copy(lessTransparency = v) } })
        }
        AccentPicker(settings.accent) { color -> update { it.copy(accent = color) } }

        SectionTitle(stringResource(R.string.style_glass))
        GlassSample()

        SectionTitle(stringResource(R.string.style_buttons))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(stringResource(R.string.refresh), {})
            SecondaryButton(stringResource(R.string.style_add_event), {})
            TextButton(onClick = {}) { Text(stringResource(R.string.try_demo)) }
            PrimaryButton(stringResource(R.string.style_disabled), {}, enabled = false)
        }

        SectionTitle(stringResource(R.string.style_type))
        EclipseCard {
            Text("78,4%", style = MaterialTheme.typography.displayMedium.merge(TabularNumbers), color = c.text)
            Text(stringResource(R.string.nav_grades), style = MaterialTheme.typography.headlineLarge, color = c.text)
            Text(stringResource(R.string.style_pangram), style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(stringResource(R.string.style_body), style = MaterialTheme.typography.bodyMedium, color = c.text)
            Text(stringResource(R.string.style_secondary), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text(stringResource(R.string.style_link), style = MaterialTheme.typography.labelLarge, color = c.accentText)
        }

        SectionTitle(stringResource(R.string.style_grades))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GradeTile("6", 6)
            GradeTile("5", 5, isNew = true)
            GradeTile("4+", 4)
            GradeTile("3", 3)
            GradeTile("2", 2)
            GradeTile("1", 1, isNew = true)
            GradeTile("17/20", 4)
            GradeTile("np", null)
        }
        EclipseCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("matematyka", style = MaterialTheme.typography.titleLarge, color = c.text)
                    Text(stringResource(R.string.style_prediction), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Text("71,8%", style = MaterialTheme.typography.displaySmall.merge(TabularNumbers), color = c.readable(pl.eclipse.app.ui.theme.gradeColor(3)))
            }
            PercentBar(71.8, listOf(40.0, 50.0, 75.0, 90.0, 100.0), Modifier.padding(vertical = 8.dp), grade = 3)
            Text(stringResource(R.string.style_distance), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }

        SectionTitle(stringResource(R.string.style_blocks))
        ScheduleBlock("Fizyka — sprawdzian", BlockKind.TEST, Palette.Test, Palette.Subjects[0], subtitle = "3. lekcja · kinematyka", tag = "za 3 dni", modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Matematyka — kartkówka", BlockKind.QUIZ, Palette.Quiz, Palette.Subjects[5], subtitle = "2. lekcja", tag = "jutro", modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Język polski — zadanie", BlockKind.HOMEWORK, Palette.Homework, Palette.Subjects[3], subtitle = "Notatka o „Lalce”", modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Chemia", BlockKind.LESSON, Palette.Test, Palette.Subjects[1], subtitle = "sala 15 · Marta Wójcik", modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Biologia", BlockKind.LESSON, Palette.Test, Palette.Subjects[2], subtitle = "sala 16 · Anna Kamińska", status = BlockStatus.SUBSTITUTION, tag = stringResource(R.string.substitution), modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Historia", BlockKind.LESSON, Palette.Test, Palette.Subjects[6], subtitle = "sala 22", status = BlockStatus.CANCELLED, tag = stringResource(R.string.cancelled), modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Geografia", BlockKind.LESSON, Palette.Custom, Palette.Subjects[9], subtitle = "Przynieść: atlas", important = true, labelColors = listOf(Palette.Homework, Palette.Watch), modifier = Modifier.fillMaxWidth())
        ScheduleBlock("Nauka do fizyki", BlockKind.CUSTOM, Palette.Custom, Palette.Custom, subtitle = "16:00–17:00", modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple("Mat", BlockKind.TEST, Palette.Test), Triple("Pol", BlockKind.LESSON, Palette.Test),
                Triple("Fiz", BlockKind.QUIZ, Palette.Quiz), Triple("Ang", BlockKind.LESSON, Palette.Test), Triple("WF", BlockKind.LESSON, Palette.Test),
            ).forEachIndexed { i, (name, kind, color) ->
                ScheduleBlock(name, kind, color, Palette.Subjects[i + 2], compact = true, modifier = Modifier.weight(1f).height(44.dp))
            }
        }

        SectionTitle(stringResource(R.string.style_levels))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WarningLevel.entries.reversed().forEach { Tag(stringResource(it.word), levelColor(it)) }
        }

        SectionTitle(stringResource(R.string.style_eclipse))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            EclipseDisc(0f, size = 104.dp, showProgress = true)
            EclipseDisc(0.5f, size = 104.dp) { DiscLabel("7", "dni") }
            EclipseDisc(0.93f, size = 104.dp) { DiscLabel("5", "godz.") }
        }
        Text(stringResource(R.string.style_eclipse_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun AccentPicker(current: Long, onPick: (Long) -> Unit) {
    val c = Eclipse.colors
    var hex by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Palette.Accents.forEach { (name, color) ->
            val selected = color.toArgb().toLong() and 0xFFFFFFFF == current and 0xFFFFFFFF
            Box(
                Modifier
                    .size(if (selected) 40.dp else 34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape)
                    .clickable { onPick(color.toArgb().toLong() and 0xFFFFFFFF) }
                    .semantics { contentDescription = name },
            )
        }
    }
    OutlinedTextField(
        value = hex,
        onValueChange = { v ->
            hex = v
            parseHex(v)?.let(onPick)
        },
        label = { Text(stringResource(R.string.accent_custom)) },
        singleLine = true,
        modifier = Modifier.width(220.dp),
    )
}

/** „#8B5CF6” albo „8B5CF6” → ARGB; null, gdy to nie jest kolor. */
fun parseHex(text: String): Long? {
    val v = text.trim().removePrefix("#")
    if (v.length != 6 || v.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
    return 0xFF000000 or v.toLong(16)
}

/** Szkło nad kolorową treścią — widać rozmycie (albo matową wersję przy „Mniej przezroczystości”). */
@Composable
private fun GlassSample() {
    val glass = rememberGlassState()
    val c = Eclipse.colors
    Box(Modifier.fillMaxWidth().height(170.dp).clip(pl.eclipse.app.ui.components.PanelShape)) {
        Box(Modifier.matchParentSizeCompat().glassSource(glass)) {
            EclipseBackground()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Palette.Test, Palette.Homework, Palette.DayOff, c.accent).forEachIndexed { i, color ->
                    Box(
                        Modifier.padding(start = (i * 28).dp).width(180.dp).height(26.dp).clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.3f)))),
                    )
                }
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(12.dp).width(190.dp).height(130.dp)
                .glass(glass, pl.eclipse.app.ui.components.PanelShape).padding(14.dp),
        ) {
            Text(stringResource(R.string.style_glass_title), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(stringResource(R.string.style_glass_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

private fun Modifier.matchParentSizeCompat() = this.fillMaxWidth().height(170.dp)

private val WarningLevel.word
    get() = when (this) {
        WarningLevel.CRITICAL -> R.string.level_critical
        WarningLevel.WARNING -> R.string.level_warning
        WarningLevel.WATCH -> R.string.level_watch
    }
