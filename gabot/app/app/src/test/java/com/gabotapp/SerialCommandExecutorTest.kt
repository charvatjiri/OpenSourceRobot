package com.gabotapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialCommandExecutorTest {
    @Test
    fun executesCommandsSequentiallyAndHonorsDelay() {
        val fixture = Fixture()
        val progress = mutableListOf<Int>()

        val accepted = fixture.executor.executePlan(
            CommandPlan(
                "sequence",
                listOf(
                    PlannedCommand("first", delayAfterSuccessMs = 100L),
                    PlannedCommand("second")
                )
            ),
            onProgress = { step, _, _ -> progress += step },
            onPlanComplete = fixture.results::add
        )

        assertTrue(accepted)
        assertEquals(listOf("first"), fixture.sent)
        assertTrue(fixture.executor.onSerialLine("OK first"))
        fixture.scheduler.advanceBy(99L)
        assertEquals(listOf("first"), fixture.sent)
        fixture.scheduler.advanceBy(1L)
        assertEquals(listOf("first", "second"), fixture.sent)
        fixture.executor.onSerialLine("OK second")
        assertEquals(listOf(1, 2), progress)
        assertEquals(listOf(SerialCommandExecutor.ExecutionResult.Success), fixture.results)
    }

    @Test
    fun errorStopsQueueWithoutSendingRemainingCommands() {
        val fixture = Fixture()
        fixture.executor.execute("sequence", listOf("first", "second"))

        assertTrue(fixture.executor.onSerialLine("ERR: rejected"))

        assertEquals(listOf("first"), fixture.sent)
        assertEquals(
            SerialCommandExecutor.ExecutionResult.Error("first", "ERR: rejected"),
            fixture.results.single()
        )
        assertFalse(fixture.executor.onSerialLine("OK late"))
    }

    @Test
    fun timeoutSendsFailStopAndReportsTimedOutCommand() {
        val fixture = Fixture(timeoutMs = 500L)
        fixture.executor.execute("sequence", listOf("move"))

        fixture.scheduler.advanceBy(500L)

        assertEquals(SerialCommandExecutor.FAIL_STOP_COMMANDS, fixture.failStop)
        assertEquals(
            SerialCommandExecutor.ExecutionResult.Timeout("move"),
            fixture.results.single()
        )
    }

    @Test
    fun busyExecutorRejectsSecondPlan() {
        val fixture = Fixture()

        assertTrue(fixture.executor.execute("first", listOf("move")))
        assertFalse(fixture.executor.execute("second", listOf("other")))
        assertEquals(listOf("move"), fixture.sent)
    }

    @Test
    fun sendFailureIsReportedWithoutStartingTimeout() {
        val scheduler = FakeCommandScheduler()
        val results = mutableListOf<SerialCommandExecutor.ExecutionResult>()
        val executor = SerialCommandExecutor(
            sendCommand = { false },
            sendFailStopCommand = { true },
            onLog = {},
            onComplete = results::add,
            scheduler = scheduler
        )

        assertFalse(executor.execute("failed", listOf("move")))
        assertEquals(SerialCommandExecutor.ExecutionResult.SendFailed("move"), results.single())
        scheduler.advanceBy(SerialCommandExecutor.DEFAULT_TIMEOUT_MS)
        assertEquals(1, results.size)
    }

    private class Fixture(timeoutMs: Long = 1_000L) {
        val scheduler = FakeCommandScheduler()
        val sent = mutableListOf<String>()
        val failStop = mutableListOf<String>()
        val results = mutableListOf<SerialCommandExecutor.ExecutionResult>()
        val executor = SerialCommandExecutor(
            sendCommand = { command -> sent.add(command) },
            sendFailStopCommand = { command -> failStop.add(command) },
            onLog = {},
            onComplete = results::add,
            timeoutMs = timeoutMs,
            scheduler = scheduler
        )
    }
}
