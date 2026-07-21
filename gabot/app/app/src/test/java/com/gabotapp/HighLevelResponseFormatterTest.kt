package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Test

class HighLevelResponseFormatterTest {
    private val formatter = HighLevelResponseFormatter()

    @Test
    fun formatsLifecycleResponses() {
        assertEquals("INFO hl started collect_apple", formatter.started("collect apple"))
        assertEquals(
            "INFO hl step 2/5 wheels_fb_15",
            formatter.progress(2, 5, "wheels fb 15")
        )
        assertEquals(
            "INFO hl replan search_object attempt=3",
            formatter.replan("search object", 3)
        )
        assertEquals("OK hl goto_apple", formatter.success("goto apple"))
        assertEquals("ERR hl camera_unavailable", formatter.error("ERR: camera unavailable"))
    }

    @Test
    fun formatsStatusResponse() {
        val status = formatter.status(
            state = RobotState(
                serialConnected = true,
                bluetoothClientConnected = false,
                cameraAvailable = true,
                visionResult = VisionModule.Result(
                    objectVisible = true,
                    centerX = 0.25f,
                    centerY = 0.75f,
                    confidence = 0.8f,
                    frameWidth = 640,
                    frameHeight = 480,
                    timestampNanos = 1L
                ),
                activePlan = "look left",
                currentStep = 2,
                searchAttempts = 1,
                highLevelState = "RUNNING",
                lastSerialResponse = "OK motor 80",
                lastError = null
            ),
            activeCommandLabel = "collect apple"
        )

        assertEquals(
            "INFO status serial=connected bluetooth=disconnected camera=available " +
                "active=collect_apple state=RUNNING plan=look_left step=2 searchAttempts=1 " +
                "lastSerialResponse=OK_motor_80 lastError=none visionVisible=true " +
                "visionCenterX=0.250 visionCenterY=0.750 visionConfidence=0.800 visionFrame=640x480",
            status
        )
    }
}
