package com.money.app.ui.components

import com.money.app.ui.theme.LocalSkeuo
import com.money.app.ui.theme.LocalMetro
import com.money.app.ui.theme.DarkInk
import com.money.app.ui.theme.tile
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.money.app.ui.theme.CircleShape
import com.money.app.ui.theme.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.money.app.brand.Brands
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.appContainer
import com.money.app.ui.theme.BlobShape
import com.money.app.ui.theme.IconShape
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import com.money.app.ui.theme.Thread
import com.money.app.ui.theme.darken
import com.money.app.ui.theme.gel
import com.money.app.ui.theme.leather
import com.money.app.ui.theme.lighten
import com.money.app.ui.theme.paper
import com.money.app.ui.theme.well
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth

/** Click with a springy press-down and a light haptic tick. */
@Composable
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    if (com.money.app.ui.theme.LocalNothing.current) return nothingPress(enabled, haptic, onLongClick, onClick)
    if (LocalMetro.current) return metroTilt(enabled, haptic, onLongClick, onClick)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "press",
    )
    val feedback = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick?.let {
                {
                    feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
        ) {
            if (haptic) feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

@Composable
fun CategoryBadge(
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    seed: Int = icon.hashCode(),
    animated: Boolean = false,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return Box(
        modifier.size(size).clip(com.money.app.ui.theme.RoundShape).background(Ui.colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], contentDescription = null, tint = when {
                color == Ui.colors.accent -> Ui.colors.ink
                // Pastels need more ink on the light paper.
                Ui.colors.isDark -> color
                else -> androidx.compose.ui.graphics.lerp(color, Color.Black, 0.35f)
            }, modifier = Modifier.size(size * 0.46f))
    }
    Box(
        modifier
            .size(size)
            .blob(color, seed = seed, amplitude = 0.09f, animated = animated),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], contentDescription = null, tint = Ink, modifier = Modifier.size(size * 0.46f))
    }
}

/** Logo of a fixed expense: custom picture, preset brand, or category-style icon. */
@Composable
fun RecurringLogo(
    brandKey: String?,
    imagePath: String?,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = false,
) {
    if (LocalSkeuo.current) return RecurringLogoSkeuo(brandKey = brandKey, imagePath = imagePath, icon = icon, color = color, modifier = modifier, size = size, animated = animated)
    val brand = Brands.find(brandKey)
    val metro = LocalMetro.current
    when {
        imagePath != null -> AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(BlobShape(imagePath.hashCode())),
        )
        brand != null -> Box(
            // Modern UI tiles keep the brand's own colour instead of the solid accent mapping.
            if (metro) modifier.size(size).background(brand.color) else modifier.size(size).blob(brand.color, seed = brand.key.hashCode(), amplitude = 0.08f, animated = animated),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (brand.isLight) DarkInk else Color.White
            val vector = brand.vector
            if (vector != null) {
                Icon(vector, contentDescription = brand.name, tint = tint, modifier = Modifier.size(size * 0.44f))
            } else {
                Text(
                    brand.monogram.orEmpty(),
                    color = tint,
                    style = Ui.type.label.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * if ((brand.monogram?.length ?: 1) > 2) 0.24f else 0.34f).sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        else -> CategoryBadge(icon, color, modifier, size, animated = animated)
    }
}

/** Logo of a fixed expense: custom picture, preset brand, or category-style icon. */
@Composable
private fun RecurringLogoSkeuo(
    brandKey: String?,
    imagePath: String?,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = false,
) {
    val brand = Brands.find(brandKey)
    when {
        imagePath != null -> AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(IconShape),
        )
        brand != null -> Box(
            modifier.size(size).blob(brand.color, seed = brand.key.hashCode(), amplitude = 0.08f, animated = animated),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (brand.isLight) DarkInk else Color.White
            val vector = brand.vector
            if (vector != null) {
                Icon(vector, contentDescription = brand.name, tint = tint, modifier = Modifier.size(size * 0.44f))
            } else {
                Text(
                    brand.monogram.orEmpty(),
                    color = tint,
                    style = Ui.type.label.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (size.value * if ((brand.monogram?.length ?: 1) > 2) 0.24f else 0.34f).sp,
                    ),
                    maxLines = 1,
                )
            }
        }
        else -> CategoryBadge(icon, color, modifier, size, animated = animated)
    }
}

/** "1.240" big plus ",50 €" small. */
@Composable
fun AmountText(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.ink,
    signed: Boolean = false,
    decimalsScale: Float = 0.5f,
) {
    val (int, dec) = Money.parts(cents)
    val sign = when {
        signed && cents > 0 -> "+"
        cents < 0 -> "−"
        else -> ""
    }
    Text(
        buildAnnotatedString {
            append(sign + int)
            withStyle(SpanStyle(fontSize = style.fontSize * decimalsScale, color = color.copy(alpha = 0.7f))) {
                append("$dec €")
            }
        },
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

/** True when the user chose to hide amounts on Home and Movimenti. */
val LocalHideAmounts = compositionLocalOf { false }

/** Shows [content], or soft dots in its place when amounts are hidden; the swap pops with a little scale. */
@Composable
fun Masked(
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    hidden: Boolean = LocalHideAmounts.current,
    content: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = hidden,
        modifier = modifier,
        contentAlignment = Alignment.Center,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f))
        },
        label = "masked",
    ) { isHidden ->
        if (isHidden) Text("••••", style = style, color = color.copy(alpha = 0.75f), maxLines = 1) else content()
    }
}

/** Flips the saved hide-amounts choice. */
@Composable
fun rememberHideAmountsToggle(): () -> Unit {
    val hidden = LocalHideAmounts.current
    val settings = appContainer().settings
    val scope = rememberCoroutineScope()
    return { scope.launch { settings.setHideAmounts(!hidden) } }
}

/** True when the user chose to hide only the account balances (Disponibile). */
val LocalHideBalances = compositionLocalOf { false }

/** Account balances are hidden with every amount, or on their own. */
@Composable
fun balancesHidden(): Boolean = LocalHideAmounts.current || LocalHideBalances.current

/** Flips the saved hide-balances choice. */
@Composable
fun rememberHideBalancesToggle(): () -> Unit {
    val hidden = LocalHideBalances.current
    val settings = appContainer().settings
    val scope = rememberCoroutineScope()
    return { scope.launch { settings.setHideBalances(!hidden) } }
}

/** [text] or dots when amounts are hidden, for amounts inside longer strings. */
@Composable
fun hideable(text: String): String = if (LocalHideAmounts.current) "••••" else text

/** Eye that hides or reveals amounts everywhere they are maskable. */
@Composable
fun HideAmountsButton(modifier: Modifier = Modifier) {
    val hidden = LocalHideAmounts.current
    RoundIconButton(
        if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
        rememberHideAmountsToggle(),
        modifier,
        contentDescription = if (hidden) "Mostra importi" else "Nascondi importi",
    )
}

/** Counts up/down to [cents] whenever it changes. */
@Composable
fun AnimatedAmount(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.ink,
    signed: Boolean = false,
    decimalsScale: Float = 0.5f,
    masked: Boolean = LocalHideAmounts.current,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(cents) {
        anim.animateTo(cents.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    val shown = if (anim.isRunning) anim.value.toLong() else cents
    Masked(style, color, modifier, hidden = masked) { AmountText(shown, style, Modifier, color, signed, decimalsScale) }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.accent,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return PillNothing(text, selected, onClick, modifier, icon, leading)
    if (LocalMetro.current) return PillMetro(text, selected, onClick, modifier, color, icon, leading)
    if (LocalSkeuo.current) return PillSkeuo(text = text, selected = selected, onClick = onClick, modifier = modifier, color = color, icon = icon, leading = leading)
    val c = Ui.colors
    val bg by animateColorAsState(if (selected) color else c.surfaceHigh, label = "pillBg")
    val fg by animateColorAsState(if (selected) Ink else c.ink, label = "pillFg")
    Row(
        modifier
            .bouncyClick(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Ui.type.label, color = fg, maxLines = 1)
    }
}

@Composable
private fun PillSkeuo(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.accent,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Ui.colors
    val fg = if (selected) Ink else c.ink
    Row(
        modifier
            .bouncyClick(onClick = onClick)
            .then(if (selected) Modifier.gel(color, CircleShape) else Modifier.paper(c.surface, CircleShape, elevation = 2.dp, dark = c.isDark))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Ui.type.label.onGel(selected), color = fg, maxLines = 1)
    }
}

/** On a gel fill the letterpress edge turns into a soft white highlight. */
@Composable
fun TextStyle.onGel(gel: Boolean = true): TextStyle =
    if (gel && LocalSkeuo.current) copy(shadow = Shadow(Color.White.copy(alpha = 0.45f), Offset(0f, 1.2f), 0f)) else this

/** Pill selector with a springy sliding indicator. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return SegmentedNothing(options, selected, onSelect, modifier)
    if (LocalMetro.current) return SegmentedMetro(options, selected, onSelect, modifier, colors)
    if (LocalSkeuo.current) return SegmentedSkeuo(options = options, selected = selected, onSelect = onSelect, modifier = modifier, colors = colors)
    val c = Ui.colors
    BoxWithConstraints(
        modifier
            .clip(CircleShape)
            .background(c.surfaceHigh)
            .padding(4.dp)
    ) {
        val w = maxWidth / options.size
        val offset by animateDpAsState(w * selected, spring(dampingRatio = 0.7f, stiffness = 380f), label = "segOffset")
        val indicator by animateColorAsState(colors?.getOrNull(selected) ?: c.accent, label = "segColor")
        Box(
            Modifier
                .offset(x = offset)
                .width(w)
                .height(40.dp)
                .clip(CircleShape)
                .background(indicator)
        )
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            options.forEachIndexed { i, label ->
                val fg by animateColorAsState(if (i == selected) Ink else c.inkSoft, label = "segFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Ui.type.label, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Recessed track with a glossy segment that slides on a spring, like the old iOS segmented control. */
@Composable
private fun SegmentedSkeuo(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
) {
    val c = Ui.colors
    BoxWithConstraints(
        modifier
            .well(c.surfaceHigh, RoundedCornerShape(14.dp), c.isDark)
            .padding(4.dp)
    ) {
        val w = maxWidth / options.size
        val offset by animateDpAsState(w * selected, spring(dampingRatio = 0.7f, stiffness = 380f), label = "segOffset")
        val indicator by animateColorAsState(colors?.getOrNull(selected) ?: c.accent, label = "segColor")
        Box(
            Modifier
                .offset(x = offset)
                .width(w)
                .height(40.dp)
                .gel(indicator, RoundedCornerShape(11.dp))
        )
        Row(Modifier.fillMaxWidth().height(40.dp)) {
            options.forEachIndexed { i, label ->
                val fg by animateColorAsState(if (i == selected) Ink else c.inkSoft, label = "segFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(11.dp))
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Ui.type.label.onGel(i == selected), color = fg, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.surface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return SoftCardNothing(modifier, color, padding, content)
    if (LocalSkeuo.current) return SoftCardSkeuo(modifier = modifier, color = color, padding = padding, content = content)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (color == Ui.colors.surface) color else color.tile())
            .padding(padding),
        content = content,
    )
}

/** A sheet of cream paper resting on the linen. */
@Composable
private fun SoftCardSkeuo(
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.surface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .paper(color, RoundedCornerShape(18.dp), dark = Ui.colors.isDark)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return SectionTitleNothing(text, modifier, action, onAction)
    if (LocalMetro.current) return SectionTitleMetro(text, modifier, action, onAction)
    if (LocalSkeuo.current) return SectionTitleSkeuo(text = text, modifier = modifier, action = action, onAction = onAction)
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Ui.type.titleSmall, color = Ui.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = Ui.type.label,
                color = Ui.colors.accentText,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SectionTitleSkeuo(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Ui.type.titleSmall, color = Ui.colors.inkSoft, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = Ui.type.label.onGel(),
                color = Ink,
                modifier = Modifier
                    .bouncyClick(onClick = onAction)
                    .gel(Ui.colors.accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Ui.colors.surfaceHigh,
    tint: Color = Ui.colors.ink,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return RoundIconButtonNothing(icon, onClick, modifier, background, tint, size, contentDescription)
    if (LocalMetro.current) return RoundIconButtonMetro(icon, onClick, modifier, background, tint, size, contentDescription)
    if (LocalSkeuo.current) return RoundIconButtonSkeuo(icon = icon, onClick = onClick, modifier = modifier, background = background, tint = tint, size = size, contentDescription = contentDescription)
    Box(
        modifier
            .size(size)
            .bouncyClick(onClick = onClick)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Round domed button, like a metal knob; a custom [background] turns it into a gel. */
@Composable
private fun RoundIconButtonSkeuo(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Ui.colors.surfaceHigh,
    tint: Color = Ui.colors.ink,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    val c = Ui.colors
    val custom = background != c.surfaceHigh
    Box(
        modifier
            .size(size)
            .bouncyClick(onClick = onClick)
            .then(if (custom) Modifier.gel(background, CircleShape) else Modifier.knob(c.isDark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (custom) tint else c.inkSoft, modifier = Modifier.size(size * 0.5f))
    }
}

/** Domed metal button: vertical shading, a shine on top, dark rim and a shadow under. */
fun Modifier.knob(dark: Boolean): Modifier = drawBehind {
    val r = size.minDimension / 2
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(Color.Black.copy(alpha = 0.3f), r, center + Offset(0f, 1.5.dp.toPx()))
    val base = if (dark) Color(0xFF4A4038) else Color(0xFFF2ECE2)
    drawCircle(Brush.verticalGradient(listOf(base.lighten(0.5f), base, base.darken(0.18f))), r, center)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (dark) 0.18f else 0.7f), Color.Transparent), center - Offset(0f, r * 0.45f), r * 0.8f), r, center)
    drawCircle(Color.Black.copy(alpha = if (dark) 0.6f else 0.28f), r - 0.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (com.money.app.ui.theme.LocalNothing.current) return TopBarNothing(title, onBack, modifier, actions)
    if (LocalMetro.current) return TopBarMetro(title, onBack, modifier, actions)
    if (LocalSkeuo.current) return TopBarSkeuo(title = title, onBack = onBack, modifier = modifier, actions = actions)
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Indietro")
        Text(
            title,
            style = Ui.type.title,
            color = Ui.colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

/** Stitched leather toolbar running under the status bar, title stamped in cream thread colour. */
@Composable
private fun TopBarSkeuo(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RectangleShape)
            .leather(RectangleShape, corner = 0.dp)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Indietro")
        Text(
            title,
            style = Ui.type.title.copy(shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, -1.5f), 0f)),
            color = Thread,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ui.colors.accent,
    icon: ImageVector? = null,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return PrimaryButtonNothing(text, onClick, modifier, enabled, color, icon)
    if (LocalMetro.current) return PrimaryButtonMetro(text, onClick, modifier, enabled, color, icon)
    if (LocalSkeuo.current) return PrimaryButtonSkeuo(text = text, onClick = onClick, modifier = modifier, enabled = enabled, color = color, icon = icon)
    val bg by animateColorAsState(if (enabled) color else Ui.colors.surfaceHigh, label = "btnBg")
    val fg by animateColorAsState(if (enabled) Ink else Ui.colors.inkFaint, label = "btnFg")
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .bouncyClick(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .clip(CircleShape)
            .background(bg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Ui.type.bodyStrong.copy(fontSize = 17.sp), color = fg)
    }
}

@Composable
private fun PrimaryButtonSkeuo(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ui.colors.accent,
    icon: ImageVector? = null,
) {
    val c = Ui.colors
    val bg by animateColorAsState(if (enabled) color else c.surfaceHigh, label = "btnBg")
    val fg by animateColorAsState(if (enabled) Ink else c.inkFaint, label = "btnFg")
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .bouncyClick(enabled = enabled, pressedScale = 0.96f, onClick = onClick)
            .gel(bg, RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Ui.type.bodyStrong.copy(fontSize = 17.sp).onGel(enabled), color = fg)
    }
}

@Composable
fun MonthSwitcher(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Ui.type.titleSmall,
) {
    if (LocalSkeuo.current) return MonthSwitcherSkeuo(month = month, onChange = onChange, modifier = modifier, style = style)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Rounded.ChevronLeft, { onChange(month.minusMonths(1)) }, size = 36.dp, contentDescription = "Mese precedente")
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
            },
            label = "month",
            modifier = Modifier.width(150.dp),
        ) {
            Text(if (LocalMetro.current) Dates.monthYear(it).lowercase() else if (com.money.app.ui.theme.LocalNothing.current) Dates.monthYear(it).uppercase() else Dates.monthYear(it), style = if (com.money.app.ui.theme.LocalNothing.current) Ui.type.label.copy(fontSize = 14.sp, letterSpacing = 0.1.em) else style, color = Ui.colors.ink, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
        }
        RoundIconButton(Icons.Rounded.ChevronRight, { onChange(month.plusMonths(1)) }, size = 36.dp, contentDescription = "Mese successivo")
    }
}

@Composable
private fun MonthSwitcherSkeuo(
    month: YearMonth,
    onChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Ui.type.titleSmall,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Rounded.ChevronLeft, { onChange(month.minusMonths(1)) }, size = 36.dp, contentDescription = "Mese precedente")
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
            },
            label = "month",
            modifier = Modifier.width(176.dp),
        ) {
            Text(Dates.monthYear(it), style = style, color = Ui.colors.ink, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
        }
        RoundIconButton(Icons.Rounded.ChevronRight, { onChange(month.plusMonths(1)) }, size = 36.dp, contentDescription = "Mese successivo")
    }
}

@Composable
fun EmptyState(
    title: String,
    text: String,
    icon: String = "savings",
    modifier: Modifier = Modifier,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return EmptyStateNothing(title, text, modifier)
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CategoryBadge(icon, Ui.colors.accent, size = 96.dp, animated = true)
        Spacer(Modifier.height(16.dp))
        Text(title, style = Ui.type.titleSmall, color = Ui.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = Ui.type.body, color = Ui.colors.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

/** Thin progress bar with rounded ends; fills with a spring. */
@Composable
fun SoftProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    track: Color = Ui.colors.surfaceHigh,
) {
    if (com.money.app.ui.theme.LocalNothing.current) return SegmentedBarNothing(progress, color, modifier, height)
    if (LocalSkeuo.current) return SoftProgressSkeuo(progress = progress, color = color, modifier = modifier, height = height, track = track)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = 60f))
    }
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(anim.value.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color.tile()))
    }
}

/** Recessed groove filled with a glossy bar that springs into place. */
@Composable
private fun SoftProgressSkeuo(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    track: Color = Ui.colors.surfaceHigh,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = 60f))
    }
    Box(modifier.fillMaxWidth().height(height).well(track, CircleShape, Ui.colors.isDark)) {
        if (anim.value > 0.001f) {
            Box(Modifier.fillMaxWidth(anim.value.coerceIn(0f, 1f)).fillMaxHeight().gel(color, CircleShape, drop = false))
        }
    }
}
