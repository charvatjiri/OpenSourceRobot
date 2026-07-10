package com.gabotapp

class CommandPlanner {
    fun plan(command: HighLevelCommand, state: RobotState): PlanningResult {
        if (!state.serialConnected) {
            return PlanningResult.Error("serial disconnected")
        }

        return when (command) {
            HighLevelCommand.Status -> PlanningResult.Complete("status")
            HighLevelCommand.Cancel -> PlanningResult.Complete("cancel")
            HighLevelCommand.Pause -> PlanningResult.Complete("pause")
            HighLevelCommand.Resume -> PlanningResult.Complete("resume")
            HighLevelCommand.Stop -> PlanningResult.Execute(
                CommandPlan(
                    label = "stop",
                    commands = SerialCommandExecutor.STOP_COMMANDS.map(::PlannedCommand)
                )
            )
            is HighLevelCommand.Look -> planLook(command)
            is HighLevelCommand.GoTo -> planVisionMovement(command, state, collect = false)
            is HighLevelCommand.Collect -> planVisionMovement(command, state, collect = true)
        }
    }

    private fun planLook(command: HighLevelCommand.Look): PlanningResult.Execute {
        if (command.direction == HighLevelCommand.Direction.CENTER) {
            return PlanningResult.Execute(
                CommandPlan(
                    label = "look center",
                    commands = listOf(PlannedCommand("shoulder horizontal 0"))
                )
            )
        }
        val speed = when (command.direction) {
            HighLevelCommand.Direction.LEFT -> -LOOK_SPEED
            HighLevelCommand.Direction.RIGHT -> LOOK_SPEED
            HighLevelCommand.Direction.CENTER -> 0
        }
        return PlanningResult.Execute(
            CommandPlan(
                label = "look ${command.direction.name.lowercase()}",
                commands = listOf(
                    PlannedCommand("shoulder horizontal $speed", LOOK_DURATION_MS),
                    PlannedCommand("shoulder horizontal 0")
                )
            )
        )
    }

    private fun planVisionMovement(
        command: HighLevelCommand,
        state: RobotState,
        collect: Boolean
    ): PlanningResult {
        if (!state.cameraAvailable) {
            return PlanningResult.Error("camera unavailable")
        }

        val vision = state.visionResult
        if (!vision.objectVisible || vision.confidence < MIN_CONFIDENCE) {
            if (state.searchAttempts >= MAX_SEARCH_ATTEMPTS) {
                return PlanningResult.Error("object not found after $MAX_SEARCH_ATTEMPTS attempts")
            }
            val direction = if (state.searchAttempts % 2 == 0) SEARCH_SPEED else -SEARCH_SPEED
            return PlanningResult.Execute(
                CommandPlan(
                    label = "search object",
                    commands = listOf(
                        PlannedCommand("wheels rl $direction", SEARCH_DURATION_MS),
                        PlannedCommand("wheels rl 0", CAMERA_SETTLE_MS)
                    ),
                    replanAfterCompletion = true,
                    countsAsSearchAttempt = true
                )
            )
        }

        if (vision.centerX < CENTER_LEFT) {
            return centeringPlan(-CENTERING_SPEED, "center left")
        }
        if (vision.centerX > CENTER_RIGHT) {
            return centeringPlan(CENTERING_SPEED, "center right")
        }

        val targetLabel = when (command) {
            is HighLevelCommand.Collect -> command.objectName
            is HighLevelCommand.GoTo -> command.objectName ?: command.target
            else -> "object"
        }
        val commands = mutableListOf(
            PlannedCommand("wheels rl 0"),
            PlannedCommand("wheels fb $APPROACH_SPEED", APPROACH_DURATION_MS),
            PlannedCommand("wheels fb 0")
        )
        if (collect) {
            commands += PlannedCommand("grab 0", GRAB_DURATION_MS)
            commands += PlannedCommand("grab 1")
        }
        return PlanningResult.Execute(
            CommandPlan(
                label = if (collect) "collect $targetLabel" else "goto $targetLabel",
                commands = commands
            )
        )
    }

    private fun centeringPlan(speed: Int, label: String) = PlanningResult.Execute(
        CommandPlan(
            label = label,
            commands = listOf(
                PlannedCommand("wheels rl $speed", CENTERING_DURATION_MS),
                PlannedCommand("wheels rl 0", CAMERA_SETTLE_MS)
            ),
            replanAfterCompletion = true
        )
    )

    companion object {
        const val MIN_CONFIDENCE = 0.25f
        const val CENTER_LEFT = 0.4f
        const val CENTER_RIGHT = 0.6f
        const val MAX_SEARCH_ATTEMPTS = 6

        private const val LOOK_SPEED = 40
        private const val SEARCH_SPEED = 15
        private const val CENTERING_SPEED = 15
        private const val APPROACH_SPEED = 15
        private const val LOOK_DURATION_MS = 400L
        private const val SEARCH_DURATION_MS = 350L
        private const val CENTERING_DURATION_MS = 250L
        private const val APPROACH_DURATION_MS = 500L
        private const val GRAB_DURATION_MS = 500L
        private const val CAMERA_SETTLE_MS = 250L
    }
}
