package com.gabotapp

import android.os.Handler
import android.os.Looper

interface CommandScheduler {
    fun postDelayed(task: Runnable, delayMs: Long)
    fun cancel(task: Runnable)
}

private class AndroidCommandScheduler : CommandScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun postDelayed(task: Runnable, delayMs: Long) {
        handler.postDelayed(task, delayMs)
    }

    override fun cancel(task: Runnable) {
        handler.removeCallbacks(task)
    }
}

class SerialCommandExecutor(
    private val sendCommand: (String) -> Boolean,
    private val sendFailStopCommand: (String) -> Boolean,
    private val onLog: (String) -> Unit,
    private val onComplete: (ExecutionResult) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val scheduler: CommandScheduler = AndroidCommandScheduler()
) {
    private val queue = ArrayDeque<PlannedCommand>()
    private var activeCommand: PlannedCommand? = null
    private var activeLabel = ""
    private var active = false
    private var currentStep = 0
    private var totalSteps = 0
    private var progressCallback: (Int, Int, PlannedCommand) -> Unit = { _, _, _ -> }
    private var completionCallback: (ExecutionResult) -> Unit = onComplete
    private var pendingAdvance: Runnable? = null

    private val timeoutRunnable = Runnable {
        val timedOutCommand = activeCommand?.command ?: return@Runnable
        onLog("Serial executor timeout: $timedOutCommand")
        val callback = completionCallback
        clearActiveState()
        sendFailStopCommands()
        callback(ExecutionResult.Timeout(timedOutCommand))
    }

    fun execute(label: String, commands: List<String>): Boolean = executePlan(
        plan = CommandPlan(label, commands.map(::PlannedCommand)),
        onProgress = { _, _, _ -> },
        onPlanComplete = onComplete
    )

    fun executePlan(
        plan: CommandPlan,
        onProgress: (step: Int, total: Int, command: PlannedCommand) -> Unit,
        onPlanComplete: (ExecutionResult) -> Unit
    ): Boolean {
        if (plan.commands.isEmpty()) {
            onPlanComplete(ExecutionResult.Success)
            return true
        }
        if (active) {
            onLog("Serial executor busy: $activeLabel")
            return false
        }

        active = true
        activeLabel = plan.label
        currentStep = 0
        totalSteps = plan.commands.size
        progressCallback = onProgress
        completionCallback = onPlanComplete
        queue.clear()
        queue.addAll(plan.commands)
        onLog("Serial executor start: ${plan.label} (${plan.commands.size} command(s))")
        return sendNext()
    }

    fun onSerialLine(line: String): Boolean {
        val plannedCommand = activeCommand ?: return false
        val command = plannedCommand.command
        if (line.startsWith("OK", ignoreCase = true)) {
            scheduler.cancel(timeoutRunnable)
            activeCommand = null
            onLog("Serial executor OK for '$command': $line")
            scheduleNext(plannedCommand.delayAfterSuccessMs)
            return true
        }
        if (line.startsWith("ERR", ignoreCase = true)) {
            scheduler.cancel(timeoutRunnable)
            onLog("Serial executor ERR for '$command': $line")
            finish(ExecutionResult.Error(command, line))
            return true
        }
        return false
    }

    fun cancel(reason: String) {
        if (!active && activeCommand == null && queue.isEmpty()) {
            return
        }
        onLog("Serial executor canceled: $reason")
        clearActiveState()
    }

    fun cancelAndFailStop(reason: String) {
        cancel(reason)
        sendFailStopCommands()
    }

    fun destroy() {
        clearActiveState()
    }

    private fun scheduleNext(delayMs: Long) {
        if (delayMs <= 0L) {
            sendNext()
            return
        }
        val advance = Runnable {
            pendingAdvance = null
            if (active) {
                sendNext()
            }
        }
        pendingAdvance = advance
        scheduler.postDelayed(advance, delayMs)
    }

    private fun sendNext(): Boolean {
        val nextCommand = queue.removeFirstOrNull()
        if (nextCommand == null) {
            val completedLabel = activeLabel
            onLog("Serial executor complete: $completedLabel")
            finish(ExecutionResult.Success)
            return true
        }

        activeCommand = nextCommand
        currentStep++
        progressCallback(currentStep, totalSteps, nextCommand)
        if (!sendCommand(nextCommand.command)) {
            finish(ExecutionResult.SendFailed(nextCommand.command))
            return false
        }
        scheduler.cancel(timeoutRunnable)
        scheduler.postDelayed(timeoutRunnable, timeoutMs)
        return true
    }

    private fun finish(result: ExecutionResult) {
        val callback = completionCallback
        clearActiveState()
        callback(result)
    }

    private fun sendFailStopCommands() {
        FAIL_STOP_COMMANDS.forEach { command ->
            sendFailStopCommand(command)
        }
    }

    private fun clearActiveState() {
        scheduler.cancel(timeoutRunnable)
        pendingAdvance?.let(scheduler::cancel)
        pendingAdvance = null
        queue.clear()
        activeCommand = null
        activeLabel = ""
        active = false
        currentStep = 0
        totalSteps = 0
        progressCallback = { _, _, _ -> }
        completionCallback = onComplete
    }

    sealed class ExecutionResult {
        data object Success : ExecutionResult()
        data class Error(val command: String, val response: String) : ExecutionResult()
        data class Timeout(val command: String) : ExecutionResult()
        data class SendFailed(val command: String) : ExecutionResult()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 3_000L

        val STOP_COMMANDS = listOf(
            "shoulder horizontal 0",
            "shoulder vertical 0",
            "wheels fb 0",
            "wheels rl 0",
            "grab 0",
            "release 0"
        )

        val FAIL_STOP_COMMANDS = listOf(
            "shoulder horizontal 0",
            "shoulder vertical 0",
            "wheels fb 0",
            "wheels rl 0"
        )
    }
}
