package com.gabotapp

import java.util.Locale

class HighLevelResponseFormatter {
    fun started(label: String): String = "INFO hl started ${tokenize(label)}"

    fun progress(step: Int, total: Int, command: String): String {
        return "INFO hl step $step/$total ${tokenize(command)}"
    }

    fun replan(label: String, searchAttempts: Int): String {
        return "INFO hl replan ${tokenize(label)} attempt=$searchAttempts"
    }

    fun success(message: String): String = "OK hl ${tokenize(message)}"

    fun error(message: String): String {
        val normalized = message.removePrefix("ERR:").trim()
        return "ERR hl ${tokenize(normalized)}"
    }

    fun status(state: RobotState, activeCommandLabel: String?): String {
        val vision = state.visionResult
        return listOf(
            "INFO status",
            "serial=${if (state.serialConnected) "connected" else "disconnected"}",
            "bluetooth=${if (state.bluetoothClientConnected) "connected" else "disconnected"}",
            "camera=${if (state.cameraAvailable) "available" else "unavailable"}",
            "active=${tokenize(activeCommandLabel)}",
            "state=${state.highLevelState}",
            "plan=${tokenize(state.activePlan)}",
            "step=${state.currentStep}",
            "searchAttempts=${state.searchAttempts}",
            "lastSerialResponse=${tokenize(state.lastSerialResponse)}",
            "lastError=${tokenize(state.lastError)}",
            "visionVisible=${vision.objectVisible}",
            "visionCenterX=${formatFloat(vision.centerX)}",
            "visionCenterY=${formatFloat(vision.centerY)}",
            "visionConfidence=${formatFloat(vision.confidence)}",
            "visionFrame=${vision.frameWidth}x${vision.frameHeight}"
        ).joinToString(" ")
    }

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.3f", value)

    private fun tokenize(value: String?): String {
        return value
            ?.trim()
            ?.ifEmpty { null }
            ?.replace(Regex("\\s+"), "_")
            ?: "none"
    }
}
