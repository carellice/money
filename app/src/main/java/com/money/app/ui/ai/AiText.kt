package com.money.app.ui.ai

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.StartOffset

/** Renders the small markdown subset the model is asked to use: **bold** and "- " bullets. */
fun aiText(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.trim().lines()
        .map { it.trimEnd() }
        .map { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> "•  " + trimmed.drop(2)
                trimmed.startsWith("#") -> "**" + trimmed.trimStart('#').trim() + "**"
                else -> line
            }
        }
    lines.forEachIndexed { index, line ->
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
        }
        if (index < lines.lastIndex) append("\n")
    }
}

/** Three blobs bouncing while the model thinks. */
@Composable
fun ThinkingDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val lift = transition.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(420), RepeatMode.Reverse, StartOffset(i * 140)),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(9.dp)
                    .graphicsLayer {
                        translationY = -lift.value * 8.dp.toPx()
                        alpha = 0.4f + 0.6f * lift.value
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
