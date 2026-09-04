package com.marbleng.app.core

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

// MARBLE_QR_CAMERA_V123
//
// The second half of QR import. A config link is just as often held up on another screen as it is
// saved as a screenshot, so MarbleNG now reads a code from the camera as well as from the image
// picker. Decoding stays on device: ZXing's pure-Java core turns the luminance plane of a camera
// frame into text, which means no camera library, no ML model download and no frame ever leaving
// the device.
//
// Everything here is pure Kotlin with no Android imports — the frame arrives as an ordinary byte
// array — so the whole decode path is covered by ordinary JVM unit tests.

/** Reads a QR code out of the luminance plane of a camera frame. */
object QrFrameDecoder {

    private val HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        // A viewfinder runs dozens of attempts a second, so a frame is decoded cheaply and the
        // *next* frame is the retry. TRY_HARDER would cost more per frame than it saves overall.
        DecodeHintType.TRY_HARDER to false
    )

    /**
     * Decode the Y plane of a YUV_420_888 camera frame.
     *
     * @param luminance the frame's Y plane
     * @param width frame width in pixels
     * @param height frame height in pixels
     * @param rowStride bytes per row of the Y plane; camera hardware pads rows, so this is often
     *   larger than [width]
     * @param sensorOrientation the camera sensor's rotation, used to turn the frame upright — a
     *   phone held in portrait delivers a landscape sensor image
     * @return the decoded text, or `null` when this frame holds no readable code
     */
    fun decode(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width,
        sensorOrientation: Int = 0
    ): String? {
        if (width <= 0 || height <= 0) return null
        val required = (height - 1) * rowStride.coerceAtLeast(width) + width
        if (luminance.size < required) return null

        val compact = compactLuminance(luminance, width, height, rowStride)
        val rotated = normalizeOrientation(compact, width, height, sensorOrientation)
        return decode(rotated.first, rotated.second, rotated.third)
    }

    /** Copy a padded Y plane into the tightly packed array the binarizer expects. */
    fun compactLuminance(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int = width
    ): ByteArray {
        val stride = rowStride.coerceAtLeast(width)
        if (stride == width && luminance.size == width * height) return luminance
        val out = ByteArray(width * height)
        for (row in 0 until height) {
            System.arraycopy(luminance, row * stride, out, row * width, width)
        }
        return out
    }

    /**
     * Turn an upright-in-landscape frame into the orientation the user is looking at.
     *
     * @return `(pixels, width, height)` after rotation; 0 and 180 keep the dimensions, 90 and 270
     *   swap them.
     */
    fun normalizeOrientation(
        luminance: ByteArray,
        width: Int,
        height: Int,
        sensorOrientation: Int
    ): Triple<ByteArray, Int, Int> {
        val turns = ((sensorOrientation % 360) + 360) % 360
        return when (turns) {
            90 -> Triple(rotate(luminance, width, height, clockwise = true), height, width)
            180 -> Triple(rotate(luminance, width, height, clockwise = true).let {
                rotate(it, height, width, clockwise = true)
            }, width, height)
            270 -> Triple(rotate(luminance, width, height, clockwise = false), height, width)
            else -> Triple(luminance, width, height)
        }
    }

    private fun rotate(
        luminance: ByteArray,
        width: Int,
        height: Int,
        clockwise: Boolean
    ): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val source = y * width + x
                val target = if (clockwise) {
                    // (x, y) → (height - 1 - y, x) in the rotated frame
                    x * height + (height - 1 - y)
                } else {
                    // (x, y) → (y, width - 1 - x)
                    (width - 1 - x) * height + y
                }
                out[target] = luminance[source]
            }
        }
        return out
    }

    private fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        // Hybrid handles the uneven lighting of a photograph; global histogram is cheaper and
        // wins on the flat, high-contrast image of a screen. The inverted pass covers light
        // modules on a dark background, which is how every dark-mode wallet renders its code.
        //
        // Inversion happens on the *luminance source*, not on the binary bitmap: ZXing's
        // BinaryBitmap has no invert() of its own — it is the LuminanceSource that hands back
        // an inverted view of the same pixels (InvertedLuminanceSource), which each binarizer
        // then turns into its own black matrix. Two sources × two binarizers, four cheap
        // attempts, and the next frame is the retry.
        val source = PlanarYUVLuminanceSource(
            luminance, width, height, 0, 0, width, height, false
        )
        val attempts = listOf(
            { view: LuminanceSource -> BinaryBitmap(HybridBinarizer(view)) },
            { view: LuminanceSource -> BinaryBitmap(GlobalHistogramBinarizer(view)) }
        )
        for (attempt in attempts) {
            read(attempt(source))?.let { return it }
            read(attempt(source.invert()))?.let { return it }
        }
        return null
    }

    private fun read(bitmap: BinaryBitmap): String? = runCatching {
        val reader = MultiFormatReader()
        reader.setHints(HINTS)
        try {
            reader.decodeWithState(bitmap).text?.takeIf { it.isNotBlank() }
        } finally {
            reader.reset()
        }
    }.getOrNull()
}
