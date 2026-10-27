package com.eirahoutmoss.nexsense.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eirahoutmoss.nexsense.core.layers.LayerKind
import com.eirahoutmoss.nexsense.settings.ThemeMode

/** Konsept görselindeki tasarım dili: koyu lacivert/antrasit zemin, cyan/mavi vurgular. */
object NexColors {
    val Navy = Color(0xFF0A1220)
    val Surface = Color(0xFF111B2E)
    val SurfaceHigh = Color(0xFF18243B)
    val Outline = Color(0xFF2A3A57)
    val Cyan = Color(0xFF34D1F5)
    val Blue = Color(0xFF2F8CF0)
    val TextHigh = Color(0xFFE6EEF8)
    val TextMid = Color(0xFF9FB0C8)
    val Demo = Color(0xFFFFB020)
    val Danger = Color(0xFFFF5A6A)
    val Unmeasured = Color(0xFF6B7A93)
}

/** Katman renkleri: sıcaklık turuncu-kırmızı, manyetik mavi-mor, akustik mor, titreşim yeşil. */
object LayerColors {
    data class Ramp(val low: Color, val high: Color) {
        fun at(t: Double): Color = lerp(low, high, t.toFloat().coerceIn(0f, 1f))
    }

    fun ramp(kind: LayerKind): Ramp = when (kind) {
        LayerKind.MAGNETIC -> Ramp(Color(0xFF2F6BFF), Color(0xFFB04DFF))
        LayerKind.MOTION -> Ramp(Color(0xFF1C7A4A), Color(0xFF5CFF8A))
        LayerKind.ACOUSTIC -> Ramp(Color(0xFF5B2A9E), Color(0xFFE07BFF))
        LayerKind.SPL -> Ramp(Color(0xFF6A3FBF), Color(0xFFFF7BD5))
        LayerKind.REVERB -> Ramp(Color(0xFF3A2D8F), Color(0xFF9AE6FF))
        LayerKind.SCAN -> Ramp(Color(0xFF9FB0C8), Color(0xFF9FB0C8))
        LayerKind.TEMPERATURE -> Ramp(Color(0xFFFFB347), Color(0xFFFF3B2F))
        LayerKind.HUMIDITY -> Ramp(Color(0xFF7FD6FF), Color(0xFF1F5FD6))
        LayerKind.GEOMETRY -> Ramp(NexColors.Cyan, NexColors.Cyan)
        LayerKind.NOTES -> Ramp(Color(0xFFFFD84D), Color(0xFFFFD84D))
    }

    fun swatch(kind: LayerKind): Color = ramp(kind).at(0.7)
}

private val Dark = darkColorScheme(
    primary = NexColors.Cyan,
    onPrimary = NexColors.Navy,
    secondary = NexColors.Blue,
    onSecondary = Color.White,
    background = NexColors.Navy,
    onBackground = NexColors.TextHigh,
    surface = NexColors.Surface,
    onSurface = NexColors.TextHigh,
    surfaceVariant = NexColors.SurfaceHigh,
    onSurfaceVariant = NexColors.TextMid,
    surfaceContainer = NexColors.Surface,
    surfaceContainerHigh = NexColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF1F2D47),
    surfaceContainerLow = Color(0xFF0E1728),
    outline = NexColors.Outline,
    outlineVariant = Color(0xFF223049),
    error = NexColors.Danger,
)

private val Light = lightColorScheme(
    primary = Color(0xFF006B8A),
    onPrimary = Color.White,
    secondary = Color(0xFF1E5FC4),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F1A2B),
    surface = Color.White,
    onSurface = Color(0xFF0F1A2B),
    surfaceVariant = Color(0xFFE3EAF3),
    onSurfaceVariant = Color(0xFF44546B),
    outline = Color(0xFF98A6BA),
    error = Color(0xFFC62838),
)

private val NexTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Sayısal değerler için eşit genişlikli rakamlar (titremez). */
val ValueStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Medium)

@Composable
fun NexSenseTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = NexTypography, content = content)
}
