package com.money.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.money.app.R
import kotlin.random.Random

/*
 * Skeuomorphic toolkit: linen backdrop, paper cards, glossy gel fills, recessed wells and
 * stitched leather. Everything is drawn in code except the leather photo.
 */

fun Color.lighten(f: Float) = lerp(this, Color.White, f)
fun Color.darken(f: Float) = lerp(this, Color.Black, f)

/** Glossy "gel" fill in the given outline: vertical shading, top shine, dark rim and a soft drop below. */
fun DrawScope.drawGel(outline: Outline, color: Color, drop: Boolean = true, pressed: Boolean = false) {
    if (drop) translate(top = 1.5.dp.toPx()) { drawOutline(outline, Color.Black.copy(alpha = 0.28f)) }
    val top = if (pressed) color.darken(0.12f) else color.lighten(0.32f)
    val bottom = if (pressed) color.lighten(0.05f) else color.darken(0.14f)
    drawOutline(outline, Brush.verticalGradient(listOf(top, color, bottom)))
    if (!pressed) {
        // Shine on the upper half, the classic glass highlight.
        clipRect(bottom = size.height * 0.5f) {
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.12f)), endY = size.height * 0.5f))
        }
    }
    drawOutline(outline, color.darken(0.38f).copy(alpha = 0.85f), style = Stroke(1.dp.toPx()))
}

/** Gel fill behind the content, clipped to [shape]. */
fun Modifier.gel(color: Color, shape: Shape, pressed: Boolean = false, drop: Boolean = true): Modifier = drawBehind {
    drawGel(shape.createOutline(size, layoutDirection, this), color, drop, pressed)
}

/** A sheet of paper resting on the table: shadow, faint shading and a bevel catching the light. */
fun Modifier.paper(color: Color, shape: Shape = RoundedCornerShape(18.dp), elevation: Dp = 5.dp, dark: Boolean = false): Modifier =
    this
        .shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(color.lighten(if (dark) 0.06f else 0.35f), color, color.darken(0.04f))))
        .border(
            1.dp,
            Brush.verticalGradient(
                if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.5f))
                else listOf(Color.White, Color.Black.copy(alpha = 0.16f)),
            ),
            shape,
        )

/** Pressed into the surface: inner shadow on top, highlight on the lower lip. */
fun Modifier.well(color: Color, shape: Shape, dark: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(color)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (dark) 0.45f else 0.2f), Color.Transparent), endY = 7.dp.toPx()))
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                if (dark) listOf(Color.Black.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))
                else listOf(Color.Black.copy(alpha = 0.22f), Color.White.copy(alpha = 0.9f)),
            ),
            shape,
        )

/** Dashed thread running [inset] inside the edge; draws over the background, under the content. */
fun Modifier.stitches(color: Color, corner: Dp, inset: Dp = 6.dp): Modifier = drawBehind {
    val i = inset.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    val r = (corner.toPx() - i).coerceAtLeast(2f)
    // Dark groove under the thread so it sits in the material.
    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(i, i + 1.dp.toPx()), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(r), style = Stroke(1.2.dp.toPx(), pathEffect = dash))
    drawRoundRect(color, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(r), style = Stroke(1.2.dp.toPx(), pathEffect = dash))
}

val Thread = Color(0xFFEBD3A3)

/** Stitched brown leather, from the photo in res/drawable-nodpi. */
@Composable
fun Modifier.leather(shape: Shape, corner: Dp = 18.dp, stitched: Boolean = true, shade: Float = 0f): Modifier {
    val image = ImageBitmap.imageResource(R.drawable.tex_leather)
    val brush = remember(image) { ShaderBrush(ImageShader(image, TileMode.Mirror, TileMode.Mirror)) }
    return this
        .clip(shape)
        .background(brush)
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.25f + shade))))
        }
        .then(if (stitched) Modifier.stitches(Thread.copy(alpha = 0.8f), corner) else Modifier)
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))), shape)
}

private val linenCache = HashMap<Boolean, ImageBitmap>()

/** Woven linen tile, generated once: per-thread brightness on rows and columns plus fine grain. */
private fun linenTile(dark: Boolean): ImageBitmap = linenCache.getOrPut(dark) {
    val n = 256
    val r = Random(7)
    val rows = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.10f }
    val cols = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.10f }
    val base = if (dark) Color(0xFF2B2420) else Color(0xFFE6DDCB)
    val px = IntArray(n * n)
    for (y in 0 until n) for (x in 0 until n) {
        // Threads alternate over/under, so rows and columns take turns showing.
        val weave = if ((x + y) % 2 == 0) rows[y] else cols[x]
        val v = weave + (r.nextFloat() - 0.5f) * 0.07f
        val c = if (v >= 0) base.lighten(v * (if (dark) 0.6f else 1.6f)) else base.darken(-v * (if (dark) 1.2f else 0.9f))
        px[y * n + x] = android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Picks the modifier chain for the style in use: [skeuo] or the [classic] one. */
@Composable
fun Modifier.skin(skeuo: @Composable Modifier.() -> Modifier, classic: @Composable Modifier.() -> Modifier): Modifier =
    if (LocalSkeuo.current) skeuo() else classic()

/** Page backdrop: linen in the skeuomorphic style, the plain background otherwise. */
@Composable
fun Modifier.linen(dark: Boolean): Modifier {
    if (!LocalSkeuo.current) return background(Ui.colors.bg)
    val brush = ShaderBrush(ImageShader(linenTile(dark), TileMode.Repeated, TileMode.Repeated))
    return background(brush).drawBehind {
        // A faint vignette, as if lit from above.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.04f else 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.12f))))
    }
}
