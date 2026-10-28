package com.money.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.money.app.domain.Dates
import com.money.app.domain.MonthPoint
import com.money.app.ui.theme.Ink
import com.money.app.ui.theme.Ui
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DonutSegment(val share: Float, val color: Color)

/** Ring chart that sweeps in; the selected segment grows thicker. */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    selected: Int = -1,
    track: Color = Ui.colors.surfaceHigh,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val grow by animateFloatAsState(if (selected >= 0) 1f else 0f, spring(dampingRatio = 0.5f), label = "grow")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val extra = stroke * 0.35f
            val inset = stroke / 2f + extra
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (segments.isEmpty()) return@Canvas
            val gap = if (segments.size > 1) 4f else 0f
            var start = -90f
            val total = 360f * sweep.value
            segments.forEachIndexed { i, seg ->
                val angle = seg.share * 360f
                val visible = (minOf(start + angle, -90f + total) - start).coerceAtLeast(0f)
                if (visible > gap) {
                    val width = if (i == selected) stroke + extra * grow else stroke
                    drawArc(
                        seg.color, start + gap / 2, visible - gap, false, Offset(inset, inset), arcSize,
                        style = Stroke(width, cap = if (segments.size == 1) StrokeCap.Butt else StrokeCap.Round),
                    )
                }
                start += angle
            }
        }
        center()
    }
}

/** Income/expense bar pairs per month, growing in one after the other. */
@Composable
fun TrendBars(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier,
    highlight: YearMonth? = null,
    height: Dp = 160.dp,
) {
    val c = Ui.colors
    val max = (points.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0L).coerceAtLeast(1L)
    Row(modifier.fillMaxWidth().height(height + 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        points.forEachIndexed { index, point ->
            val anim = remember(point) { Animatable(0f) }
            LaunchedEffect(point) {
                delay(index * 70L)
                anim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 120f))
            }
            val active = point.month == highlight
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.height(height).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Bar(point.income.toFloat() / max * anim.value, c.income.copy(alpha = if (active) 1f else 0.55f))
                    Bar(point.expense.toFloat() / max * anim.value, c.expense.copy(alpha = if (active) 1f else 0.55f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    Dates.monthShort(point.month),
                    style = Ui.type.caption,
                    color = if (active) c.ink else c.inkFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight(fraction.coerceIn(0.015f, 1f))
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(color)
    )
}

/** Month calendar where each day is a dot sized and tinted by how much was spent. */
@Composable
fun SpendingCalendar(
    month: YearMonth,
    daily: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    onDay: ((LocalDate) -> Unit)? = null,
) {
    val c = Ui.colors
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val offset = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                Text(it, style = Ui.type.caption, color = c.inkFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = r * 7 + col - offset
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (index in 0 until month.lengthOfMonth()) {
                            val value = daily[index]
                            val intensity = value.toFloat() / max
                            val anim = remember(month, value) { Animatable(0f) }
                            LaunchedEffect(month, value) {
                                delay(index * 18L)
                                anim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f))
                            }
                            val date = month.atDay(index + 1)
                            if (value > 0) {
                                Box(
                                    Modifier
                                        .fillMaxSize(0.35f + 0.6f * intensity)
                                        .graphicsLayer { scaleX = anim.value; scaleY = anim.value }
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.35f + 0.65f * intensity))
                                )
                            } else if (date == today) {
                                Box(Modifier.fillMaxSize(0.8f).clip(CircleShape).background(c.surfaceHigh))
                            }
                            Text(
                                "${index + 1}",
                                style = Ui.type.caption,
                                color = if (value > 0 && intensity > 0.3f) Ink else c.inkSoft,
                                modifier = if (onDay != null) Modifier.bouncyClick { onDay(date) } else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}
