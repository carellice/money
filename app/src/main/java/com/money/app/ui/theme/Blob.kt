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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
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

/** Draws a living blob behind the content. Only the draw phase reads the clock. */
@Composable
fun Modifier.blob(
    color: Color,
    seed: Int = 1,
    amplitude: Float = 0.07f,
    animated: Boolean = true,
    speed: Float = 1f,
): Modifier {
    val time = LocalBlobTime.current
    val spec = remember(seed, amplitude) { BlobSpec(seed, amplitude = amplitude) }
    val path = remember { Path() }
    return drawBehind {
        val t = if (animated) time.value * speed else 0f
        drawPath(spec.path(size, t, path), color)
    }
}

@Composable
fun Modifier.blob(
    brush: Brush,
    seed: Int = 1,
    amplitude: Float = 0.07f,
    speed: Float = 1f,
): Modifier {
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
