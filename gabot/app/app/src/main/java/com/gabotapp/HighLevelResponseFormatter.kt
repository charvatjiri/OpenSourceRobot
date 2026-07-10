package com.gabotapp

import java.util.Locale

class HighLevelResponseFormatter {
    fun started(label: String): String = "INFO hl started ${token(label)}"

    fun progress(step: Int, total: Int, command: String): String =
        "INFO hl step $step/$total ${token(command)}"

    fun replan(label: String, attempt: Int): String =
        "INFO hl replan ${token(label)} attempt $attempt"

    fun success(message: String): String = "OK hl ${token(message)}"

    fun error(message: String): String = "ERR hl ${token(message)}"

    fun status(state: RobotState, activeCommand: String?): String {
        val vision = state.visionResult
        return listOf(
            "INFO status",
            "serial=${connected(state.serialConnected)}",
            "bluetooth=${connected(state.bluetoothClientConnected)}",
            "camera=${available(state.cameraAvailable)}",
            "active=${tokenOrNone(activeCommand)}",
            "state=${state.highLevelState}",
            "plan=${tokenOrNone(state.activePlan)}",
            "step=${state.currentStep}",
            "searchAttempts=${state.searchAttempts}",
            "lastSerialResponse=${tokenOrNone(state.lastSerialResponse)}",
            "lastError=${tokenOrNone(state.lastError)}",
            "visionVisible=${vision.objectVisible}",
            "visionCenterX=${formatFloat(vision.centerX)}",
            "visionCenterY=${formatFloat(vision.centerY)}",
            "visionConfidence=${formatFloat(vision.confidence)}",
            "visionFrame=${vision.frameWidth}x${vision.frameHeight}"
        ).joinToString(" ")
    }

    private fun connected(value: Boolean): String = if (value) "connected" else "disconnected"

    private fun available(value: Boolean): String = if (value) "available" else "unavailable"

    private fun tokenOrNone(value: String?): String = value?.let(::token) ?: "none"

    private fun token(value: String): String {
        val normalized = value.trim()
            .removePrefix("ERR:")
            .removePrefix("ERR")
            .trim()
        if (normalized.isBlank()) {
            return "none"
        }
        return normalized.replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_')
    }

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.3f", value)
}
