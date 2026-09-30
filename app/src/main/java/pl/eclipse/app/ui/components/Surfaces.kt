package pl.eclipse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import pl.eclipse.app.ui.theme.Eclipse
import kotlin.random.Random

val CardShape = RoundedCornerShape(12.dp)
val PanelShape = RoundedCornerShape(24.dp)

/** Tło aplikacji (SPEC 11.1): gradient nocy lub świtu, 3 rozmyte plamy światła w kolorze akcentu i delikatny szum. */
@Composable
fun EclipseBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val c = Eclipse.colors
    val noise = remember { noiseBrush() }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(c.backgroundTop, c.backgroundBottom)))
                fun blob(x: Float, y: Float, radius: Float, alpha: Float) {
                    val center = Offset(size.width * x, size.height * y)
                    drawCircle(
                        Brush.radialGradient(listOf(c.accent.copy(alpha = alpha), Color.Transparent), center, radius),
                        radius, center,
                    )
                }
                blob(0.9f, 0.08f, size.width * 0.8f, c.glow)
                blob(0.05f, 0.55f, size.width * 0.6f, c.glow * 0.55f)
                blob(0.75f, 1.0f, size.width * 0.55f, c.glow * 0.4f)
                drawRect(noise, alpha = if (c.isDark) 0.05f else 0.035f)
            },
        content = content,
    )
}

/** Szum w małej, powtarzanej bitmapie — żeby gradient nie tworzył pasów. */
private fun noiseBrush(): ShaderBrush {
    val size = 96
    val random = Random(7)
    val pixels = IntArray(size * size) {
        val v = random.nextInt(256)
        (random.nextInt(40, 120) shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap: ImageBitmap = android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Karta na ekranie: bez rozmycia, półprzezroczyste tło i ramka 1 dp — bez cieni (SPEC 11.1). */
@Composable
fun EclipseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardShape,
    border: Color = Eclipse.colors.cardBorder,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Eclipse.colors
    Column(
        modifier
            .clip(shape)
            .background(c.card)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Główny przycisk: tło w kolorze akcentu, tekst z kontrastem (SPEC 11.2). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Eclipse.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.textSecondary.copy(alpha = 0.18f),
            disabledContentColor = c.textSecondary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Eclipse.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, c.border.copy(alpha = if (c.isDark) 0.28f else 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Nagłówek sekcji: zwykłe zdanie, bez CAPS LOCKA i ozdobników (SPEC 11.6). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = Eclipse.colors.text, modifier = modifier.padding(top = 8.dp))
}

/** Pusty stan, który zachęca do działania (SPEC 11.9). */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(vertical = 12.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Eclipse.colors.textSecondary)
    }
}
