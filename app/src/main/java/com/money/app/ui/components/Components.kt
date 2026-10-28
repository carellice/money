package com.money.app.ui.components

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImage
import com.money.app.brand.Brands
import com.money.app.domain.Dates
import com.money.app.domain.Money
import com.money.app.ui.theme.BlobShape
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.blob
import java.io.File
import java.time.YearMonth

/** Click with a springy press-down and a light haptic tick. */
@Composable
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier {
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
        .clickable(interaction, indication = null, enabled = enabled) {
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
    val brand = Brands.find(brandKey)
    when {
        imagePath != null -> AsyncImage(
            model = File(imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(BlobShape(imagePath.hashCode())),
        )
        brand != null -> Box(
            modifier.size(size).blob(brand.color, seed = brand.key.hashCode(), amplitude = 0.08f, animated = animated),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (brand.isLight) Ink else Color.White
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

/** Counts up/down to [cents] whenever it changes. */
@Composable
fun AnimatedAmount(
    cents: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.ink,
    signed: Boolean = false,
    decimalsScale: Float = 0.5f,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(cents) {
        anim.animateTo(cents.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    val shown = if (anim.isRunning) anim.value.toLong() else cents
    AmountText(shown, style, modifier, color, signed, decimalsScale)
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

/** Pill selector with a springy sliding indicator. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color>? = null,
) {
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

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = Ui.colors.surface,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(color)
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
fun RoundIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Ui.colors.surfaceHigh,
    tint: Color = Ui.colors.ink,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
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

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
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

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ui.colors.accent,
    icon: ImageVector? = null,
) {
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
        Text(text, style = Ui.type.bodyStrong.copy(fontSize = 16.sp), color = fg)
    }
}

@Composable
fun MonthSwitcher(
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
            modifier = Modifier.width(150.dp),
        ) {
            Text(Dates.monthYear(it), style = style, color = Ui.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = 60f))
    }
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(anim.value.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}
