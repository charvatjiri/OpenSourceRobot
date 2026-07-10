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
    fun plansGotoApproachForCenteredObject() {
        val goto = execute(HighLevelCommand.GoTo("visible_object", "apple"), state(centerX = 0.5f))

        assertFalse(goto.plan.replanAfterCompletion)
        assertEquals("wheels fb 15", goto.plan.commands[1].command)
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
    fun collectAdvancesThroughNamedStages() {
        val search = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.SEARCH_OBJECT)
        )
        assertEquals("collect search object found", search.plan.label)
        assertEquals(CollectStage.CENTER_OBJECT, search.plan.collectStageAfterCompletion)
        assertTrue(search.plan.replanAfterCompletion)

        val center = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.CENTER_OBJECT)
        )
        assertEquals("collect object centered", center.plan.label)
        assertEquals(CollectStage.APPROACH_OBJECT, center.plan.collectStageAfterCompletion)

        val approach = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.APPROACH_OBJECT)
        )
        assertEquals("collect approach object", approach.plan.label)
        assertEquals(CollectStage.VERIFY_OBJECT, approach.plan.collectStageAfterCompletion)
        assertTrue(approach.plan.countsAsCollectApproachAttempt)

        val verify = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.VERIFY_OBJECT)
        )
        assertEquals("collect verify object", verify.plan.label)
        assertEquals(CollectStage.GRAB_OBJECT, verify.plan.collectStageAfterCompletion)
        assertTrue(verify.plan.countsAsCollectVerifyAttempt)

        val grab = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.GRAB_OBJECT)
        )
        assertEquals("collect grab apple", grab.plan.label)
        assertEquals(CollectStage.DONE, grab.plan.collectStageAfterCompletion)
        assertEquals("grab 0", grab.plan.commands[2].command)
        assertEquals("grab 1", grab.plan.commands[3].command)

        val done = planner.plan(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.5f, collectStage = CollectStage.DONE)
        )
        assertEquals(PlanningResult.Complete("collect apple"), done)
    }

    @Test
    fun collectCentersBeforeApproachAndLimitsCenterAttempts() {
        val center = execute(
            HighLevelCommand.Collect("apple"),
            state(centerX = 0.2f, collectStage = CollectStage.CENTER_OBJECT)
        )

        assertEquals("collect center left", center.plan.label)
        assertEquals("wheels rl -15", center.plan.commands.first().command)
        assertTrue(center.plan.countsAsCollectCenterAttempt)

        val exhausted = planner.plan(
            HighLevelCommand.Collect("apple"),
            state(
                centerX = 0.2f,
                collectStage = CollectStage.CENTER_OBJECT,
                collectCenterAttempts = CommandPlanner.MAX_COLLECT_CENTER_ATTEMPTS
            )
        )
        assertTrue(exhausted is PlanningResult.Error)
    }

    @Test
    fun collectReentersSearchWhenVisionConfidenceIsLost() {
        val result = execute(
            HighLevelCommand.Collect("apple"),
            state(visible = false, collectStage = CollectStage.VERIFY_OBJECT)
        )

        assertEquals("collect verify lost object", result.plan.label)
        assertEquals(CollectStage.SEARCH_OBJECT, result.plan.collectStageAfterCompletion)
        assertTrue(result.plan.countsAsCollectVerifyAttempt)
    }

    @Test
    fun targetSpecificCollectIgnoresDifferentDetectedProfile() {
        val result = execute(
            HighLevelCommand.Collect("apple"),
            state(objectName = "cube_blue", collectStage = CollectStage.SEARCH_OBJECT)
        )

        assertEquals("collect search object", result.plan.label)
        assertTrue(result.plan.countsAsSearchAttempt)
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

    @Test
    fun lookCenterStopsHorizontalShoulderMovement() {
        val result = execute(
            HighLevelCommand.Look(HighLevelCommand.Direction.CENTER),
            state(cameraAvailable = false)
        )

        assertEquals("look center", result.plan.label)
        assertEquals(listOf("shoulder horizontal 0"), result.plan.commands.map { it.command })
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
        objectName: String? = "apple_red",
        centerX: Float = 0.5f,
        attempts: Int = 0,
        collectStage: CollectStage? = null,
        collectCenterAttempts: Int = 0,
        collectApproachAttempts: Int = 0,
        collectVerifyAttempts: Int = 0
    ) = RobotState(
        serialConnected = serialConnected,
        bluetoothClientConnected = true,
        cameraAvailable = cameraAvailable,
        visionResult = VisionModule.Result(
            objectVisible = visible,
            objectName = objectName,
            centerX = centerX,
            centerY = 0.5f,
            width = if (visible) 0.2f else 0f,
            height = if (visible) 0.2f else 0f,
            confidence = if (visible) 0.9f else 0f,
            identityConfidence = if (visible) 0.8f else 0f,
            frameWidth = 640,
            frameHeight = 480,
            timestampNanos = 1L
        ),
        searchAttempts = attempts,
        collectStage = collectStage,
        collectCenterAttempts = collectCenterAttempts,
        collectApproachAttempts = collectApproachAttempts,
        collectVerifyAttempts = collectVerifyAttempts
    )
}
