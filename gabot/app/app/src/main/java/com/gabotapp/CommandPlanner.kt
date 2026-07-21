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
            is HighLevelCommand.Collect -> planCollect(command, state)
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
        if (!hasUsableVision(state, targetName(command))) {
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

    private fun planCollect(command: HighLevelCommand.Collect, state: RobotState): PlanningResult {
        if (!state.cameraAvailable) {
            return PlanningResult.Error("camera unavailable")
        }

        return when (state.collectStage ?: CollectStage.SEARCH_OBJECT) {
            CollectStage.SEARCH_OBJECT -> planCollectSearch(command.objectName, state)
            CollectStage.CENTER_OBJECT -> planCollectCenter(command.objectName, state)
            CollectStage.APPROACH_OBJECT -> planCollectApproach(command.objectName, state)
            CollectStage.VERIFY_OBJECT -> planCollectVerify(command.objectName, state)
            CollectStage.GRAB_OBJECT -> planCollectGrab(command)
            CollectStage.DONE -> PlanningResult.Complete("collect ${command.objectName}")
        }
    }

    private fun planCollectSearch(targetName: String, state: RobotState): PlanningResult {
        if (hasUsableVision(state, targetName)) {
            return transitionCollectStage(
                label = "collect search object found",
                nextStage = CollectStage.CENTER_OBJECT
            )
        }
        if (state.searchAttempts >= MAX_SEARCH_ATTEMPTS) {
            return PlanningResult.Error("object not found after $MAX_SEARCH_ATTEMPTS attempts")
        }
        val direction = if (state.searchAttempts % 2 == 0) SEARCH_SPEED else -SEARCH_SPEED
        return PlanningResult.Execute(
            CommandPlan(
                label = "collect search object",
                commands = listOf(
                    PlannedCommand("wheels rl $direction", SEARCH_DURATION_MS),
                    PlannedCommand("wheels rl 0", CAMERA_SETTLE_MS)
                ),
                replanAfterCompletion = true,
                countsAsSearchAttempt = true,
                collectStageAfterCompletion = CollectStage.SEARCH_OBJECT
            )
        )
    }

    private fun planCollectCenter(targetName: String, state: RobotState): PlanningResult {
        if (!hasUsableVision(state, targetName)) {
            return transitionCollectStage(
                label = "collect lost object",
                nextStage = CollectStage.SEARCH_OBJECT
            )
        }
        if (isCentered(state.visionResult)) {
            return transitionCollectStage(
                label = "collect object centered",
                nextStage = CollectStage.APPROACH_OBJECT
            )
        }
        if (state.collectCenterAttempts >= MAX_COLLECT_CENTER_ATTEMPTS) {
            return PlanningResult.Error(
                "collect center iteration limit reached after $MAX_COLLECT_CENTER_ATTEMPTS attempts"
            )
        }
        return if (state.visionResult.centerX < CENTER_LEFT) {
            collectCenteringPlan(-CENTERING_SPEED, "collect center left")
        } else {
            collectCenteringPlan(CENTERING_SPEED, "collect center right")
        }
    }

    private fun planCollectApproach(targetName: String, state: RobotState): PlanningResult {
        if (!hasUsableVision(state, targetName)) {
            return transitionCollectStage(
                label = "collect lost object before approach",
                nextStage = CollectStage.SEARCH_OBJECT
            )
        }
        if (!isCentered(state.visionResult)) {
            return transitionCollectStage(
                label = "collect object off center",
                nextStage = CollectStage.CENTER_OBJECT
            )
        }
        if (state.collectApproachAttempts >= MAX_COLLECT_APPROACH_ATTEMPTS) {
            return PlanningResult.Error(
                "collect approach iteration limit reached after $MAX_COLLECT_APPROACH_ATTEMPTS attempts"
            )
        }
        return PlanningResult.Execute(
            CommandPlan(
                label = "collect approach object",
                commands = listOf(
                    PlannedCommand("wheels fb $APPROACH_SPEED", COLLECT_APPROACH_DURATION_MS),
                    PlannedCommand("wheels fb 0", CAMERA_SETTLE_MS)
                ),
                replanAfterCompletion = true,
                collectStageAfterCompletion = CollectStage.VERIFY_OBJECT,
                countsAsCollectApproachAttempt = true
            )
        )
    }

    private fun planCollectVerify(targetName: String, state: RobotState): PlanningResult {
        if (state.collectVerifyAttempts >= MAX_COLLECT_VERIFY_ATTEMPTS) {
            return PlanningResult.Error(
                "collect verify iteration limit reached after $MAX_COLLECT_VERIFY_ATTEMPTS attempts"
            )
        }
        if (!hasUsableVision(state, targetName)) {
            return transitionCollectStage(
                label = "collect verify lost object",
                nextStage = CollectStage.SEARCH_OBJECT,
                countsAsVerifyAttempt = true
            )
        }
        if (!isCentered(state.visionResult)) {
            return transitionCollectStage(
                label = "collect verify off center",
                nextStage = CollectStage.CENTER_OBJECT,
                countsAsVerifyAttempt = true
            )
        }
        return transitionCollectStage(
            label = "collect verify object",
            nextStage = CollectStage.GRAB_OBJECT,
            countsAsVerifyAttempt = true
        )
    }

    private fun planCollectGrab(command: HighLevelCommand.Collect): PlanningResult.Execute =
        PlanningResult.Execute(
            CommandPlan(
                label = "collect grab ${command.objectName}",
                commands = listOf(
                    PlannedCommand("wheels fb 0"),
                    PlannedCommand("wheels rl 0"),
                    PlannedCommand("grab 0", GRAB_DURATION_MS),
                    PlannedCommand("grab 1")
                ),
                replanAfterCompletion = true,
                collectStageAfterCompletion = CollectStage.DONE
            )
        )

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

    private fun collectCenteringPlan(speed: Int, label: String) = PlanningResult.Execute(
        CommandPlan(
            label = label,
            commands = listOf(
                PlannedCommand("wheels rl $speed", CENTERING_DURATION_MS),
                PlannedCommand("wheels rl 0", CAMERA_SETTLE_MS)
            ),
            replanAfterCompletion = true,
            collectStageAfterCompletion = CollectStage.CENTER_OBJECT,
            countsAsCollectCenterAttempt = true
        )
    )

    private fun transitionCollectStage(
        label: String,
        nextStage: CollectStage,
        countsAsVerifyAttempt: Boolean = false
    ) = PlanningResult.Execute(
        CommandPlan(
            label = label,
            commands = emptyList(),
            replanAfterCompletion = true,
            collectStageAfterCompletion = nextStage,
            countsAsCollectVerifyAttempt = countsAsVerifyAttempt
        )
    )

    private fun hasUsableVision(state: RobotState, targetName: String?): Boolean =
        state.visionResult.objectVisible &&
            state.visionResult.confidence >= MIN_CONFIDENCE &&
            targetMatchesVision(targetName, state.visionResult.objectName)

    private fun isCentered(vision: VisionModule.Result): Boolean =
        vision.centerX >= CENTER_LEFT && vision.centerX <= CENTER_RIGHT

    private fun targetName(command: HighLevelCommand): String? = when (command) {
        is HighLevelCommand.Collect -> command.objectName
        is HighLevelCommand.GoTo -> command.objectName ?: command.target
        else -> null
    }

    private fun targetMatchesVision(targetName: String?, detectedName: String?): Boolean {
        if (targetName.isNullOrBlank() || detectedName.isNullOrBlank()) {
            return true
        }
        val expectedProfile = targetToProfile(targetName) ?: return true
        return detectedName.equals(expectedProfile, ignoreCase = true)
    }

    private fun targetToProfile(targetName: String): String? =
        when (targetName.trim().lowercase()) {
            "object", "visible_object", "target", "any" -> null
            "apple", "red", "apple_red" -> "apple_red"
            "cube", "blue", "cube_blue" -> "cube_blue"
            "marker", "green", "marker_green" -> "marker_green"
            else -> targetName.trim().lowercase()
        }

    companion object {
        const val MIN_CONFIDENCE = 0.25f
        const val CENTER_LEFT = 0.4f
        const val CENTER_RIGHT = 0.6f
        const val MAX_SEARCH_ATTEMPTS = 6
        const val MAX_COLLECT_CENTER_ATTEMPTS = 8
        const val MAX_COLLECT_APPROACH_ATTEMPTS = 4
        const val MAX_COLLECT_VERIFY_ATTEMPTS = 4

        private const val LOOK_SPEED = 40
        private const val SEARCH_SPEED = 15
        private const val CENTERING_SPEED = 15
        private const val APPROACH_SPEED = 15
        private const val LOOK_DURATION_MS = 400L
        private const val SEARCH_DURATION_MS = 350L
        private const val CENTERING_DURATION_MS = 250L
        private const val APPROACH_DURATION_MS = 500L
        private const val COLLECT_APPROACH_DURATION_MS = 350L
        private const val GRAB_DURATION_MS = 500L
        private const val CAMERA_SETTLE_MS = 250L
    }
}
