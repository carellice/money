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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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

/** True when the user picked the skeuomorphic style in Impostazioni. */
val LocalSkeuo = staticCompositionLocalOf { false }

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

// Skeuomorphic palette: dark felt and worn leather at night, linen and cream paper by day,
// with a brass accent that sits well next to the stitched leather.
private val SkeuoDarkColors = MoneyColors(
    isDark = true,
    bg = Color(0xFF2B2420),
    surface = Color(0xFF3A312A),
    surfaceHigh = Color(0xFF241E1A),
    ink = Color(0xFFF5EBDA),
    inkSoft = Color(0xFFC9B9A3),
    inkFaint = Color(0xFF8C7C69),
    line = Color(0x33000000),
    income = Color(0xFF6FC27A),
    expense = Color(0xFFE5715C),
    accent = Color(0xFFE0B055),
    incomeText = Color(0xFF8FDB98),
    expenseText = Color(0xFFFF9682),
    accentText = Color(0xFFF0C878),
)

private val SkeuoLightColors = MoneyColors(
    isDark = false,
    bg = Color(0xFFE6DDCB),
    surface = Color(0xFFFBF7EE),
    surfaceHigh = Color(0xFFE9E0CE),
    ink = Color(0xFF33261A),
    inkSoft = Color(0xFF65543F),
    inkFaint = Color(0xFF9A8A74),
    line = Color(0x1F3A2A1A),
    income = Color(0xFF6FC27A),
    expense = Color(0xFFE5715C),
    accent = Color(0xFFE0B055),
    incomeText = Color(0xFF2F7A3B),
    expenseText = Color(0xFFA9392A),
    accentText = Color(0xFF8A5A12),
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

/** Bold serif for titles and figures of the skeuomorphic style, like a ledger printed on good paper. */
val SerifFamily = FontFamily.Serif

@Immutable
class MoneyType(dark: Boolean = false, skeuo: Boolean = false) {
    private val numbers = "tnum, lnum"
    // Skeuomorphic letterpress: a light edge below on paper, a dark one above on felt.
    private val press = when {
        !skeuo -> null
        dark -> Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, -1.5f), 0f)
        else -> Shadow(Color.White.copy(alpha = 0.85f), Offset(0f, 1.6f), 0f)
    }
    private val head = if (skeuo) SerifFamily else DisplayFamily
    private val bold = if (skeuo) FontWeight.Bold else FontWeight.SemiBold
    // Hero sizes are always set per screen, so the global scale does not apply.
    val hero = TextStyle(fontFamily = head, fontWeight = if (skeuo) FontWeight.Bold else FontWeight.ExtraBold, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = if (skeuo) (-0.02).em else (-0.04).em, fontFeatureSettings = numbers, shadow = press)
    val display = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = scaled(if (skeuo) 36f else 38f), lineHeight = scaled(40f), letterSpacing = if (skeuo) (-0.01).em else (-0.03).em, fontFeatureSettings = numbers, shadow = press)
    val title = TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = scaled(if (skeuo) 23f else 24f), lineHeight = scaled(28f), letterSpacing = if (skeuo) 0.em else (-0.02).em, shadow = press)
    val titleSmall = TextStyle(fontFamily = head, fontWeight = bold, fontSize = scaled(18f), lineHeight = scaled(22f), letterSpacing = if (skeuo) 0.em else (-0.01).em, shadow = press)
    val body = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = scaled(15f), lineHeight = scaled(21f), shadow = press)
    val bodyStrong = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(15f), lineHeight = scaled(21f), shadow = press)
    val label = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(13f), lineHeight = scaled(17f), shadow = press)
    val caption = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = scaled(12f), lineHeight = scaled(16f), shadow = press)
    val amount = TextStyle(fontFamily = head, fontWeight = bold, fontSize = scaled(16f), lineHeight = scaled(20f), fontFeatureSettings = numbers, shadow = press)
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
fun MoneyTheme(dark: Boolean, accent: Long = Palette.LILAC, skeuo: Boolean = false, content: @Composable () -> Unit) {
    val base = when {
        skeuo -> if (dark) SkeuoDarkColors else SkeuoLightColors
        else -> if (dark) DarkColors else LightColors
    }
    val colors = remember(dark, accent, skeuo) {
        // The default lilac keeps its hand-tuned text shade.
        if (accent == Palette.LILAC) base
        else base.copy(accent = Color(accent), accentText = accentTextFor(Color(accent), dark))
    }
    val type = remember(dark, skeuo) { MoneyType(dark, skeuo) }
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
        displayLarge = baseType.displayLarge.scaled(if (skeuo) SerifFamily else DisplayFamily),
        headlineLarge = baseType.headlineLarge.scaled(if (skeuo) SerifFamily else DisplayFamily),
        headlineMedium = baseType.headlineMedium.scaled(if (skeuo) SerifFamily else DisplayFamily),
        headlineSmall = baseType.headlineSmall.scaled(if (skeuo) SerifFamily else DisplayFamily),
        titleLarge = baseType.titleLarge.scaled(if (skeuo) SerifFamily else DisplayFamily),
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
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalBlobTime provides time, LocalSkeuo provides skeuo) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** How a stored accent is painted: the default lilac turns brass in the skeuomorphic style. */
@Composable
fun accentShown(): (Long) -> Long = if (LocalSkeuo.current) { c -> if (c == Palette.LILAC) 0xFFE0B055 else c } else { c -> c }
