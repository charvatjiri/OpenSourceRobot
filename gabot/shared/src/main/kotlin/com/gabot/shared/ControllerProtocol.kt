package com.gabot.shared

object ControllerCommands {
    const val GRAB_START = "grab 1"
    const val GRAB_STOP = "grab 0"
    const val RELEASE_START = "release 1"
    const val RELEASE_STOP = "release 0"

    const val ARM_UP = "shoulder vertical -50"
    const val ARM_DOWN = "shoulder vertical 30"
    const val ARM_VERTICAL_STOP = "shoulder vertical 0"
    const val ARM_LEFT = "shoulder horizontal -40"
    const val ARM_RIGHT = "shoulder horizontal 40"
    const val ARM_HORIZONTAL_STOP = "shoulder horizontal 0"

    const val WHEELS_FORWARD = "wheels fb 15"
    const val WHEELS_BACK = "wheels fb -15"
    const val WHEELS_FB_STOP = "wheels fb 0"
    const val WHEELS_LEFT = "wheels rl -15"
    const val WHEELS_RIGHT = "wheels rl 15"
    const val WHEELS_RL_STOP = "wheels rl 0"

    fun wristHorizontal(position: Int): String = "wrist horizontal $position"

    fun wristVertical(position: Int): String = "wrist vertical $position"
}

data class WristPosition(
    val horizontal: Int = 80,
    val vertical: Int = 100
) {
    fun moveHorizontal(delta: Int): WristPosition = copy(
        horizontal = (horizontal + delta).coerceIn(HORIZONTAL_MIN, HORIZONTAL_MAX)
    )

    fun moveVertical(delta: Int): WristPosition = copy(
        vertical = (vertical + delta).coerceIn(VERTICAL_MIN, VERTICAL_MAX)
    )

    companion object {
        const val HORIZONTAL_MIN = 10
        const val HORIZONTAL_MAX = 150
        const val VERTICAL_MIN = 50
        const val VERTICAL_MAX = 150
        const val STEP_DEGREES = 1
        const val STEP_INTERVAL_MS = 20L
    }
}
