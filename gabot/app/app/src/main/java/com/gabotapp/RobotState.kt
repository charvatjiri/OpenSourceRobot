package com.gabotapp

data class RobotState(
    val serialConnected: Boolean,
    val bluetoothClientConnected: Boolean,
    val cameraAvailable: Boolean,
    val visionResult: VisionModule.Result,
    val activePlan: String? = null,
    val currentStep: Int = 0,
    val searchAttempts: Int = 0,
    val highLevelState: String = "IDLE",
    val lastSerialResponse: String? = null,
    val lastError: String? = null
)
