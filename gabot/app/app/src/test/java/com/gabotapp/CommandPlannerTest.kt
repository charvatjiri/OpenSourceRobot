package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPlannerTest {
    private val planner = CommandPlanner()

    @Test
    fun plansSearchWhenObjectIsNotVisible() {
        val result = execute(HighLevelCommand.GoTo("visible_object", null), state(visible = false))

        assertTrue(result.plan.replanAfterCompletion)
        assertTrue(result.plan.countsAsSearchAttempt)
        assertEquals("wheels rl 15", result.plan.commands.first().command)
        assertEquals("wheels rl 0", result.plan.commands.last().command)
    }

    @Test
    fun alternatesSearchAndStopsAfterMaximumAttempts() {
        val alternate = execute(
            HighLevelCommand.GoTo("visible_object", null),
            state(visible = false, attempts = 1)
        )
        assertEquals("wheels rl -15", alternate.plan.commands.first().command)

        val exhausted = planner.plan(
            HighLevelCommand.GoTo("visible_object", null),
            state(visible = false, attempts = CommandPlanner.MAX_SEARCH_ATTEMPTS)
        )
        assertTrue(exhausted is PlanningResult.Error)
    }

    @Test
    fun centersObjectBeforeApproach() {
        val left = execute(HighLevelCommand.GoTo("visible_object", null), state(centerX = 0.2f))
        val right = execute(HighLevelCommand.GoTo("visible_object", null), state(centerX = 0.8f))

        assertEquals("wheels rl -15", left.plan.commands.first().command)
        assertEquals("wheels rl 15", right.plan.commands.first().command)
        assertTrue(left.plan.replanAfterCompletion)
        assertTrue(right.plan.replanAfterCompletion)
    }

    @Test
    fun plansApproachAndCollectionForCenteredObject() {
        val goto = execute(HighLevelCommand.GoTo("visible_object", "apple"), state(centerX = 0.5f))
        val collect = execute(HighLevelCommand.Collect("apple"), state(centerX = 0.5f))

        assertFalse(goto.plan.replanAfterCompletion)
        assertEquals("wheels fb 15", goto.plan.commands[1].command)
        assertEquals("grab 0", collect.plan.commands[3].command)
        assertEquals("grab 1", collect.plan.commands[4].command)
    }

    @Test
    fun rejectsAutonomyWithoutCameraOrSerial() {
        val command = HighLevelCommand.GoTo("visible_object", null)

        assertTrue(planner.plan(command, state(cameraAvailable = false)) is PlanningResult.Error)
        assertTrue(planner.plan(command, state(serialConnected = false)) is PlanningResult.Error)
    }

    @Test
    fun lowConfidenceResultUsesSearchPlan() {
        val lowConfidenceState = state().copy(
            visionResult = state().visionResult.copy(confidence = 0.1f)
        )

        val result = execute(HighLevelCommand.Collect("apple"), lowConfidenceState)

        assertTrue(result.plan.countsAsSearchAttempt)
        assertTrue(result.plan.replanAfterCompletion)
    }

    @Test
    fun lookPlanTurnsShoulderAndStopsIt() {
        val result = execute(
            HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT),
            state(cameraAvailable = false)
        )

        assertEquals("shoulder horizontal 40", result.plan.commands.first().command)
        assertEquals("shoulder horizontal 0", result.plan.commands.last().command)
    }

    private fun execute(command: HighLevelCommand, state: RobotState): PlanningResult.Execute {
        val result = planner.plan(command, state)
        assertTrue(result is PlanningResult.Execute)
        return result as PlanningResult.Execute
    }

    private fun state(
        serialConnected: Boolean = true,
        cameraAvailable: Boolean = true,
        visible: Boolean = true,
        centerX: Float = 0.5f,
        attempts: Int = 0
    ) = RobotState(
        serialConnected = serialConnected,
        bluetoothClientConnected = true,
        cameraAvailable = cameraAvailable,
        visionResult = VisionModule.Result(
            objectVisible = visible,
            centerX = centerX,
            centerY = 0.5f,
            confidence = if (visible) 0.9f else 0f,
            frameWidth = 640,
            frameHeight = 480,
            timestampNanos = 1L
        ),
        searchAttempts = attempts
    )
}
