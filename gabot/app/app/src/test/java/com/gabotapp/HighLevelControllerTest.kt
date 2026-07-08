package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HighLevelControllerTest {
    @Test
    fun stopExecutesCompleteSafeStopSequence() {
        val fixture = Fixture()

        fixture.controller.handle(HighLevelCommand.Stop)
        fixture.acknowledgeCommands(SerialCommandExecutor.STOP_COMMANDS.size)

        assertEquals(SerialCommandExecutor.STOP_COMMANDS, fixture.sent)
        assertEquals(listOf("OK hl stop"), fixture.responses)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun executionErrorTriggersFailStop() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.executor.onSerialLine("ERR: motor blocked")

        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals(listOf("ERR: motor blocked"), fixture.responses)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun timeoutTriggersExactlyOneFailStopSequence() {
        val fixture = Fixture(timeoutMs = 500L)
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        fixture.scheduler.advanceBy(500L)

        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals(listOf("ERR: timeout waiting for shoulder horizontal 40"), fixture.responses)
    }

    @Test
    fun unavailableCameraRejectsAutonomousCommand() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(cameraAvailable = false)

        fixture.controller.handle(HighLevelCommand.GoTo("visible_object", null))

        assertEquals(listOf("ERR: camera unavailable"), fixture.responses)
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

        assertEquals(listOf("OK hl goto apple"), fixture.responses)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun activeControllerRejectsSecondCommand() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))

        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.RIGHT))

        assertTrue(fixture.controller.isActive)
        assertEquals("ERR: high-level controller busy", fixture.responses.single())
    }

    @Test
    fun stopInterruptsActivePlanAndRunsStopSequence() {
        val fixture = Fixture()
        fixture.controller.handle(HighLevelCommand.Look(HighLevelCommand.Direction.LEFT))
        assertEquals("shoulder horizontal -40", fixture.sent.last())

        fixture.controller.handle(HighLevelCommand.Stop)
        assertEquals(SerialCommandExecutor.STOP_COMMANDS.first(), fixture.sent.last())
        fixture.acknowledgeCommands(SerialCommandExecutor.STOP_COMMANDS.size)

        assertEquals(listOf("OK hl stop"), fixture.responses)
        assertFalse(fixture.controller.isActive)
    }

    @Test
    fun disconnectedSerialRejectsCommand() {
        val fixture = Fixture()
        fixture.state = fixture.state.copy(serialConnected = false)

        fixture.controller.handle(HighLevelCommand.GoTo("visible_object", null))

        assertEquals(listOf("ERR: serial disconnected"), fixture.responses)
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
