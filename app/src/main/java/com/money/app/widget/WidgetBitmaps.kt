package com.money.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import com.money.app.brand.Brands
import com.money.app.data.RecurringEntity
import com.money.app.ui.components.AppIcons
import com.money.app.ui.theme.BlobSpec
import java.io.File

/**
 * Widgets run on RemoteViews, which can't draw custom shapes: every blob and logo is
 * pre-rendered into a bitmap, using the same seeds as the app so they look identical.
 */
internal object WidgetBitmaps {
    private const val INK = 0xFF15121F.toInt()

    fun logo(item: RecurringEntity, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val brand = Brands.find(item.brandKey)
        val image = item.imagePath?.let { decode(it, px) }
        when {
            image != null -> {
                val blob = blobPath(item.imagePath.hashCode(), px.toFloat())
                canvas.clipPath(blob)
                val side = minOf(image.width, image.height)
                val src = Rect((image.width - side) / 2, (image.height - side) / 2, (image.width + side) / 2, (image.height + side) / 2)
                canvas.drawBitmap(image, src, RectF(0f, 0f, px.toFloat(), px.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                image.recycle()
            }
            brand != null -> {
                canvas.drawPath(blobPath(brand.key.hashCode(), px.toFloat()), fill(brand.hex.toInt()))
                val tint = if (brand.isLight) INK else 0xFFFFFFFF.toInt()
                val vector = brand.vector
                if (vector != null) drawIcon(canvas, vector, px, 0.44f, tint)
                else drawMonogram(canvas, brand.monogram.orEmpty(), px, tint)
            }
            else -> {
                canvas.drawPath(blobPath(item.icon.hashCode(), px.toFloat()), fill(item.color.toInt()))
                drawIcon(canvas, AppIcons[item.icon], px, 0.46f, INK)
            }
        }
        return bitmap
    }

    /** A plain colored blob, used as decoration. */
    fun blob(seed: Int, color: Int, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPath(blobPath(seed, px.toFloat()), fill(color))
        return bitmap
    }

    /** Soft pastel blobs bleeding off the corners, like the app backdrop. Transparent elsewhere. */
    fun backdrop(accent: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun soft(color: Int) = (color and 0x00FFFFFF) or 0x38000000
        fun place(seed: Int, color: Int, cx: Float, cy: Float, d: Float) {
            val path = BlobSpec(seed, amplitude = 0.12f).path(Size(d, d), 1.7f).asAndroidPath()
            path.offset(cx - d / 2f, cy - d / 2f)
            canvas.drawPath(path, fill(soft(color)))
        }
        place(101, accent, width * 0.95f, height * 0.05f, height * 0.9f)
        place(108, 0xFF8FE3C0.toInt(), width * 0.02f, height * 1.0f, height * 0.8f)
        place(115, 0xFFFF9EC4.toInt(), width * 0.62f, height * 1.05f, height * 0.4f)
        return bitmap
    }

    private fun blobPath(seed: Int, px: Float): Path =
        BlobSpec(seed, amplitude = 0.08f).path(Size(px, px), 0f).asAndroidPath()

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun drawIcon(canvas: Canvas, vector: ImageVector, px: Int, scale: Float, color: Int) {
        val path = Path()
        fun walk(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorPath -> path.addPath(node.pathData.toPath().asAndroidPath())
                    is VectorGroup -> walk(node)
                }
            }
        }
        walk(vector.root)
        val size = px * scale
        val matrix = Matrix().apply {
            setScale(size / vector.viewportWidth, size / vector.viewportHeight)
            postTranslate((px - size) / 2f, (px - size) / 2f)
        }
        path.transform(matrix)
        canvas.drawPath(path, fill(color))
    }

    private fun drawMonogram(canvas: Canvas, text: String, px: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = px * if (text.length > 2) 0.24f else 0.34f
            textAlign = Paint.Align.CENTER
        }
        val y = px / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, px / 2f, y, paint)
    }

    private fun decode(path: String, px: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
