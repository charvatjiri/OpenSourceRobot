package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighLevelControllerTest {
    @Test
    fun statusReturnsCurrentStateWithoutSerialCommand() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(
            serialConnected = false,
            bluetoothClientConnected = true,
            cameraAvailable = false,
            visionResult = vision(visible = true, centerX = 0.25f),
            lastSerialResponse = "OK get version",
            lastError = "camera unavailable"
        )

        fixture.controller.handle(HighLevelCommand.Status)

        assertTrue(fixture.sent.isEmpty())
        assertTrue(fixture.failStop.isEmpty())
        assertFalse(fixture.controller.isActive)
        assertEquals(
            "INFO status serial=disconnected bluetooth=connected camera=unavailable " +
                "active=none state=IDLE plan=none step=0 searchAttempts=0 " +
                "collectStage=none collectCenterAttempts=0 collectApproachAttempts=0 collectVerifyAttempts=0 " +
                "lastSerialResponse=OK_get_version lastError=camera_unavailable visionVisible=true " +
                "visionCenterX=0.250 visionCenterY=0.500 visionConfidence=0.900 visionFrame=640x480",
            fixture.responses.single()
        )
    }

    @Test
    fun statusDoesNotInterruptActiveCommand() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.controller.handle(HighLevelCommand.Status)

        assertTrue(fixture.controller.isActive)
        assertEquals("shoulder horizontal -40", fixture.sent.single())
        assertEquals(
            "INFO status serial=connected bluetooth=connected camera=available " +
                "active=look_left state=RUNNING plan=look_left step=1 searchAttempts=0 " +
                "collectStage=none collectCenterAttempts=0 collectApproachAttempts=0 collectVerifyAttempts=0 " +
                "lastSerialResponse=none lastError=none visionVisible=true " +
                "visionCenterX=0.500 visionCenterY=0.500 visionConfidence=0.900 visionFrame=640x480",
            fixture.responses.last()
        )
    }

    @Test
    fun exposesExplicitStateTransitions() {
        val fixture = Fixture()
        assertEquals(HighLevelController.State.IDLE, fixture.controller.state)

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))
        assertEquals(HighLevelController.State.RUNNING, fixture.controller.state)

        fixture.controller.handle(HighLevelCommand.Stop)
        assertEquals(HighLevelController.State.STOPPING, fixture.controller.state)

        fixture.acknowledgeCommands(SerialCommandExecutor.STOP_COMMANDS.size)
        assertEquals(HighLevelController.State.COMPLETED, fixture.controller.state)
    }

    @Test
    fun stopExecutesCompleteSafeStopSequence() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Stop)
        fixture.acknowledgeCommands(SerialCommandExecutor.STOP_COMMANDS.size)

        assertEquals(SerialCommandExecutor.STOP_COMMANDS, fixture.sent)
        assertEquals("INFO hl started stop", fixture.responses.first())
        assertTrue(fixture.responses.contains("INFO hl step 1/6 shoulder_horizontal_0"))
        assertEquals("OK hl stop", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
        assertEquals(HighLevelController.State.COMPLETED, fixture.controller.state)
    }

    @Test
    fun emitsFormalLifecycleResponsesForSuccessfulCommand() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))
        fixture.executor.onSerialLine("OK right")
        fixture.scheduler.advanceBy(400L)
        fixture.executor.onSerialLine("OK stop")

        assertEquals(
            listOf(
                "INFO hl started look_right",
                "INFO hl step 1/2 shoulder_horizontal_40",
                "INFO hl step 2/2 shoulder_horizontal_0",
                "OK hl look_right"
            ),
            fixture.responses
        )
        assertEquals(HighLevelController.State.COMPLETED, fixture.controller.state)
    }

    @Test
    fun executionErrorTriggersFailStop() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.executor.onSerialLine("ERR: motor blocked")

        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals("ERR hl motor_blocked", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
        assertEquals(HighLevelController.State.FAILED, fixture.controller.state)
    }

    @Test
    fun terminalStateAllowsNextCommand() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))
        fixture.executor.onSerialLine("OK left")
        fixture.scheduler.advanceBy(400L)
        fixture.executor.onSerialLine("OK stop")

        assertEquals(HighLevelController.State.COMPLETED, fixture.controller.state)

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        assertEquals(HighLevelController.State.RUNNING, fixture.controller.state)
        assertEquals("shoulder horizontal 40", fixture.sent.last())
    }

    @Test
    fun cancelMovesActiveCommandToFailedState() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.controller.cancel("bluetooth client disconnected", failStop = true)

        assertFalse(fixture.controller.isActive)
        assertEquals(HighLevelController.State.FAILED, fixture.controller.state)
        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
    }

    @Test
    fun timeoutTriggersExactlyOneFailStopSequence() {
        val fixture = Fixture(timeoutMs = 500L)
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        fixture.scheduler.advanceBy(500L)

        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals("ERR hl timeout_waiting_for_shoulder_horizontal_40", fixture.responses.last())
    }

    @Test
    fun unavailableCameraRejectsAutonomousCommand() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(cameraAvailable = false)

        fixture.controller.handle(HighLevelCommand.GoTo("visible_object", null))

        assertEquals("ERR hl camera_unavailable", fixture.responses.last())
        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun searchesThenReplansUsingUpdatedCameraResult() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(visionResult = vision(visible = false))

        fixture.controller.handle(HighLevelCommand.GoTo("visible_object", "apple"))
        assertEquals("wheels rl 15", fixture.sent.last())
        fixture.executor.onSerialLine("OK search")
        fixture.scheduler.advanceBy(350L)
        assertEquals("wheels rl 0", fixture.sent.last())

        fixture.state = fixture.state.copy(visionResult = vision(visible = true, centerX = 0.5f))
        fixture.executor.onSerialLine("OK search stop")
        fixture.scheduler.advanceBy(250L)
        assertEquals("wheels rl 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK centered")
        assertEquals("wheels fb 15", fixture.sent.last())
        fixture.executor.onSerialLine("OK approach")
        fixture.scheduler.advanceBy(500L)
        assertEquals("wheels fb 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK approach stop")

        assertEquals("OK hl goto_apple", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun activeControllerRejectsSecondCommand() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        assertTrue(fixture.controller.isActive)
        assertEquals("ERR hl high-level_controller_busy", fixture.responses.last())
    }

    @Test
    fun stopInterruptsActivePlanAndRunsStopSequence() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))
        assertEquals("shoulder horizontal -40", fixture.sent.last())

        fixture.controller.handle(HighLevelCommand.Stop)
        assertEquals(SerialCommandExecutor.STOP_COMMANDS.first(), fixture.sent.last())
        fixture.acknowledgeCommands(SerialCommandExecutor.STOP_COMMANDS.size)

        assertEquals("OK hl stop", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun cancelInterruptsActivePlanAndClearsController() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.controller.handle(HighLevelCommand.Cancel)

        assertFalse(fixture.controller.isActive)
        assertEquals(HighLevelController.State.IDLE, fixture.controller.state)
        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals("OK hl cancel", fixture.responses.last())
    }

    @Test
    fun pauseStopsMotionAndResumeReplansPausedCommand() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        fixture.controller.handle(HighLevelCommand.Pause)

        assertTrue(fixture.controller.isActive)
        assertEquals(HighLevelController.State.PAUSED, fixture.controller.state)
        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals("OK hl pause", fixture.responses.last())

        fixture.controller.handle(HighLevelCommand.Resume)

        assertEquals(HighLevelController.State.RUNNING, fixture.controller.state)
        assertEquals("INFO hl started resume_look_right", fixture.responses[fixture.responses.lastIndex - 1])
        assertEquals("shoulder horizontal 40", fixture.sent.last())
    }

    @Test
    fun resumeWithoutPausedCommandReturnsError() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Resume)

        assertEquals("ERR hl no_paused_high-level_command_to_resume", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun lookCenterUsesOperationalCenterCommand() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.CENTER))
        fixture.executor.onSerialLine("OK center")

        assertEquals(listOf("shoulder horizontal 0"), fixture.sent)
        assertEquals("OK hl look_center", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun collectRunsThroughStageBasedApproachVerifyAndGrab() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Collect("apple"))

        assertEquals("wheels fb 15", fixture.sent.last())
        assertTrue(fixture.responses.contains("INFO hl replan collect_search_object_found attempt 0"))
        assertTrue(fixture.responses.contains("INFO hl replan collect_object_centered attempt 0"))

        fixture.executor.onSerialLine("OK approach")
        fixture.scheduler.advanceBy(350L)
        assertEquals("wheels fb 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK approach stop")
        fixture.scheduler.advanceBy(250L)

        assertEquals("wheels fb 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK grab wheel stop")
        assertEquals("wheels rl 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK grab rotate stop")
        assertEquals("grab 0", fixture.sent.last())
        fixture.executor.onSerialLine("OK grab close")
        fixture.scheduler.advanceBy(500L)
        assertEquals("grab 1", fixture.sent.last())
        fixture.executor.onSerialLine("OK grab hold")

        assertEquals("OK hl collect_apple", fixture.responses.last())
        assertFalse(fixture.controller.isActive)
        assertEquals(HighLevelController.State.COMPLETED, fixture.controller.state)
    }

    @Test
    fun disconnectedSerialRejectsCommand() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(serialConnected = false)

        fixture.controller.handle(HighLevelCommand.GoTo("visible_object", null))

        assertEquals("ERR hl serial_disconnected", fixture.responses.last())
        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertFalse(fixture.controller.isActive)
    }

    private class Fixture(timeoutMs: Long = 1_000L) {
        val scheduler = FakeCommandScheduler()
        val sent = mutableListOf<String>()
        val failStop = mutableListOf<String>()
        val responses = mutableListOf<String>()
        var state = RobotState(
            serialConnected = true,
            bluetoothClientConnected = true,
            cameraAvailable = true,
            visionResult = vision()
        )
        val executor = SerialCommandExecutor(
            sendCommand = { command -> sent.add(command) },
            sendFailStopCommand = { command -> failStop.add(command) },
            onLog = {},
            onComplete = {},
            timeoutMs = timeoutMs,
            scheduler = scheduler
        )
        val controller = HighLevelController(
            planner = CommandPlanner(),
            serialExecutor = executor,
            stateProvider = { state },
            sendResponse = responses::add,
            onLog = {}
        )

        fun acknowledgeCommands(count: Int) {
            repeat(count) {
                assertTrue(executor.onSerialLine("OK"))
            }
        }
    }

    companion object {
        private fun vision(visible: Boolean = true, centerX: Float = 0.5f) = VisionModule.Result(
            objectVisible = visible,
            centerX = centerX,
            centerY = 0.5f,
            confidence = if (visible) 0.9f else 0f,
            frameWidth = 640,
            frameHeight = 480,
            timestampNanos = 1L
        )
    }
}
