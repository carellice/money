package com.money.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.money.app.ui.components.dotGrid
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic organic outline: [points] control points around an ellipse whose radii
 * breathe over time, joined with a closed Catmull-Rom spline.
 */
class BlobSpec(seed: Int, val points: Int = 7, val amplitude: Float = 0.07f) {
    private val random = Random(seed)
    private val phase = FloatArray(points) { random.nextFloat() * 2f * PI.toFloat() }
    private val phase2 = FloatArray(points) { random.nextFloat() * 2f * PI.toFloat() }
    private val speed = FloatArray(points) { 0.6f + random.nextFloat() * 0.8f }
    private val bias = FloatArray(points) { (random.nextFloat() - 0.5f) * amplitude }
    private val rotation = random.nextFloat() * 2f * PI.toFloat()
    private val xs = FloatArray(points)
    private val ys = FloatArray(points)

    fun path(size: Size, time: Float, path: Path = Path(), inset: Float = 0f): Path {
        path.reset()
        val scale = 1f / (1f + amplitude * 1.6f)
        val rx = (size.width / 2f - inset) * scale
        val ry = (size.height / 2f - inset) * scale
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (i in 0 until points) {
            val angle = rotation + 2f * PI.toFloat() * i / points
            val wobble = sin(time * speed[i] + phase[i]) * 0.7f + sin(time * speed[i] * 0.53f + phase2[i]) * 0.3f
            val k = 1f + bias[i] + amplitude * wobble
            xs[i] = cx + cos(angle) * rx * k
            ys[i] = cy + sin(angle) * ry * k
        }
        path.moveTo(xs[0], ys[0])
        for (i in 0 until points) {
            val p0 = (i - 1 + points) % points
            val p2 = (i + 1) % points
            val p3 = (i + 2) % points
            path.cubicTo(
                xs[i] + (xs[p2] - xs[p0]) / 6f, ys[i] + (ys[p2] - ys[p0]) / 6f,
                xs[p2] - (xs[p3] - xs[i]) / 6f, ys[p2] - (ys[p3] - ys[i]) / 6f,
                xs[p2], ys[p2],
            )
        }
        path.close()
        return path
    }
}

/** A frozen blob outline, handy for clipping images. */
class BlobShape(seed: Int, private val time: Float = 0f, amplitude: Float = 0.07f) : Shape {
    private val spec = BlobSpec(seed, amplitude = amplitude)
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(spec.path(size, time))
}

/** Rounded "app icon" square that stands in for the organic outline in the skeuomorphic style. */
val IconShape = RoundedCornerShape(26)

/** [BlobShape] or, in the skeuomorphic style, [IconShape]. */
@Composable
fun blobShape(seed: Int): Shape = when {
    LocalMetro.current -> RectangleShape
    LocalNothing.current -> androidx.compose.foundation.shape.CircleShape
    LocalSkeuo.current -> IconShape
    else -> remember(seed) { BlobShape(seed) }
}

/** Draws a living blob behind the content, filled or, with [stroke], only its outline. Only the draw phase reads the clock. */
@Composable
fun Modifier.blob(
    color: Color,
    seed: Int = 1,
    amplitude: Float = 0.07f,
    animated: Boolean = true,
    speed: Float = 1f,
    stroke: Dp? = null,
): Modifier {
    // Nothing: a flat circle, or a thin ring with [stroke].
    if (LocalNothing.current) return drawBehind {
        // Wide boxes (amount field, hero) get a 16dp card, never a squashed circle.
        if (kotlin.math.abs(size.width - size.height) > 4.dp.toPx()) {
            val cr = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            if (stroke == null) drawRoundRect(color, cornerRadius = cr)
            else stroke.toPx().let { w -> drawRoundRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), cr, style = Stroke(w)) }
            return@drawBehind
        }
        val r = size.minDimension / 2
        if (stroke == null) drawCircle(color, r)
        else stroke.toPx().let { w -> drawCircle(color, r - w / 2, style = Stroke(w)) }
    }
    // Modern UI: a flat, solid square tile, or a square outline with [stroke].
    if (LocalMetro.current) {
        val solid = color.solid()
        return drawBehind {
            if (stroke == null) drawRect(solid)
            else stroke.toPx().let { w -> drawRect(solid, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w)) }
        }
    }
    if (LocalSkeuo.current) return drawBehind {
        // Glossy gel "app icon", or an engraved rim with [stroke].
        val outline = IconShape.createOutline(size, layoutDirection, this)
        if (stroke == null) drawGel(outline, color, drop = color.alpha > 0.6f)
        else drawOutline(outline, color, style = Stroke(stroke.toPx()))
    }
    val time = LocalBlobTime.current
    val spec = remember(seed, amplitude) { BlobSpec(seed, amplitude = amplitude) }
    val path = remember { Path() }
    return drawBehind {
        val t = if (animated) time.value * speed else 0f
        if (stroke == null) drawPath(spec.path(size, t, path), color)
        else {
            val width = stroke.toPx()
            drawPath(spec.path(size, t, path, inset = width / 2), color, style = Stroke(width))
        }
    }
}

@Composable
fun Modifier.blob(
    brush: Brush,
    seed: Int = 1,
    amplitude: Float = 0.07f,
    speed: Float = 1f,
): Modifier {
    // Nothing has no gradients either: the big hero shapes become an inverted 16dp card in display ink.
    if (LocalNothing.current) {
        val accent = Ui.colors.accent
        return drawBehind { drawRoundRect(accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())) }
    }
    // Modern UI has no gradients: every gradient blob here starts from the accent, so it turns into a flat accent tile.
    if (LocalMetro.current) {
        val accent = Ui.colors.accent
        return drawBehind { drawRect(accent) }
    }
    if (LocalSkeuo.current) return drawBehind {
        val outline = IconShape.createOutline(size, layoutDirection, this)
        translate(top = 2.dp.toPx()) { drawOutline(outline, Color.Black.copy(alpha = 0.3f)) }
        drawOutline(outline, brush)
        clipRect(bottom = size.height * 0.5f) {
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f)), endY = size.height * 0.5f))
        }
        drawOutline(outline, Color.Black.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()))
    }
    val time = LocalBlobTime.current
    val spec = remember(seed, amplitude) { BlobSpec(seed, amplitude = amplitude) }
    val path = remember { Path() }
    return drawBehind {
        drawPath(spec.path(size, time.value * speed, path), brush)
    }
}

/** Soft floating blobs used as page backdrop. */
@Composable
fun Modifier.backdropBlobs(colors: List<Color>, alpha: Float = 0.16f): Modifier {
    // The skeuomorphic pages already sit on linen.
    // Nothing: a faint dot-matrix grid instead of the floating blobs.
    if (LocalNothing.current) return dotGrid(Ui.colors.line, step = 18.dp)
    if (LocalSkeuo.current || LocalMetro.current) return this
    val time = LocalBlobTime.current
    val specs = remember(colors.size) { List(colors.size) { BlobSpec(100 + it * 7, amplitude = 0.12f) } }
    val path = remember { Path() }
    return drawBehind {
        val t = time.value * 0.35f
        specs.forEachIndexed { i, spec ->
            val w = size.width * (0.7f + 0.15f * i)
            val cx = size.width * (if (i % 2 == 0) 0.05f else 0.95f) + sin(t * 0.7f + i) * 30f
            val cy = size.height * (0.08f + 0.35f * i) + cos(t * 0.5f + i * 2) * 40f
            val blobPath = spec.path(Size(w, w), t + i)
            blobPath.translate(Offset(cx - w / 2f, cy - w / 2f))
            drawPath(blobPath, colors[i].copy(alpha = alpha))
            path.reset()
        }
    }
}
