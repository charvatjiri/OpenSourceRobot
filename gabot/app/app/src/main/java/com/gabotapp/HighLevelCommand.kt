package com.gabotapp

sealed class HighLevelCommand {
    data class Collect(val objectName: String) : HighLevelCommand()
    data class GoTo(val target: String, val objectName: String?) : HighLevelCommand()
    data class Look(val direction: Direction) : HighLevelCommand()
    data object Status : HighLevelCommand()
    data object Cancel : HighLevelCommand()
    data object Pause : HighLevelCommand()
    data object Resume : HighLevelCommand()
    data object Stop : HighLevelCommand()

    enum class Direction {
        LEFT,
        RIGHT,
        CENTER
    }
}
