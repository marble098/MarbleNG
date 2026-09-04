package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_QR_CAMERA_V123
 *
 * The camera decode path is verified end to end against the product's own encoder: a payload is
 * encoded with [QrCode], rasterized into the exact byte layout a YUV_420_888 camera frame hands
 * over (a luminance plane, optionally with padded rows), and must come back out as the same text.
 *
 * That covers the three ways a real frame differs from a clean bitmap — row padding, sensor
 * rotation and a dark-on-light inversion — without a device, a camera or a fixture image.
 */
class QrFrameDecoderTest {

    private val payload = "vless://a1b2c3d4-0000-4000-8000-000000000000@1.2.3.4:443?security=reality#Frankfurt"

    /** Pixels per QR module; 4 keeps a phone-photo-sized symbol without inflating the test. */
    private val modulePixels = 4

    /** ISO/IEC 18004 quiet zone, in modules. */
    private val quietModules = 4

    private fun rasterize(
        code: QrCode,
        rowPadding: Int = 0,
        invert: Boolean = false,
        turns: Int = 0
    ): Raster {
        val edge = code.size
        val modules = edge + quietModules * 2
        var source = Array(modules) { row ->
            ByteArray(modules) { column ->
                val dark = row >= quietModules && column >= quietModules &&
                    row < edge + quietModules && column < edge + quietModules &&
                    code.isDark(row - quietModules, column - quietModules)
                val value = if (dark) 0.toByte() else 255.toByte()
                if (invert) (255 - (value.toInt() and 0xFF)).toByte() else value
            }
        }
        repeat(((turns % 4) + 4) % 4) { source = rotateClockwise(source) }

        val height = source.size
        val width = source[0].size
        val stride = width + rowPadding
        val bytes = ByteArray(stride * height)
        // Any padding byte is garbage the decoder must never read.
        bytes.fill(0x7F)
        for (row in 0 until height) {
            System.arraycopy(source[row], 0, bytes, row * stride, width)
        }
        return Raster(bytes, width, height, stride)
    }

    private fun rotateClockwise(input: Array<ByteArray>): Array<ByteArray> {
        val height = input.size
        val width = input[0].size
        return Array(width) { column ->
            ByteArray(height) { row -> input[height - 1 - row][column] }
        }
    }

    private data class Raster(val bytes: ByteArray, val width: Int, val height: Int, val stride: Int)

    @Test
    fun decodesARasterizedConfigLink() {
        val raster = rasterize(QrCode.encode(payload, QrEcc.M))
        val decoded = QrFrameDecoder.decode(raster.bytes, raster.width, raster.height)
        assertEquals(payload, decoded)
    }

    @Test
    fun ignoresPaddedCameraRows() {
        // Camera hardware aligns rows; the decoder must read `rowStride`, not `width`.
        val raster = rasterize(QrCode.encode(payload, QrEcc.M), rowPadding = 96)
        val decoded = QrFrameDecoder.decode(
            raster.bytes,
            raster.width,
            raster.height,
            rowStride = raster.stride
        )
        assertEquals(payload, decoded)
    }

    @Test
    fun decodesAnInvertedDarkModeCode() {
        val raster = rasterize(QrCode.encode(payload, QrEcc.M), invert = true)
        val decoded = QrFrameDecoder.decode(raster.bytes, raster.width, raster.height)
        assertEquals(payload, decoded)
    }

    @Test
    fun uprightsARotatedSensorFrame() {
        // A portrait phone delivers a landscape sensor image; the decoder rotates before reading.
        val raster = rasterize(QrCode.encode(payload, QrEcc.M), turns = 1)
        val decoded = QrFrameDecoder.decode(
            raster.bytes,
            raster.width,
            raster.height,
            sensorOrientation = 90
        )
        assertEquals(payload, decoded)
    }

    @Test
    fun compactLuminanceStripsRowPadding() {
        val raster = rasterize(QrCode.encode("hello", QrEcc.L), rowPadding = 32)
        val compact = QrFrameDecoder.compactLuminance(
            raster.bytes,
            raster.width,
            raster.height,
            raster.stride
        )
        assertEquals(raster.width * raster.height, compact.size)
        // The padding garbage must be gone: every byte is a real pixel of the symbol.
        assertTrue(compact.all { it == 0.toByte() || it == 255.toByte() })
    }

    @Test
    fun rotationPreservesEveryPixel() {
        val raster = rasterize(QrCode.encode("hello", QrEcc.L))
        val (rotated, width, height) = QrFrameDecoder.normalizeOrientation(
            raster.bytes, raster.width, raster.height, 90
        )
        assertEquals(raster.height, width)
        assertEquals(raster.width, height)
        assertEquals(raster.bytes.size, rotated.size)
        assertEquals(
            raster.bytes.sorted(),
            rotated.sorted()
        )
    }

    @Test
    fun rejectsAnOversizedClaimAboutTheBuffer() {
        // A frame whose buffer is shorter than the geometry claims must be refused, not read past
        // the end of the array.
        assertNull(QrFrameDecoder.decode(ByteArray(10), 64, 64))
        assertNull(QrFrameDecoder.decode(ByteArray(0), 0, 0))
    }
}
