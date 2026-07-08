package com.gabotapp

import java.nio.ByteBuffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LumaObjectDetectorTest {
    private val detector = LumaObjectDetector(sampleStep = 1)

    @Test
    fun uniformFrameHasNoVisibleObject() {
        val result = analyze(ByteArray(100 * 80) { 100 }, width = 100, height = 80)

        assertFalse(result.objectVisible)
        assertTrue(result.confidence == 0f)
    }

    @Test
    fun brightRegionIsDetectedAtNormalizedCenter() {
        val width = 100
        val height = 80
        val frame = frameWithBrightRegion(width, height, 60 until 80, 20 until 40)

        val result = analyze(frame, width, height)

        assertTrue(result.objectVisible)
        assertTrue(result.centerX in 0.68f..0.72f)
        assertTrue(result.centerY in 0.36f..0.39f)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun rotationTransformsObjectCoordinates() {
        val width = 100
        val height = 80
        val frame = frameWithBrightRegion(width, height, 10 until 30, 8 until 24)

        val result = analyze(frame, width, height, rotationDegrees = 90)

        assertTrue(result.objectVisible)
        assertTrue(result.centerX in 0.80f..0.82f)
        assertTrue(result.centerY in 0.19f..0.21f)
    }

    private fun frameWithBrightRegion(
        width: Int,
        height: Int,
        xRange: IntRange,
        yRange: IntRange
    ) = ByteArray(width * height) { 30 }.also { frame ->
        for (y in yRange) {
            for (x in xRange) {
                frame[y * width + x] = 230.toByte()
            }
        }
    }

    private fun analyze(
        frame: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0
    ) = detector.analyze(
        buffer = ByteBuffer.wrap(frame),
        width = width,
        height = height,
        rowStride = width,
        pixelStride = 1,
        rotationDegrees = rotationDegrees,
        timestampNanos = 1L
    )
}
