package com.money.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The screens import these instead of the Compose shapes: same calls, but in the Modern UI
 * style every corner is square, as on Windows Phone.
 */

@Composable
fun RoundedCornerShape(size: Dp): Shape =
    if (LocalMetro.current) RectangleShape else androidx.compose.foundation.shape.RoundedCornerShape(size)

@Composable
fun RoundedCornerShape(percent: Int): Shape =
    if (LocalMetro.current) RectangleShape else androidx.compose.foundation.shape.RoundedCornerShape(percent)

@Composable
fun RoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): Shape =
    if (LocalMetro.current) RectangleShape
    else androidx.compose.foundation.shape.RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

val CircleShape: Shape
    @Composable get() = if (LocalMetro.current) RectangleShape else androidx.compose.foundation.shape.CircleShape

/** Always round, also in Modern UI: the app bar buttons of Windows Phone were circles. */
val RoundShape: Shape = androidx.compose.foundation.shape.CircleShape
