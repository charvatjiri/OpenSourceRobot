package com.gabotapp

import androidx.camera.core.ImageAnalysis
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface VisionModule : AutoCloseable {
    data class FrameMetadata(
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val timestampNanos: Long
    )

    val imageAnalysis: ImageAnalysis
    val latestFrame: FrameMetadata?
}

class CameraXVisionModule : VisionModule {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentFrame: VisionModule.FrameMetadata? = null

    override val latestFrame: VisionModule.FrameMetadata?
        get() = currentFrame

    override val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also { analysis ->
            analysis.setAnalyzer(analysisExecutor) { image ->
                try {
                    currentFrame = VisionModule.FrameMetadata(
                        width = image.width,
                        height = image.height,
                        rotationDegrees = image.imageInfo.rotationDegrees,
                        timestampNanos = image.imageInfo.timestamp
                    )
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
