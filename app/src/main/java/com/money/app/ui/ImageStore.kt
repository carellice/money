package com.money.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies pictures into app storage, downscaled and with EXIF rotation applied. */
object ImageStore {

    suspend fun import(context: Context, uri: Uri, dest: File, maxSize: Int): Boolean =
        save(ImageDecoder.createSource(context.contentResolver, uri), dest, maxSize)

    /** Re-encodes a camera capture in place at a sane size. */
    suspend fun shrink(file: File, maxSize: Int): Boolean =
        save(ImageDecoder.createSource(file), file, maxSize)

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    private suspend fun save(source: ImageDecoder.Source, dest: File, maxSize: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, maxSize.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            dest.parentFile?.mkdirs()
            dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }
}
