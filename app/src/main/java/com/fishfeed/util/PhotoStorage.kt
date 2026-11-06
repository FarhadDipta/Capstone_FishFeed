package com.fishfeed.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Menyimpan foto kolam ke penyimpanan internal aplikasi (offline, tanpa izin tambahan). */
object PhotoStorage {
    private const val DIR = "pond_photos"
    private const val MAX_SIDE = 1024

    /** Menyalin foto yang dipilih pengguna (diperkecil) lalu mengembalikan path filenya. */
    suspend fun savePondPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            // Putar sesuai orientasi kamera (EXIF) bila perlu.
            val rotation = try {
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (rotation != 0f || scale < 1f) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (rotation != 0f) m.postRotate(rotation)
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }

            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "pond_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Memuat foto (diperkecil sesuai kebutuhan tampilan). */
    fun load(path: String, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).delete()
        } catch (e: Exception) {
            // abaikan
        }
    }
}
