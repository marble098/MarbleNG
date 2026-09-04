package com.marbleng.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * MARBLE_QR_IMPORT_V121 — read a share link out of a QR code the user already has.
 *
 * Marble does not ask for the camera. A QR config almost always reaches a phone as a screenshot,
 * a saved picture or a photo of somebody's screen, and all three arrive through the system image
 * picker without a single runtime permission. This decodes that image.
 *
 * The picture is downsampled to a sane working size first: a 12-megapixel photo of a QR code
 * neither decodes better nor fits comfortably in memory, and ZXing's binarizer works best on a
 * clean, moderately sized grayscale grid. Both the image and its inversion are attempted, because
 * dark-mode screenshots produce light-on-dark codes that a single pass rejects.
 */
object QrImageDecoder {

    /** Longest edge (px) the decoder works on. Large enough for dense codes, small enough to be cheap. */
    private const val MAX_EDGE = 1_600

    fun decode(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        return try {
            decode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        decodePixels(pixels, width, height)?.let { return it }

        // Inverted pass: light modules on a dark background (dark-mode screenshots).
        for (index in pixels.indices) {
            val argb = pixels[index]
            pixels[index] = (argb and 0xFF000000.toInt()) or (argb.inv() and 0x00FFFFFF)
        }
        return decodePixels(pixels, width, height)
    }

    private fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeSource(source)
    }

    /**
     * MARBLE_QR_CAMERA_V123 — decode one camera frame straight from its luminance plane.
     *
     * A viewfinder frame is YUV, not a bitmap: converting it to ARGB just to feed ZXing would cost
     * an allocation per frame on the analysis thread. The Y plane *is* the grayscale image the
     * binarizer wants, so it is handed over directly. [rowStride] is the plane's row stride, which
     * carries padding on most sensors — but not on all of them, so the buffer's own length decides
     * which stride is real instead of trusting the reported one.
     *
     * Deliberately no inversion pass here: a live viewfinder is lit, and frames arrive ~30 a second,
     * so the next frame is a cheaper answer than a second decode of this one.
     */
    fun decodeLuminance(
        yuv: ByteArray,
        rowStride: Int,
        width: Int,
        height: Int
    ): String? {
        if (width <= 0 || height <= 0) return null
        val dataWidth = when {
            rowStride >= width && yuv.size >= rowStride * height -> rowStride
            yuv.size >= width * height -> width
            else -> return null
        }
        val source = runCatching {
            PlanarYUVLuminanceSource(yuv, dataWidth, height, 0, 0, width, height, false)
        }.getOrNull() ?: return null
        return decodeSource(source)
    }

    private fun decodeSource(source: LuminanceSource): String? {
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    // A photograph of a screen is never a clean grid; spend the extra passes.
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
