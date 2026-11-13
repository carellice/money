package com.money.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.RoundShape
import com.money.app.ui.theme.Ui

/*
 * Nothing look: monochrome canvas, Space Mono ALL CAPS labels, pill and circle controls,
 * flat surfaces without shadows, segmented bars, dot-matrix figures and percussive ease-out motion.
 */

/** The one easing of the Nothing style: subtle ease-out, no spring. */
val NothingEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

/** Press feedback: the control dims for a moment, like a mechanical key; no bounce. */
@Composable
internal fun Modifier.nothingPress(
    enabled: Boolean,
    haptic: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(if (pressed) 60 else 180, easing = NothingEase), label = "press")
    val feedback = LocalHapticFeedback.current
    return this
        .graphicsLayer { this.alpha = alpha }
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

/** Uniform dot grid, the Nothing dot-matrix motif, drawn behind the content. */
fun Modifier.dotGrid(color: Color, step: Dp = 16.dp, radius: Dp = 1.dp): Modifier = drawBehind {
    val s = step.toPx()
    val r = radius.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += s
        }
        y += s
    }
}

@Composable
internal fun PillNothing(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    leading: (@Composable () -> Unit)?,
) {
    val c = Ui.colors
    val bg by animateColorAsState(if (selected) c.accent else Color.Transparent, tween(200, easing = NothingEase), label = "pillBg")
    val fg by animateColorAsState(if (selected) Ink else c.ink, tween(200, easing = NothingEase), label = "pillFg")
    Row(
        modifier
            .bouncyClick(onClick = onClick)
            .clip(RoundShape)
            .background(bg)
            .border(1.dp, if (selected) c.accent else c.inkFaint.copy(alpha = 0.6f), RoundShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = Ui.type.label, color = fg, maxLines = 1)
    }
}

/** Framed pill; the chosen segment is inverted (display ink behind paper-coloured text). */
@Composable
internal fun SegmentedNothing(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val c = Ui.colors
    BoxWithConstraints(modifier.clip(RoundShape).border(1.dp, c.inkFaint.copy(alpha = 0.6f), RoundShape).padding(3.dp)) {
        val w = maxWidth / options.size
        val offset by animateDpAsState(w * selected, tween(220, easing = NothingEase), label = "segOffset")
        Box(Modifier.offset(x = offset).width(w).height(38.dp).clip(RoundShape).background(c.accent))
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            options.forEachIndexed { i, label ->
                val fg by animateColorAsState(if (i == selected) Ink else c.inkSoft, tween(200, easing = NothingEase), label = "segFg")
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundShape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label.uppercase(), style = Ui.type.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun SoftCardNothing(modifier: Modifier, color: Color, padding: Dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Ui.colors
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    // Colour is an event, not a fill: a coloured card stays a flat surface with a signal dot, and its Ink text turns to ink.
    androidx.compose.runtime.CompositionLocalProvider(com.money.app.ui.theme.LocalNothingInk provides if (color == c.surface) com.money.app.ui.theme.LocalNothingInk.current else c.ink) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape)
                .then(if (color == c.surface) Modifier else Modifier.drawBehind { drawCircle(color, 4.dp.toPx(), Offset(size.width - 16.dp.toPx(), 16.dp.toPx())) })
                .padding(padding),
            content = content,
        )
    }
}

/** Instrument-panel label: Space Mono, ALL CAPS, secondary grey; the action in brackets. */
@Composable
internal fun SectionTitleNothing(text: String, modifier: Modifier, action: String?, onAction: (() -> Unit)?) {
    val c = Ui.colors
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = Ui.type.label.copy(letterSpacing = 0.1.em), color = c.inkSoft, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                "[ ${action.uppercase()} ]",
                style = Ui.type.label,
                color = c.ink,
                modifier = Modifier.bouncyClick(onClick = onAction).padding(horizontal = 2.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun RoundIconButtonNothing(icon: ImageVector, onClick: () -> Unit, modifier: Modifier, background: Color, tint: Color, size: Dp, contentDescription: String?) {
    val c = Ui.colors
    val custom = background != c.surfaceHigh
    Box(
        modifier
            .size(size)
            .bouncyClick(onClick = onClick)
            .clip(RoundShape)
            .background(if (custom) background else c.surface)
            .then(if (custom) Modifier else Modifier.border(1.dp, c.line, RoundShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (custom) tint else c.ink, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
internal fun TopBarNothing(title: String, onBack: (() -> Unit)?, modifier: Modifier, actions: @Composable RowScope.() -> Unit) {
    val c = Ui.colors
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) RoundIconButtonNothing(Icons.AutoMirrored.Rounded.ArrowBack, onBack, Modifier, c.surfaceHigh, c.ink, 42.dp, "Indietro")
        Text(title, style = Ui.type.title, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Pill button in display ink, Space Mono caps; a coloured [color] (expense, income) keeps its signal. */
@Composable
internal fun PrimaryButtonNothing(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, color: Color, icon: ImageVector?) {
    val c = Ui.colors
    val fill = if (color == c.accent) c.accent else color
    val bg by animateColorAsState(if (enabled) fill else Color.Transparent, tween(200, easing = NothingEase), label = "btnBg")
    val fg = when {
        !enabled -> c.inkFaint
        fill == c.accent -> Ink
        else -> Color.White
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .bouncyClick(enabled = enabled, onClick = onClick)
            .clip(RoundShape)
            .background(bg)
            .then(if (enabled) Modifier else Modifier.border(1.dp, c.inkFaint.copy(alpha = 0.6f), RoundShape)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = Ui.type.label.copy(fontSize = 14.sp, letterSpacing = 0.08.em), color = fg, maxLines = 1)
    }
}

/** Physical switch: pill track, round thumb; on = display-ink track with a paper thumb. */
@Composable
fun NothingToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(200, easing = NothingEase), label = "toggle")
    val track by animateColorAsState(if (checked) c.accent else Color.Transparent, tween(200, easing = NothingEase), label = "track")
    val thumb by animateColorAsState(if (checked) Ink else c.inkFaint, tween(200, easing = NothingEase), label = "thumb")
    Box(
        modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundShape)
            .background(track)
            .border(1.dp, if (checked) c.accent else c.inkFaint, RoundShape)
            .clickable { onChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .size(22.dp)
                .graphicsLayer { translationX = t * 22.dp.toPx() }
                .clip(RoundShape)
                .background(thumb),
        )
    }
}

@Composable
internal fun SwitchRowNothing(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val c = Ui.colors
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick { onChange(!checked) }
            .drawBehind { drawLine(c.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.bodyStrong, color = c.ink)
            subtitle?.let { Text(it, style = Ui.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.inkSoft) }
        }
        Spacer(Modifier.size(12.dp))
        NothingToggle(checked, onChange)
    }
}

/** The signature Nothing bar: discrete square segments with 2dp gaps that light up one by one. */
@Composable
internal fun SegmentedBarNothing(progress: Float, color: Color, modifier: Modifier, height: Dp) {
    val c = Ui.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo(progress.coerceIn(0f, 1f), tween(400, easing = NothingEase)) }
    val off = c.line
    val on = color
    Box(
        modifier.fillMaxWidth().height(height.coerceIn(4.dp, 20.dp)).drawBehind {
            val gap = 2.dp.toPx()
            val n = (size.width / (6.dp.toPx() + gap)).toInt().coerceIn(8, 48)
            val w = (size.width - gap * (n - 1)) / n
            val lit = (anim.value * n).let { kotlin.math.ceil(it - 0.001f).toInt() }
            for (i in 0 until n) {
                drawRect(if (i < lit) on else off, Offset(i * (w + gap), 0f), Size(w, size.height))
            }
        },
    )
}

/** Empty state: lots of room, a grey headline and one line of hint, a small dot-matrix square. */
@Composable
internal fun EmptyStateNothing(title: String, text: String, modifier: Modifier) {
    val c = Ui.colors
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).dotGrid(c.inkFaint, step = 10.dp, radius = 1.5.dp))
        Spacer(Modifier.height(20.dp))
        Text(title.uppercase(), style = Ui.type.label.copy(letterSpacing = 0.1.em), color = c.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = Ui.type.body, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

/** Tab page title: small mono app tag over a dot-matrix headline. */
@Composable
internal fun PageTitleNothing(text: String, modifier: Modifier) {
    val c = Ui.colors
    Column(modifier) {
        Text("MONEY", style = Ui.type.caption.copy(letterSpacing = 0.14.em), color = c.inkFaint)
        Text(text.uppercase(), style = Ui.type.hero.copy(fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = 0.02.em), color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}
