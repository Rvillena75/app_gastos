package com.villegol.appgastos.ai.receipt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class PreparedReceiptImage(
    val path: String,
    val elapsedMs: Long,
    val transformed: Boolean
)

/**
 * Conservative, reusable image preparation. It corrects EXIF orientation and
 * caps very large captures before OCR; it does not alter the original evidence.
 */
class ReceiptImagePreprocessor(
    context: android.content.Context,
    private val maxDimension: Int = 2_400
) {
    private val cacheDirectory = File(context.applicationContext.cacheDir, "receipt-preprocessed")

    fun prepare(sourcePath: String): PreparedReceiptImage {
        val started = System.nanoTime()
        val source = File(sourcePath)
        require(source.isFile) { "No existe la imagen: $sourcePath" }
        val orientation = runCatching {
            ExifInterface(source).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "No se pudo decodificar la imagen: $sourcePath" }

        val needsTransform = orientation != ExifInterface.ORIENTATION_NORMAL ||
            max(bounds.outWidth, bounds.outHeight) > maxDimension
        if (!needsTransform) {
            return PreparedReceiptImage(source.absolutePath, elapsedMs(started), transformed = false)
        }

        cacheDirectory.mkdirs()
        val cache = File(cacheDirectory, "${source.nameWithoutExtension}-${source.length()}-$maxDimension.jpg")
        if (cache.isFile && cache.length() > 0) {
            return PreparedReceiptImage(cache.absolutePath, elapsedMs(started), transformed = true)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options)
            ?: error("No se pudo cargar la imagen: $sourcePath")
        val scaled = scaleToMax(decoded, maxDimension)
        if (scaled !== decoded) decoded.recycle()
        val oriented = applyOrientation(scaled, orientation)
        if (oriented !== scaled) scaled.recycle()
        FileOutputStream(cache).use { output ->
            check(oriented.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "No se pudo escribir la imagen preprocesada" }
        }
        oriented.recycle()
        return PreparedReceiptImage(cache.absolutePath, elapsedMs(started), transformed = true)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > maxDimension * 2) sample *= 2
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap, maximum: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maximum) return bitmap
        val scale = maximum.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun elapsedMs(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000L
}
