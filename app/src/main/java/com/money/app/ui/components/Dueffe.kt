package com.money.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.money.app.ui.theme.DueffeLineStrong
import com.money.app.ui.theme.DueffePrimaryStrong
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.LocalBlobTime
import com.money.app.ui.theme.Ui
import kotlin.math.cos
import kotlin.math.sin

/** Dueffe "Reveal" curve: entrances of pages and details. */
val DueffeReveal = CubicBezierEasing(0.22f, 0.78f, 0.16f, 1f)

/** Card: vertical gradient from Surface High to Surface, 1px gold line, radius 26, no shadow. */
@Composable
internal fun SoftCardDueffe(modifier: Modifier, color: Color, padding: Dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Ui.colors
    val shape = RoundedCornerShape(26.dp)
    // A custom colour keeps a solid fill: its text is dark Ink, which needs the light colour behind it.
    val fill = if (color == c.surface) Brush.verticalGradient(listOf(c.surfaceHigh, c.surface))
    else Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.12f)))
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, if (color == c.surface) c.line else Color.Transparent, shape)
            .padding(padding),
        content = content,
    )
}

/** Gold gradient at 135°, from the accent to Primary Strong. */
fun goldBrush(accent: Color) = Brush.linearGradient(listOf(accent, lerp(accent, DueffePrimaryStrong, 0.7f)), Offset.Zero, Offset.Infinite)

/** Main button: gold gradient, On Primary text, radius 18. */
@Composable
internal fun PrimaryButtonDueffe(text: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean, color: Color, icon: ImageVector?) {
    val c = Ui.colors
    val shape = RoundedCornerShape(18.dp)
    val fg = if (enabled) Ink else c.inkFaint
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .bouncyClick(enabled = enabled, pressedScale = 0.95f, onClick = onClick)
            .clip(shape)
            .then(if (enabled) Modifier.background(goldBrush(color)) else Modifier.background(c.surface).border(1.dp, DueffeLineStrong, shape)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Ui.type.label.copy(fontSize = 16.sp), color = fg, maxLines = 1)
    }
}

/** Chip: Surface pill with a line border, solid gold when selected, colours in 260ms. */
@Composable
internal fun PillDueffe(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, color: Color, icon: ImageVector?, leading: (@Composable () -> Unit)?) {
    val c = Ui.colors
    val bg by animateColorAsState(if (selected) color else c.surface, androidx.compose.animation.core.tween(260), label = "chipBg")
    val fg by animateColorAsState(if (selected) Ink else c.ink, androidx.compose.animation.core.tween(260), label = "chipFg")
    val line by animateColorAsState(if (selected) color else c.line, androidx.compose.animation.core.tween(260), label = "chipLine")
    Row(
        modifier
            .bouncyClick(pressedScale = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, line, CircleShape)
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

/** Round button: Surface with a line border; a custom [background] fills it. */
@Composable
internal fun RoundIconButtonDueffe(icon: ImageVector, onClick: () -> Unit, modifier: Modifier, background: Color, tint: Color, size: Dp, contentDescription: String?) {
    val c = Ui.colors
    val plain = background == c.surfaceHigh
    Box(
        modifier
            .size(size)
            .bouncyClick(onClick = onClick)
            .clip(CircleShape)
            .background(if (plain) c.surface else background)
            .border(1.dp, if (plain) c.line else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Aurora: three gold and amber nebulae drifting slowly (32s loop) on the near-black page. */
@Composable
fun Modifier.aurora(): Modifier {
    val time = LocalBlobTime.current
    val gold = Ui.colors.accent
    return drawBehind {
        val t = time.value * (2f * Math.PI.toFloat() / 32f)
        val nebulae = listOf(
            Triple(Offset(size.width * (0.15f + 0.08f * sin(t)), size.height * (0.10f + 0.04f * cos(t))), gold.copy(alpha = 0.18f), size.width * 0.75f),
            Triple(Offset(size.width * (0.90f + 0.06f * cos(t * 1.3f)), size.height * (0.32f + 0.05f * sin(t * 0.7f))), DueffePrimaryStrong.copy(alpha = 0.12f), size.width * 0.70f),
            Triple(Offset(size.width * (0.40f + 0.10f * sin(t * 0.8f + 2f)), size.height * (0.78f + 0.04f * cos(t))), gold.copy(alpha = 0.10f), size.width * 0.85f),
        )
        nebulae.forEach { (center, color, radius) ->
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
        }
    }
}
