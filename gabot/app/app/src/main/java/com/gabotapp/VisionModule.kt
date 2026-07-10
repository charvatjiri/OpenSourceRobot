package com.gabotapp

import androidx.camera.core.ImageAnalysis
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

interface VisionModule : AutoCloseable {
    data class Result(
        val objectVisible: Boolean,
        val objectName: String?,
        val centerX: Float,
        val centerY: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
        val identityConfidence: Float,
        val frameWidth: Int,
        val frameHeight: Int,
        val timestampNanos: Long
    ) {
        companion object {
            val EMPTY = Result(false, null, 0.5f, 0.5f, 0f, 0f, 0f, 0f, 0, 0, 0L)
        }
    }

    val imageAnalysis: ImageAnalysis
    val latestResult: Result
}

class CameraXVisionModule(
    private val onResult: (VisionModule.Result) -> Unit = {}
) : VisionModule {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detector = ColorProfileObjectDetector()

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
                    val uPlane = image.planes[1]
                    val vPlane = image.planes[2]
                    val detection = detector.analyze(
                        yBuffer = yPlane.buffer,
                        uBuffer = uPlane.buffer,
                        vBuffer = vPlane.buffer,
                        width = image.width,
                        height = image.height,
                        yRowStride = yPlane.rowStride,
                        yPixelStride = yPlane.pixelStride,
                        uRowStride = uPlane.rowStride,
                        uPixelStride = uPlane.pixelStride,
                        vRowStride = vPlane.rowStride,
                        vPixelStride = vPlane.pixelStride,
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

data class ColorProfile(
    val name: String,
    val matcher: (red: Int, green: Int, blue: Int) -> Double
)

class ColorProfileObjectDetector(
    private val profiles: List<ColorProfile> = DEFAULT_PROFILES,
    private val sampleStep: Int = 2,
    private val minimumCoverage: Double = 0.002,
    private val maximumCoverage: Double = 0.45,
    private val minimumIdentityConfidence: Double = 0.20
) {
    fun analyze(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        yPixelStride: Int,
        uRowStride: Int,
        uPixelStride: Int,
        vRowStride: Int,
        vPixelStride: Int,
        rotationDegrees: Int,
        timestampNanos: Long
    ): VisionModule.Result {
        if (width <= 1 || height <= 1 || profiles.isEmpty()) {
            return VisionModule.Result.EMPTY.copy(timestampNanos = timestampNanos)
        }

        val yPixels = yBuffer.duplicate()
        val uPixels = uBuffer.duplicate()
        val vPixels = vBuffer.duplicate()
        val detections = profiles.associateWith { Accumulator() }
        var sampleCount = 0

        forEachSample(width, height) { x, y ->
            val yValue = readPlane(yPixels, x, y, yRowStride, yPixelStride) ?: return@forEachSample
            val uValue = readPlane(uPixels, x / 2, y / 2, uRowStride, uPixelStride) ?: return@forEachSample
            val vValue = readPlane(vPixels, x / 2, y / 2, vRowStride, vPixelStride) ?: return@forEachSample
            val (red, green, blue) = yuvToRgb(yValue, uValue, vValue)
            sampleCount++
            profiles.forEach { profile ->
                val score = profile.matcher(red, green, blue)
                if (score > 0.0) {
                    detections.getValue(profile).add(x, y, score)
                }
            }
        }

        if (sampleCount == 0) {
            return VisionModule.Result.EMPTY.copy(timestampNanos = timestampNanos)
        }

        val best = detections.entries.maxByOrNull { it.value.identityScore(sampleCount) }
            ?: return emptyResult(width, height, timestampNanos)
        val accumulator = best.value
        val coverage = accumulator.count.toDouble() / sampleCount
        if (
            accumulator.count == 0 ||
            coverage < minimumCoverage ||
            coverage > maximumCoverage
        ) {
            return emptyResult(width, height, timestampNanos)
        }

        val identityConfidence = accumulator.identityScore(sampleCount)
        if (identityConfidence < minimumIdentityConfidence) {
            return emptyResult(width, height, timestampNanos)
        }

        val rawX = (accumulator.sumX / accumulator.count / (width - 1)).coerceIn(0.0, 1.0)
        val rawY = (accumulator.sumY / accumulator.count / (height - 1)).coerceIn(0.0, 1.0)
        val rawWidth = ((accumulator.maxX - accumulator.minX + 1).toDouble() / width).coerceIn(0.0, 1.0)
        val rawHeight = ((accumulator.maxY - accumulator.minY + 1).toDouble() / height).coerceIn(0.0, 1.0)
        val (centerX, centerY) = rotate(rawX, rawY, rotationDegrees)
        val (objectWidth, objectHeight) = rotateSize(rawWidth, rawHeight, rotationDegrees)
        val coverageConfidence = (coverage / 0.08).coerceIn(0.0, 1.0)
        val confidence = (0.65 * identityConfidence + 0.35 * coverageConfidence).coerceIn(0.0, 1.0)

        return VisionModule.Result(
            objectVisible = true,
            objectName = best.key.name,
            centerX = centerX.toFloat(),
            centerY = centerY.toFloat(),
            width = objectWidth.toFloat(),
            height = objectHeight.toFloat(),
            confidence = confidence.toFloat(),
            identityConfidence = identityConfidence.toFloat(),
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

    private fun readPlane(
        buffer: ByteBuffer,
        x: Int,
        y: Int,
        rowStride: Int,
        pixelStride: Int
    ): Int? {
        if (rowStride <= 0 || pixelStride <= 0) {
            return null
        }
        val index = y * rowStride + x * pixelStride
        return if (index in 0 until buffer.limit()) buffer.get(index).toInt() and 0xff else null
    }

    private fun yuvToRgb(yValue: Int, uValue: Int, vValue: Int): Triple<Int, Int, Int> {
        val y = yValue.toDouble()
        val u = uValue.toDouble() - 128.0
        val v = vValue.toDouble() - 128.0
        val red = (y + 1.402 * v).roundToByte()
        val green = (y - 0.344136 * u - 0.714136 * v).roundToByte()
        val blue = (y + 1.772 * u).roundToByte()
        return Triple(red, green, blue)
    }

    private fun rotate(x: Double, y: Double, rotationDegrees: Int): Pair<Double, Double> =
        when ((rotationDegrees % 360 + 360) % 360) {
            90 -> Pair(1.0 - y, x)
            180 -> Pair(1.0 - x, 1.0 - y)
            270 -> Pair(y, 1.0 - x)
            else -> Pair(x, y)
        }

    private fun rotateSize(width: Double, height: Double, rotationDegrees: Int): Pair<Double, Double> =
        when ((rotationDegrees % 360 + 360) % 360) {
            90, 270 -> Pair(height, width)
            else -> Pair(width, height)
        }

    private fun Double.roundToByte(): Int = toInt().coerceIn(0, 255)

    private fun emptyResult(width: Int, height: Int, timestampNanos: Long) =
        VisionModule.Result.EMPTY.copy(
            frameWidth = width,
            frameHeight = height,
            timestampNanos = timestampNanos
        )

    private class Accumulator {
        var count = 0
            private set
        var sumX = 0.0
            private set
        var sumY = 0.0
            private set
        var scoreSum = 0.0
            private set
        var minX = Int.MAX_VALUE
            private set
        var minY = Int.MAX_VALUE
            private set
        var maxX = Int.MIN_VALUE
            private set
        var maxY = Int.MIN_VALUE
            private set

        fun add(x: Int, y: Int, score: Double) {
            count++
            sumX += x
            sumY += y
            scoreSum += score
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        fun identityScore(sampleCount: Int): Double {
            if (count == 0 || sampleCount == 0) {
                return 0.0
            }
            val averageScore = scoreSum / count
            val coverage = count.toDouble() / sampleCount
            return (0.8 * averageScore + 0.2 * (coverage / 0.08).coerceIn(0.0, 1.0))
                .coerceIn(0.0, 1.0)
        }
    }

    companion object {
        val DEFAULT_PROFILES = listOf(
            ColorProfile("apple_red") { red, green, blue ->
                if (red >= 90 && red - green >= 35 && red - blue >= 30) {
                    ((red - max(green, blue)).toDouble() / 140.0).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
            },
            ColorProfile("cube_blue") { red, green, blue ->
                if (blue >= 85 && blue - red >= 30 && blue - green >= 25) {
                    ((blue - max(red, green)).toDouble() / 140.0).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
            },
            ColorProfile("marker_green") { red, green, blue ->
                if (green >= 85 && green - red >= 25 && green - blue >= 25) {
                    ((green - max(red, blue)).toDouble() / 140.0).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
            }
        )
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
            objectName = null,
            centerX = centerX.toFloat(),
            centerY = centerY.toFloat(),
            width = 0f,
            height = 0f,
            confidence = confidence,
            identityConfidence = 0f,
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
