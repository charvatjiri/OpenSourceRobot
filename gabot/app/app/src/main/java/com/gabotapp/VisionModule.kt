package com.gabotapp

import androidx.camera.core.ImageAnalysis
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

interface VisionModule : AutoCloseable {
    data class Result(
        val objectVisible: Boolean,
        val centerX: Float,
        val centerY: Float,
        val confidence: Float,
        val frameWidth: Int,
        val frameHeight: Int,
        val timestampNanos: Long
    ) {
        companion object {
            val EMPTY = Result(false, 0.5f, 0.5f, 0f, 0, 0, 0L)
        }
    }

    val imageAnalysis: ImageAnalysis
    val latestResult: Result
}

class CameraXVisionModule(
    private val onResult: (VisionModule.Result) -> Unit = {}
) : VisionModule {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector = LumaObjectDetector()

    @Volatile
    private var currentResult = VisionModule.Result.EMPTY

    override val latestResult: VisionModule.Result
        get() = currentResult

    override val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    val yPlane = image.planes[0]
                    val detection = detector.analyze(
                        buffer = yPlane.buffer,
                        width = image.width,
                        height = image.height,
                        rowStride = yPlane.rowStride,
                        pixelStride = yPlane.pixelStride,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        timestampNanos = image.imageInfo.timestamp
                    )
                    currentResult = detection
                    onResult(detection)
                } finally {
                    image.close()
                }
            }
        }

    override fun close() {
        imageAnalysis.clearAnalyzer()
        analysisExecutor.shutdownNow()
    }
}

class LumaObjectDetector(
    private val sampleStep: Int = 2,
    private val minimumContrast: Double = 20.0,
    private val minimumCoverage: Double = 0.003,
    private val maximumCoverage: Double = 0.35
) {
    fun analyze(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int,
        timestampNanos: Long
    ): VisionModule.Result {
        if (width <= 1 || height <= 1 || rowStride <= 0 || pixelStride <= 0) {
            return VisionModule.Result.EMPTY.copy(timestampNanos = timestampNanos)
        }

        val pixels = buffer.duplicate()
        var sampleCount = 0
        var sum = 0.0
        var sumSquares = 0.0
        forEachSample(width, height) { x, y ->
            val luma = readLuma(pixels, x, y, rowStride, pixelStride) ?: return@forEachSample
            sampleCount++
            sum += luma
            sumSquares += luma * luma
        }
        if (sampleCount == 0) {
            return VisionModule.Result.EMPTY.copy(timestampNanos = timestampNanos)
        }

        val mean = sum / sampleCount
        val variance = max(0.0, sumSquares / sampleCount - mean * mean)
        val deviation = sqrt(variance)
        if (deviation < 6.0) {
            return emptyResult(width, height, timestampNanos)
        }

        val threshold = max(minimumContrast, deviation * 1.15)
        var objectSamples = 0
        var objectX = 0.0
        var objectY = 0.0
        var contrastSum = 0.0
        forEachSample(width, height) { x, y ->
            val luma = readLuma(pixels, x, y, rowStride, pixelStride) ?: return@forEachSample
            val contrast = abs(luma - mean)
            if (contrast >= threshold) {
                objectSamples++
                objectX += x
                objectY += y
                contrastSum += contrast
            }
        }

        val coverage = objectSamples.toDouble() / sampleCount
        if (objectSamples == 0 || coverage < minimumCoverage || coverage > maximumCoverage) {
            return emptyResult(width, height, timestampNanos)
        }

        val rawX = (objectX / objectSamples / (width - 1)).coerceIn(0.0, 1.0)
        val rawY = (objectY / objectSamples / (height - 1)).coerceIn(0.0, 1.0)
        val (centerX, centerY) = rotate(rawX, rawY, rotationDegrees)
        val averageContrast = contrastSum / objectSamples
        val contrastConfidence = ((averageContrast - threshold) / 64.0).coerceIn(0.0, 1.0)
        val coverageConfidence = (coverage / 0.08).coerceIn(0.0, 1.0)
        val confidence = (0.7 * contrastConfidence + 0.3 * coverageConfidence).toFloat()

        return VisionModule.Result(
            objectVisible = confidence >= 0.15f,
            centerX = centerX.toFloat(),
            centerY = centerY.toFloat(),
            confidence = confidence,
            frameWidth = width,
            frameHeight = height,
            timestampNanos = timestampNanos
        )
    }

    private inline fun forEachSample(width: Int, height: Int, block: (Int, Int) -> Unit) {
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                block(x, y)
                x += sampleStep
            }
            y += sampleStep
        }
    }

    private fun readLuma(
        buffer: ByteBuffer,
        x: Int,
        y: Int,
        rowStride: Int,
        pixelStride: Int
    ): Int? {
        val index = y * rowStride + x * pixelStride
        return if (index in 0 until buffer.limit()) buffer.get(index).toInt() and 0xff else null
    }

    private fun rotate(x: Double, y: Double, rotationDegrees: Int): Pair<Double, Double> =
        when ((rotationDegrees % 360 + 360) % 360) {
            90 -> Pair(1.0 - y, x)
            180 -> Pair(1.0 - x, 1.0 - y)
            270 -> Pair(y, 1.0 - x)
            else -> Pair(x, y)
        }

    private fun emptyResult(width: Int, height: Int, timestampNanos: Long) =
        VisionModule.Result.EMPTY.copy(
            frameWidth = width,
            frameHeight = height,
            timestampNanos = timestampNanos
        )
}
