package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ImageCompressor {

    data class CompressedImageResult(
        val bitmap: Bitmap,
        val base64Data: String,
        val thumbnailBase64: String,
    )

    fun compressUri(context: Context, uri: Uri, maxDimension: Int = 1024): CompressedImageResult? {
        return try {
            val contentResolver = context.contentResolver

            // First decode bounds
            var inputStream: InputStream? = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Calculate sample size
            var sampleSize = 1
            val maxEdge = max(origWidth, origHeight)
            while ((maxEdge / (sampleSize * 2)) >= maxDimension) {
                sampleSize *= 2
            }

            // Decode sampled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = contentResolver.openInputStream(uri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (bitmap == null) return null

            // Handle EXIF orientation
            try {
                contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    bitmap = rotateBitmapIfNeeded(bitmap!!, orientation)
                }
            } catch (_: Exception) {
                // Ignore EXIF errors
            }

            // Further scale if still larger than maxDimension
            bitmap = scaleBitmapToMax(bitmap!!, maxDimension)

            // Compress to Base64
            val base64 = bitmapToBase64(bitmap!!, quality = 85)

            // Generate thumbnail
            val thumb = scaleBitmapToMax(bitmap!!, 160)
            val thumbBase64 = bitmapToBase64(thumb, quality = 65)

            CompressedImageResult(bitmap!!, base64, thumbBase64)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun compressBitmap(bitmap: Bitmap, maxDimension: Int = 1024): CompressedImageResult {
        val scaled = scaleBitmapToMax(bitmap, maxDimension)
        val base64 = bitmapToBase64(scaled, quality = 85)

        val thumb = scaleBitmapToMax(scaled, 160)
        val thumbBase64 = bitmapToBase64(thumb, quality = 65)

        return CompressedImageResult(scaled, base64, thumbBase64)
    }

    fun compressResource(context: Context, resId: Int, maxDimension: Int = 1024): CompressedImageResult? {
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return null
            compressBitmap(bitmap, maxDimension)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = max(width, height)
        if (maxEdge <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / maxEdge
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
