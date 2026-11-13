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
val DarkInk = Color(0xFF15121F)

/** True when the user picked the skeuomorphic style in Impostazioni. */
val LocalSkeuo = staticCompositionLocalOf { false }

/** True when the user picked the Modern UI (Windows Phone) style in Impostazioni. */
val LocalMetro = staticCompositionLocalOf { false }

/** True when the user picked the Nothing style (monochrome, dot-matrix) in Impostazioni. */
val LocalNothing = staticCompositionLocalOf { false }

/** Nothing: [Ink] inside a flat card, where text sits on the surface instead of a colour fill. */
val LocalNothingInk = staticCompositionLocalOf<Color?> { null }

/** Ink on top of colour fills: dark on the pastels, white on the solid Modern UI tiles, paper on Nothing's ink-coloured fills. */
val Ink: Color @Composable get() = when {
    LocalNothing.current -> LocalNothingInk.current ?: LocalColors.current.bg
    LocalMetro.current -> Color.White
    else -> DarkInk
}

/** Solid, saturated version of a pastel, for Modern UI tiles that carry white text. */
fun Color.solid(): Color {
    if (alpha == 0f) return this
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    // Already a solid tile colour (the Windows Phone accents): keep it as it is.
    if (hsl[1] >= 0.6f && hsl[2] in 0.25f..0.55f) return this
    // Grey-ish pastels keep a hint of their hue instead of turning neon.
    hsl[1] = if (hsl[1] < 0.15f) hsl[1] else maxOf(hsl[1], 0.72f).coerceAtMost(0.9f)
    hsl[2] = 0.44f
    return Color(ColorUtils.HSLToColor(hsl)).copy(alpha = alpha)
}

/** [solid] in the Modern UI style, unchanged otherwise. */
@Composable
fun Color.tile(): Color = if (LocalMetro.current) solid() else this

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

// Modern UI palette: true black or white, flat greys, and the Windows Phone accents
// (cyan, green, red) that carry white text.
private val MetroDarkColors = MoneyColors(
    isDark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF1F1F1F),
    surfaceHigh = Color(0xFF333333),
    ink = Color(0xFFFFFFFF),
    inkSoft = Color(0xFFA6A6A6),
    inkFaint = Color(0xFF707070),
    line = Color(0x33FFFFFF),
    income = Color(0xFF60A917),
    expense = Color(0xFFE51400),
    accent = Color(0xFF1BA1E2),
    incomeText = Color(0xFF8CCB3A),
    expenseText = Color(0xFFFF5A3C),
    accentText = Color(0xFF1BA1E2),
)

private val MetroLightColors = MoneyColors(
    isDark = false,
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F0),
    surfaceHigh = Color(0xFFDDDDDD),
    ink = Color(0xFF000000),
    inkSoft = Color(0xFF5E5E5E),
    inkFaint = Color(0xFF999999),
    line = Color(0x26000000),
    income = Color(0xFF60A917),
    expense = Color(0xFFE51400),
    accent = Color(0xFF1BA1E2),
    incomeText = Color(0xFF3F7A0B),
    expenseText = Color(0xFFC21100),
    accentText = Color(0xFF0A7FBF),
)

// Nothing palette: OLED black and white data by night, printed manual (off-white paper, black ink) by day.
// The accent is the display ink itself; red and green only encode money going out and in.
private val NothingDarkColors = MoneyColors(
    isDark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceHigh = Color(0xFF1A1A1A),
    ink = Color(0xFFE8E8E8),
    inkSoft = Color(0xFF999999),
    inkFaint = Color(0xFF666666),
    line = Color(0xFF222222),
    income = Color(0xFF4A9E5C),
    expense = Color(0xFFD71921),
    accent = Color(0xFFFFFFFF),
    incomeText = Color(0xFF5DB870),
    expenseText = Color(0xFFE5343C),
    accentText = Color(0xFFFFFFFF),
)

private val NothingLightColors = MoneyColors(
    isDark = false,
    bg = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F0F0),
    ink = Color(0xFF1A1A1A),
    inkSoft = Color(0xFF666666),
    inkFaint = Color(0xFF999999),
    line = Color(0xFFE8E8E8),
    income = Color(0xFF4A9E5C),
    expense = Color(0xFFD71921),
    accent = Color(0xFF000000),
    incomeText = Color(0xFF3A7F49),
    expenseText = Color(0xFFD71921),
    accentText = Color(0xFF000000),
)

/** The default lilac stands in for Windows Phone cyan in the Modern UI style. */
const val MetroCyan = 0xFF1BA1E2

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

@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: Int) = Font(R.font.space_grotesk, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun doto(weight: Int) = Font(R.font.doto, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Nothing type: Space Grotesk for UI, Space Mono for labels and figures, Doto dot-matrix for the hero moments. */
val GroteskFamily = FontFamily(grotesk(300), grotesk(400), grotesk(500), grotesk(700))
val MonoFamily = FontFamily(Font(R.font.space_mono, FontWeight.Normal), Font(R.font.space_mono_bold, FontWeight.Bold))
val DotFamily = FontFamily(doto(500), doto(700), doto(800))

/** Clean grotesque in light weights, the closest system face to Segoe WP. */
val MetroFamily = FontFamily.SansSerif

@Immutable
class MoneyType(dark: Boolean = false, skeuo: Boolean = false, metro: Boolean = false, nothing: Boolean = false) {
    private val numbers = "tnum, lnum"
    private val m = metro
    // Skeuomorphic letterpress: a light edge below on paper, a dark one above on felt.
    private val press = when {
        !skeuo -> null
        dark -> Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, -1.5f), 0f)
        else -> Shadow(Color.White.copy(alpha = 0.85f), Offset(0f, 1.6f), 0f)
    }
    private val head = if (skeuo) SerifFamily else DisplayFamily
    private val bold = if (skeuo) FontWeight.Bold else FontWeight.SemiBold
    // Hero sizes are always set per screen, so the global scale does not apply.
    val hero = if (nothing) TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-0.02).em, fontFeatureSettings = numbers) else if (m) TextStyle(fontFamily = MetroFamily, fontWeight = FontWeight.Light, fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-0.02).em, fontFeatureSettings = numbers) else TextStyle(fontFamily = head, fontWeight = if (skeuo) FontWeight.Bold else FontWeight.ExtraBold, fontSize = 60.sp, lineHeight = 60.sp, letterSpacing = if (skeuo) (-0.02).em else (-0.04).em, fontFeatureSettings = numbers, shadow = press)
    val display = if (nothing) TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Light, fontSize = scaled(34f), lineHeight = scaled(38f), letterSpacing = (-0.02).em, fontFeatureSettings = numbers) else if (m) TextStyle(fontFamily = MetroFamily, fontWeight = FontWeight.Light, fontSize = scaled(38f), lineHeight = scaled(42f), letterSpacing = (-0.01).em, fontFeatureSettings = numbers) else TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = scaled(if (skeuo) 36f else 38f), lineHeight = scaled(40f), letterSpacing = if (skeuo) (-0.01).em else (-0.03).em, fontFeatureSettings = numbers, shadow = press)
    val title = if (nothing) TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Normal, fontSize = scaled(22f), lineHeight = scaled(27f), letterSpacing = (-0.01).em) else if (m) TextStyle(fontFamily = MetroFamily, fontWeight = FontWeight.Light, fontSize = scaled(26f), lineHeight = scaled(30f)) else TextStyle(fontFamily = head, fontWeight = FontWeight.Bold, fontSize = scaled(if (skeuo) 23f else 24f), lineHeight = scaled(28f), letterSpacing = if (skeuo) 0.em else (-0.02).em, shadow = press)
    val titleSmall = if (nothing) TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Medium, fontSize = scaled(17f), lineHeight = scaled(22f)) else if (m) TextStyle(fontFamily = MetroFamily, fontWeight = FontWeight.Normal, fontSize = scaled(19f), lineHeight = scaled(23f)) else TextStyle(fontFamily = head, fontWeight = bold, fontSize = scaled(18f), lineHeight = scaled(22f), letterSpacing = if (skeuo) 0.em else (-0.01).em, shadow = press)
    val body = if (nothing) TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Normal, fontSize = scaled(15f), lineHeight = scaled(22f)) else TextStyle(fontFamily = if (m) MetroFamily else BodyFamily, fontWeight = FontWeight.Normal, fontSize = scaled(15f), lineHeight = scaled(21f), shadow = press)
    val bodyStrong = if (nothing) TextStyle(fontFamily = GroteskFamily, fontWeight = FontWeight.Medium, fontSize = scaled(15f), lineHeight = scaled(22f)) else TextStyle(fontFamily = if (m) MetroFamily else BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(15f), lineHeight = scaled(21f), shadow = press)
    val label = if (nothing) TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = scaled(12f), lineHeight = scaled(16f), letterSpacing = 0.06.em) else TextStyle(fontFamily = if (m) MetroFamily else BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = scaled(13f), lineHeight = scaled(17f), shadow = press)
    val caption = if (nothing) TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = scaled(11f), lineHeight = scaled(15f), letterSpacing = 0.06.em) else TextStyle(fontFamily = if (m) MetroFamily else BodyFamily, fontWeight = FontWeight.Medium, fontSize = scaled(12f), lineHeight = scaled(16f), shadow = press)
    val amount = if (nothing) TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = scaled(15f), lineHeight = scaled(20f), fontFeatureSettings = numbers) else TextStyle(fontFamily = if (m) MetroFamily else head, fontWeight = if (m) FontWeight.Normal else bold, fontSize = scaled(16f), lineHeight = scaled(20f), fontFeatureSettings = numbers, shadow = press)
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
fun MoneyTheme(dark: Boolean, accent: Long = Palette.LILAC, skeuo: Boolean = false, metro: Boolean = false, nothing: Boolean = false, content: @Composable () -> Unit) {
    val base = when {
        nothing -> if (dark) NothingDarkColors else NothingLightColors
        metro -> if (dark) MetroDarkColors else MetroLightColors
        skeuo -> if (dark) SkeuoDarkColors else SkeuoLightColors
        else -> if (dark) DarkColors else LightColors
    }
    val colors = remember(dark, accent, skeuo, metro, nothing) {
        when {
            // Nothing is monochrome: the chosen colour does not tint it.
            nothing -> base
            // The default lilac keeps its hand-tuned text shade.
            accent == Palette.LILAC -> base
            // Modern UI accents are solid: the same colour fills tiles and colours text.
            metro -> Color(accent).solid().let { base.copy(accent = it, accentText = if (dark) it.lighten(0.15f) else it.darken(0.1f)) }
            else -> base.copy(accent = Color(accent), accentText = accentTextFor(Color(accent), dark))
        }
    }
    val type = remember(dark, skeuo, metro, nothing) { MoneyType(dark, skeuo, metro, nothing) }
    val bodyFamily = if (nothing) GroteskFamily else if (metro) MetroFamily else BodyFamily
    val headFamily = when {
        nothing -> GroteskFamily
        metro -> MetroFamily
        skeuo -> SerifFamily
        else -> DisplayFamily
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = if (nothing) Color.Black else if (metro) Color.White else DarkInk, secondary = colors.income, onSecondary = if (metro || nothing) Color.White else DarkInk,
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
        displayLarge = baseType.displayLarge.scaled(headFamily),
        headlineLarge = baseType.headlineLarge.scaled(headFamily),
        headlineMedium = baseType.headlineMedium.scaled(headFamily),
        headlineSmall = baseType.headlineSmall.scaled(headFamily),
        titleLarge = baseType.titleLarge.scaled(headFamily),
        titleMedium = baseType.titleMedium.scaled(bodyFamily),
        titleSmall = baseType.titleSmall.scaled(bodyFamily),
        bodyLarge = baseType.bodyLarge.scaled(bodyFamily),
        bodyMedium = baseType.bodyMedium.scaled(bodyFamily),
        bodySmall = baseType.bodySmall.scaled(bodyFamily),
        labelLarge = baseType.labelLarge.scaled(bodyFamily),
        labelMedium = baseType.labelMedium.scaled(bodyFamily),
        labelSmall = baseType.labelSmall.scaled(bodyFamily),
    )
    val time = produceState(0f) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { value = (it - start) / 1000f }
    }
    CompositionLocalProvider(LocalColors provides colors, LocalType provides type, LocalBlobTime provides time, LocalSkeuo provides skeuo, LocalMetro provides metro, LocalNothing provides nothing) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

/** How a stored accent is painted: the default lilac turns brass in the skeuomorphic style. */
@Composable
fun accentShown(): (Long) -> Long = when {
    LocalNothing.current -> { c -> c }
    LocalMetro.current -> { c -> if (c == Palette.LILAC) MetroCyan else Color(c).solid().toArgb().toLong() and 0xFFFFFFFFL }
    LocalSkeuo.current -> { c -> if (c == Palette.LILAC) 0xFFE0B055 else c }
    else -> { c -> c }
}
