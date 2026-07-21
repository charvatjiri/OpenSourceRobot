package com.gabotapp

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorProfileObjectDetectorTest {
    private val detector = ColorProfileObjectDetector(sampleStep = 1)

    @Test
    fun redRegionIsDetectedAsAppleProfile() {
        val frame = yuvFrame(
            width = 80,
            height = 60,
            background = Rgb(45, 45, 45),
            region = Region(48 until 64, 18 until 30, Rgb(210, 30, 35))
        )

        val result = analyze(frame)

        assertTrue(result.objectVisible)
        assertEquals("apple_red", result.objectName)
        assertTrue(result.centerX in 0.69f..0.72f)
        assertTrue(result.centerY in 0.39f..0.41f)
        assertTrue(result.width in 0.18f..0.22f)
        assertTrue(result.height in 0.18f..0.22f)
        assertTrue(result.identityConfidence > 0.7f)
    }

    @Test
    fun blueRegionIsDetectedAsCubeProfile() {
        val frame = yuvFrame(
            width = 80,
            height = 60,
            background = Rgb(45, 45, 45),
            region = Region(12 until 28, 20 until 38, Rgb(35, 55, 220))
        )

        val result = analyze(frame)

        assertTrue(result.objectVisible)
        assertEquals("cube_blue", result.objectName)
        assertTrue(result.centerX in 0.24f..0.26f)
        assertTrue(result.centerY in 0.48f..0.50f)
        assertTrue(result.identityConfidence > 0.7f)
    }

    @Test
    fun neutralFrameHasNoColorProfileDetection() {
        val frame = yuvFrame(
            width = 80,
            height = 60,
            background = Rgb(90, 92, 91),
            region = null
        )

        val result = analyze(frame)

        assertFalse(result.objectVisible)
        assertEquals(null, result.objectName)
    }

    private fun analyze(frame: YuvFrame) = detector.analyze(
        yBuffer = ByteBuffer.wrap(frame.y),
        uBuffer = ByteBuffer.wrap(frame.u),
        vBuffer = ByteBuffer.wrap(frame.v),
        width = frame.width,
        height = frame.height,
        yRowStride = frame.width,
        yPixelStride = 1,
        uRowStride = frame.width / 2,
        uPixelStride = 1,
        vRowStride = frame.width / 2,
        vPixelStride = 1,
        rotationDegrees = 0,
        timestampNanos = 1L
    )

    private fun yuvFrame(
        width: Int,
        height: Int,
        background: Rgb,
        region: Region?
    ): YuvFrame {
        val y = ByteArray(width * height)
        val u = ByteArray((width / 2) * (height / 2))
        val v = ByteArray((width / 2) * (height / 2))
        for (py in 0 until height) {
            for (px in 0 until width) {
                val rgb = if (region?.contains(px, py) == true) region.rgb else background
                val yuv = rgb.toYuv()
                y[py * width + px] = yuv.y.toByte()
            }
        }
        for (py in 0 until height / 2) {
            for (px in 0 until width / 2) {
                val sourceX = px * 2
                val sourceY = py * 2
                val rgb = if (region?.contains(sourceX, sourceY) == true) region.rgb else background
                val yuv = rgb.toYuv()
                u[py * (width / 2) + px] = yuv.u.toByte()
                v[py * (width / 2) + px] = yuv.v.toByte()
            }
        }
        return YuvFrame(width, height, y, u, v)
    }

    private data class YuvFrame(
        val width: Int,
        val height: Int,
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray
    )

    private data class Region(
        val xRange: IntRange,
        val yRange: IntRange,
        val rgb: Rgb
    ) {
        fun contains(x: Int, y: Int): Boolean = x in xRange && y in yRange
    }

    private data class Rgb(val red: Int, val green: Int, val blue: Int) {
        fun toYuv(): Yuv {
            val y = (0.299 * red + 0.587 * green + 0.114 * blue).toInt().coerceIn(0, 255)
            val u = ((blue - y) * 0.565 + 128).toInt().coerceIn(0, 255)
            val v = ((red - y) * 0.713 + 128).toInt().coerceIn(0, 255)
            return Yuv(y, u, v)
        }
    }

    private data class Yuv(val y: Int, val u: Int, val v: Int)
}
