package pl.eclipse.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.eclipse.app.R
import pl.eclipse.app.ui.theme.Eclipse
import pl.eclipse.app.ui.theme.Palette
import pl.eclipse.app.ui.theme.TabularNumbers
import pl.eclipse.app.ui.theme.gradeColor

enum class BlockKind(@param:DrawableRes val icon: Int?) {
    LESSON(null), TEST(R.drawable.ic_fact_check), QUIZ(R.drawable.ic_bolt), HOMEWORK(R.drawable.ic_menu_book),
    EVENT(R.drawable.ic_flag), DAY_OFF(R.drawable.ic_wb_sunny), CUSTOM(R.drawable.ic_star),
}

enum class BlockStatus { NORMAL, SUBSTITUTION, CANCELLED, CHANGED, REMOVED }

/**
 * Blok wydarzenia lub lekcji (SPEC 11.5): pasek koloru typu z lewej, gradient od koloru typu do koloru przedmiotu,
 * poświata przy sprawdzianie i kartkówce, przerywana ramka przy zastępstwie, 50% i przekreślenie przy odwołaniu.
 * Kolor nigdy nie jest jedynym nośnikiem informacji — zawsze jest ikona albo słowo.
 */
@Composable
fun ScheduleBlock(
    title: String,
    kind: BlockKind,
    typeColor: Color,
    subjectColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: BlockStatus = BlockStatus.NORMAL,
    tag: String? = null,
    important: Boolean = false,
    labelColors: List<Color> = emptyList(),
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Eclipse.colors
    val shape = RoundedCornerShape(if (compact) 6.dp else 10.dp)
    val isLesson = kind == BlockKind.LESSON
    val bar = if (isLesson && !important) c.text.copy(alpha = 0.35f) else typeColor
    val glow = when (kind) {
        BlockKind.TEST -> 10.dp
        BlockKind.QUIZ -> 5.dp
        else -> 0.dp
    }
    val dimmed = status == BlockStatus.CANCELLED || status == BlockStatus.REMOVED
    Box(
        modifier
            .alpha(if (dimmed) 0.5f else 1f)
            .then(if (glow > 0.dp && !dimmed) Modifier.shadow(glow, shape, ambientColor = typeColor, spotColor = typeColor) else Modifier)
            .clip(shape)
            // pełne tło pod gradientem — inaczej cień poświaty prześwituje przez blok
            .background(if (c.isDark) Color(0xFF161B42) else Color(0xFFFBFCFE))
            .background(
                Brush.horizontalGradient(
                    0f to (if (isLesson) c.lessonTint else typeColor.copy(alpha = 0.18f)),
                    0.45f to Color.Transparent,
                    1f to subjectColor.copy(alpha = 0.40f),
                ),
            )
            .then(
                when {
                    status == BlockStatus.SUBSTITUTION -> Modifier.drawBehind {
                        drawRoundRect(
                            color = c.text.copy(alpha = 0.7f),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            cornerRadius = CornerRadius(shape.topStart.toPx(size, this)),
                        )
                    }
                    important -> Modifier.border(1.5.dp, typeColor.copy(alpha = 0.9f), shape)
                    else -> Modifier.border(1.dp, c.cardBorder, shape)
                },
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(if (compact) 3.dp else 4.dp).fillMaxHeight().background(bar).align(Alignment.CenterStart))
        Column(
            Modifier.padding(start = if (compact) 6.dp else 12.dp, end = if (compact) 3.dp else 10.dp, top = if (compact) 3.dp else 8.dp, bottom = if (compact) 3.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                kind.icon?.let { Icon(painterResource(it), null, Modifier.size(if (compact) 12.dp else 16.dp), tint = c.readable(typeColor)) }
                Text(
                    title,
                    style = (if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall).copy(
                        fontWeight = if (kind == BlockKind.TEST || kind == BlockKind.QUIZ) FontWeight.Bold else FontWeight.SemiBold,
                        textDecoration = if (dimmed) TextDecoration.LineThrough else null,
                    ),
                    color = c.text,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f, fill = false),
                )
                labelColors.take(3).forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(it)) }
            }
            if (!compact) subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2) }
            tag?.let { Tag(it, if (status == BlockStatus.NORMAL || status == BlockStatus.CHANGED) typeColor else c.text, compact = compact) }
        }
    }
}

/** Mały znacznik słowny, np. „za 3 dni”, „zastępstwo”, „odwołana”. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = Eclipse.colors
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
        color = c.readable(color),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = if (compact) 3.dp else 6.dp, vertical = 1.dp),
    )
}

/** Kafelek oceny: kolor według procentu, punktowe jako „17/20”, kropka przy nowych (SPEC 12.4). */
@Composable
fun GradeTile(symbol: String, grade: Int?, modifier: Modifier = Modifier, isNew: Boolean = false) {
    val c = Eclipse.colors
    val color = grade?.let(::gradeColor) ?: c.textSecondary
    Box(modifier) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleSmall.merge(TabularNumbers),
            color = c.readable(color),
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = if (grade == null) 0.10f else 0.18f))
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        if (isNew) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(CircleShape).background(c.accent))
    }
}

/** Pasek 0–100% z kreskami progów i znacznikiem średniej (SPEC 12.4). */
@Composable
fun PercentBar(average: Double?, thresholds: Collection<Double>, modifier: Modifier = Modifier, grade: Int? = null) {
    val c = Eclipse.colors
    val fill = grade?.let(::gradeColor) ?: c.accent
    Box(modifier.fillMaxWidth().height(14.dp)) {
        Spacer(
            Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center).clip(RoundedCornerShape(3.dp))
                .background(c.text.copy(alpha = 0.10f))
                .drawBehind {
                    val value = ((average ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
                    drawRect(fill, size = size.copy(width = size.width * value))
                },
        )
        Spacer(
            Modifier.fillMaxWidth().height(14.dp).drawBehind {
                thresholds.forEach { t ->
                    val x = (t / 100).toFloat() * size.width
                    drawLine(c.text.copy(alpha = 0.45f), androidx.compose.ui.geometry.Offset(x, 1.dp.toPx()), androidx.compose.ui.geometry.Offset(x, size.height - 1.dp.toPx()), 1.dp.toPx())
                }
                average?.let {
                    val x = (it / 100).toFloat().coerceIn(0f, 1f) * size.width
                    drawCircle(c.text, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                    drawCircle(fill, 3.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, size.height / 2))
                }
            },
        )
    }
}

/** Plakietka z liczbą (np. nowe oceny, ostrzeżenia krytyczne). */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, color: Color = Palette.Critical) {
    if (count <= 0) return
    Text(
        count.toString(),
        style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
        color = Color.White,
        modifier = modifier.clip(CircleShape).background(color).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}
