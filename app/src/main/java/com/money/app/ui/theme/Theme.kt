package com.money.app.ui.theme

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.money.app.data.Palette
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.money.app.R
import com.money.app.data.ThemeMode

/** Dark ink used on top of every pastel fill, in both themes. */
val Ink = Color(0xFF15121F)

@Immutable
data class MoneyColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    /** Pastel fills. */
    val income: Color,
    val expense: Color,
    val accent: Color,
    /** Same hues, readable as text on [bg]. */
    val incomeText: Color,
    val expenseText: Color,
    val accentText: Color,
)

private val DarkColors = MoneyColors(
    isDark = true,
    bg = Color(0xFF15121F),
    surface = Color(0xFF1E1A2B),
    surfaceHigh = Color(0xFF2A2540),
    ink = Color(0xFFF4EFFA),
    inkSoft = Color(0xFFB9B2CC),
    inkFaint = Color(0xFF6F6888),
    line = Color(0x1FFFFFFF),
    income = Color(0xFF8FE3C0),
    expense = Color(0xFFFF8A7A),
    accent = Color(0xFFB8A6FF),
    incomeText = Color(0xFF8FE3C0),
    expenseText = Color(0xFFFF9C8E),
    accentText = Color(0xFFC6B8FF),
)

private val LightColors = MoneyColors(
    isDark = false,
    bg = Color(0xFFF6F1EA),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFECE4F4),
    ink = Color(0xFF15121F),
    inkSoft = Color(0xFF433D55),
    inkFaint = Color(0xFF746D86),
    line = Color(0x14000000),
    income = Color(0xFF8FE3C0),
    expense = Color(0xFFFF8A7A),
    accent = Color(0xFFB8A6FF),
    incomeText = Color(0xFF17714F),
    expenseText = Color(0xFFB8382A),
    accentText = Color(0xFF5538CC),
)

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Float) = Font(
    resId = R.font.bricolage,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

val DisplayFamily = FontFamily(bricolage(500, 72f), bricolage(600, 72f), bricolage(700, 72f), bricolage(800, 72f))
val BodyFamily = FontFamily(bricolage(400, 14f), bricolage(500, 14f), bricolage(600, 14f), bricolage(700, 14f))

/** Global text scale: the base sizes below read too small on most phones. */
private const val TextScale = 1.1f

private fun scaled(value: Float) = (value * TextScale).sp

@Immutable
class MoneyType {
    private val numbers = "tnum, lnum"
    // Hero sizes are always set per screen, so the global scale does not apply.
    val hero = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = (-0.04).em, fontFeatureSettings = numbers)
    val display = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = scaled(38f), lineHeight = scaled(40f), letterSpacing = (-0.03).em, fontFeatureSettings = numbers)
    val title = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.Bold, fontSize = scaled(24f), lineHeight = scaled(28f), letterSpacing = (-0.02).em)
    val titleSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(18f), lineHeight = scaled(22f), letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = scaled(15f), lineHeight = scaled(21f))
    val bodyStrong = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(15f), lineHeight = scaled(21f))
    val label = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(13f), lineHeight = scaled(17f))
    val caption = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = scaled(12f), lineHeight = scaled(16f))
    val amount = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(16f), lineHeight = scaled(20f), fontFeatureSettings = numbers)
}

private val LocalColors = staticCompositionLocalOf { DarkColors }
private val LocalType = staticCompositionLocalOf { MoneyType() }

/** Seconds since composition, shared by every animated blob so they move in sync. */
val LocalBlobTime = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

object Ui {
    val colors: MoneyColors @Composable get() = LocalColors.current
    val type: MoneyType @Composable get() = LocalType.current
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Readable text variant of a pastel accent: lighter on dark backgrounds, deeper on light ones. */
private fun accentTextFor(accent: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    hsl[1] = maxOf(hsl[1], 0.6f)
    hsl[2] = if (dark) 0.8f else 0.36f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun TextStyle.scaled(family: FontFamily) =
    copy(fontFamily = family, fontSize = fontSize * TextScale, lineHeight = lineHeight * TextScale)

@Composable
fun MoneyTheme(dark: Boolean, accent: Long = Palette.LILAC, content: @Composable () -> Unit) {
    val base = if (dark) DarkColors else LightColors
    val colors = remember(dark, accent) {
        // The default lilac keeps its hand-tuned text shade.
        if (accent == Palette.LILAC) base
        else base.copy(accent = Color(accent), accentText = accentTextFor(Color(accent), dark))
    }
    val type = remember { MoneyType() }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = Ink, secondary = colors.income, onSecondary = Ink,
            background = colors.bg, onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.inkSoft,
            surfaceContainerHigh = colors.surfaceHigh, surfaceContainer = colors.surface,
            surfaceContainerHighest = colors.surfaceHigh, outline = colors.inkFaint, error = colors.expense,
        )
    } else {
        lightColorScheme(
            primary = colors.accentText, onPrimary = Color.White, secondary = colors.incomeText, onSecondary = Color.White,
            background = colors.bg, onBackground = colors.ink, surface = colors.surface, onSurface = colors.ink,
            surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.inkSoft,
            surfaceContainerHigh = colors.surface, surfaceContainer = colors.surface,
            surfaceContainerHighest = colors.surfaceHigh, outline = colors.inkFaint, error = colors.expenseText,
        )
    }
    val baseType = Typography()
    val typography = Typography(
        displayLarge = baseType.displayLarge.scaled(DisplayFamily),
        headlineLarge = baseType.headlineLarge.scaled(DisplayFamily),
        headlineMedium = baseType.headlineMedium.scaled(DisplayFamily),
        headlineSmall = baseType.headlineSmall.scaled(DisplayFamily),
        titleLarge = baseType.titleLarge.scaled(DisplayFamily),
        titleMedium = baseType.titleMedium.scaled(BodyFamily),
        titleSmall = baseType.titleSmall.scaled(BodyFamily),
        bodyLarge = baseType.bodyLarge.scaled(BodyFamily),
        bodyMedium = baseType.bodyMedium.scaled(BodyFamily),
        bodySmall = baseType.bodySmall.scaled(BodyFamily),
        labelLarge = baseType.labelLarge.scaled(BodyFamily),
        labelMedium = baseType.labelMedium.scaled(BodyFamily),
        labelSmall = baseType.labelSmall.scaled(BodyFamily),
    )
    val time = produceState(0f) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalBlobTime provides time) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
