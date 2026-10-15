package com.marbleng.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

object QrImageDecoder {
    private const val MAX_EDGE = 1_600

    fun decode(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return try {
            decodePixelsOf(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun decode(frame: Bitmap): String? {
        if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) return null
        val longestEdge = maxOf(frame.width, frame.height)

        val working = if (longestEdge > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longestEdge
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            frame
        }
        return try {
            decodePixelsOf(working)
        } finally {
            if (working !== frame) working.recycle()
        }
    }

    private fun decodePixelsOf(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        decodePixels(pixels, width, height)?.let { return it }

        for (index in pixels.indices) {
            val argb = pixels[index]
            pixels[index] = (argb and 0xFF000000.toInt()) or (argb.inv() and 0x00FFFFFF)
        }
        return decodePixels(pixels, width, height)
    }

    private fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),

                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
        return runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
        }.getOrNull()
        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longestEdge > 0 && longestEdge / sample > MAX_EDGE) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        }.getOrNull()
    }
}
