package com.money.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.money.app.ui.theme.RoundShape
import com.money.app.ui.theme.Ui
import com.money.app.ui.theme.solid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * Modern UI (Windows Phone) look: flat solid tiles, square corners, light lowercase type,
 * circled app bar buttons and the "tilt" press that leans toward the finger.
 */

/** Windows Phone tilt: the pressed element sinks a little and leans toward the touch point. */
@Composable
internal fun Modifier.metroTilt(
    enabled: Boolean,
    haptic: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val sink = remember { Animatable(1f) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> {
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    // Big tiles lean less than small buttons, as on the phone.
                    val k = (120f / maxOf(w, h)).coerceIn(0.15f, 1f) * 14f
                    val dx = (i.pressPosition.x / w - 0.5f) * 2f
                    val dy = (i.pressPosition.y / h - 0.5f) * 2f
                    rx.snapTo(-dy * k)
                    ry.snapTo(dx * k)
                    sink.snapTo(0.96f)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val back = tween<Float>(160, easing = LinearOutSlowInEasing)
                    coroutineScope {
                        launch { rx.animateTo(0f, back) }
                        launch { ry.animateTo(0f, back) }
                        launch { sink.animateTo(1f, back) }
                    }
                }
            }
        }
    }
    val feedback = LocalHapticFeedback.current
    return this
        .onSizeChanged { size = it }
        .graphicsLayer {
            rotationX = rx.value
            rotationY = ry.value
            scaleX = sink.value
            scaleY = sink.value
            cameraDistance = 6f * density
        }
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
internal fun PillMetro(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    color: Color,
    icon: ImageVector?,
    leading: (@Composable () -> Unit)?,
) {
    val c = Ui.colors
    val bg by animateColorAsState(if (selected) color.solid() else Color.Transparent, tween(200), label = "pillBg")
    val fg = if (selected) Color.White else c.ink
    Row(
        modifier
            .bouncyClick(onClick = onClick)
            .background(bg)
            .border(2.dp, if (selected) Color.Transparent else c.ink.copy(alpha = 0.35f), RectangleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
        Text(text.lowercase(), style = Ui.type.label.copy(fontWeight = FontWeight.Normal), color = fg, maxLines = 1)
    }
}

/** Square cells in a thin frame; the accent block slides under the chosen one. */
@Composable
internal fun SegmentedMetro(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    colors: List<Color>?,
) {
    val c = Ui.colors
    BoxWithConstraints(modifier.border(2.dp, c.ink.copy(alpha = 0.3f), RectangleShape).padding(2.dp)) {
        val w = maxWidth / options.size
        val offset by animateDpAsState(w * selected, tween(260, easing = LinearOutSlowInEasing), label = "segOffset")
        val indicator by animateColorAsState((colors?.getOrNull(selected) ?: c.accent).solid(), label = "segColor")
        Box(Modifier.offset(x = offset).width(w).height(42.dp).background(indicator))
        Row(Modifier.fillMaxWidth().height(42.dp)) {
            options.forEachIndexed { i, label ->
                val fg by animateColorAsState(if (i == selected) Color.White else c.inkSoft, label = "segFg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label.lowercase(), style = Ui.type.label.copy(fontWeight = FontWeight.Normal), color = fg, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun SectionTitleMetro(text: String, modifier: Modifier, action: String?, onAction: (() -> Unit)?) {
    Row(modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(text.lowercase(), style = Ui.type.title, color = Ui.colors.ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action.lowercase(),
                style = Ui.type.label.copy(fontWeight = FontWeight.Normal),
                color = Ui.colors.accentText,
                modifier = Modifier.bouncyClick(onClick = onAction).padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

/** Application bar button: an icon in a thin circle, or a solid disc with a custom [background]. */
@Composable
internal fun RoundIconButtonMetro(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    background: Color,
    tint: Color,
    size: Dp,
    contentDescription: String?,
) {
    val c = Ui.colors
    val custom = background != c.surfaceHigh
    Box(
        modifier
            .size(size)
            .bouncyClick(onClick = onClick)
            .clip(RoundShape)
            .then(if (custom) Modifier.background(background.solid()) else Modifier.border(2.dp, c.ink, RoundShape)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (custom) Color.White else c.ink, modifier = Modifier.size(size * 0.46f))
    }
}

/** Windows Phone page header: the app name in small capitals, then the page title, big and lowercase. */
@Composable
internal fun TopBarMetro(title: String, onBack: (() -> Unit)?, modifier: Modifier, actions: @Composable RowScope.() -> Unit) {
    val c = Ui.colors
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onBack != null) RoundIconButtonMetro(Icons.AutoMirrored.Rounded.ArrowBack, onBack, Modifier, c.surfaceHigh, c.ink, 36.dp, "Indietro")
            Text("MONEY", style = Ui.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em), color = c.ink, modifier = Modifier.weight(1f))
            actions()
        }
        Text(
            title.lowercase(),
            style = Ui.type.hero.copy(fontSize = 50.sp, lineHeight = 58.sp),
            color = c.ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun PrimaryButtonMetro(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, color: Color, icon: ImageVector?) {
    val c = Ui.colors
    val bg by animateColorAsState(if (enabled) color.solid() else Color.Transparent, label = "btnBg")
    val fg = if (enabled) Color.White else c.inkFaint
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .bouncyClick(enabled = enabled, onClick = onClick)
            .background(bg)
            .then(if (enabled) Modifier else Modifier.border(2.dp, c.inkFaint, RectangleShape)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.lowercase(), style = Ui.type.titleSmall, color = fg)
    }
}

/** Windows Phone toggle: a framed track that fills with the accent, and a solid square thumb. */
@Composable
fun MetroToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Ui.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(180, easing = LinearOutSlowInEasing), label = "toggle")
    Box(
        modifier
            .size(width = 62.dp, height = 28.dp)
            .border(2.dp, c.ink, RectangleShape)
            .clickable { onChange(!checked) }
            .padding(4.dp),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(t).background(c.accent))
        Box(
            Modifier
                .offset(x = (-4).dp, y = (-4).dp)
                .size(width = 14.dp, height = 28.dp)
                .graphicsLayer { translationX = t * (62 - 14).dp.toPx() }
                .background(c.ink),
        )
    }
}

@Composable
internal fun SwitchRowMetro(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val c = Ui.colors
    Row(
        modifier
            .fillMaxWidth()
            .bouncyClick { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Ui.type.titleSmall, color = c.ink)
            subtitle?.let { Text(it, style = Ui.type.caption, color = c.inkSoft) }
        }
        Spacer(Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            MetroToggle(checked, onChange)
            Text(if (checked) "attivo" else "disattivo", style = Ui.type.caption, color = c.inkSoft, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Title of a tab page: in Modern UI the app name in small capitals over a big lowercase title, like a pivot. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    val c = Ui.colors
    if (!com.money.app.ui.theme.LocalMetro.current) return Text(text, style = Ui.type.display, color = c.ink, modifier = modifier)
    Column(modifier) {
        Text("MONEY", style = Ui.type.label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em), color = c.ink)
        Text(text.lowercase(), style = Ui.type.hero.copy(fontSize = 50.sp, lineHeight = 56.sp), color = c.ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}
