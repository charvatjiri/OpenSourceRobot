package com.gabotapp

import android.os.Handler
import android.os.Looper

class SerialCommandExecutor(
    private val sendCommand: (String) -> Boolean,
    private val sendFailStopCommand: (String) -> Boolean,
    private val onLog: (String) -> Unit,
    private val onComplete: (ExecutionResult) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var activeCommand: String? = null
    private var activeLabel: String = ""
    private var active = false

    private val timeoutRunnable = Runnable {
        val timedOutCommand = activeCommand ?: return@Runnable
        onLog("Serial executor timeout: $timedOutCommand")
        clearActiveState()
        sendFailStopCommands()
        onComplete(ExecutionResult.Timeout(timedOutCommand))
    }

    fun execute(label: String, commands: List<String>): Boolean {
        if (commands.isEmpty()) {
            onComplete(ExecutionResult.Success)
            return true
        }
        if (active) {
            onLog("Serial executor busy: $activeLabel")
            return false
        }

        active = true
        activeLabel = label
        queue.clear()
        queue.addAll(commands)
        onLog("Serial executor start: $label (${commands.size} command(s))")
        return sendNext()
    }

    fun onSerialLine(line: String): Boolean {
        val command = activeCommand ?: return false
        if (line.startsWith("OK", ignoreCase = true)) {
            handler.removeCallbacks(timeoutRunnable)
            onLog("Serial executor OK for '$command': $line")
            sendNext()
            return true
        }
        if (line.startsWith("ERR", ignoreCase = true)) {
            handler.removeCallbacks(timeoutRunnable)
            onLog("Serial executor ERR for '$command': $line")
            clearActiveState()
            onComplete(ExecutionResult.Error(command, line))
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

    private fun sendNext(): Boolean {
        val nextCommand = queue.removeFirstOrNull()
        if (nextCommand == null) {
            val completedLabel = activeLabel
            clearActiveState()
            onLog("Serial executor complete: $completedLabel")
            onComplete(ExecutionResult.Success)
            return true
        }

        activeCommand = nextCommand
        if (!sendCommand(nextCommand)) {
            val failedCommand = nextCommand
            clearActiveState()
            onComplete(ExecutionResult.SendFailed(failedCommand))
            return false
        }
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, timeoutMs)
        return true
    }

    private fun sendFailStopCommands() {
        FAIL_STOP_COMMANDS.forEach { command ->
            sendFailStopCommand(command)
        }
    }

    private fun clearActiveState() {
        handler.removeCallbacks(timeoutRunnable)
        queue.clear()
        activeCommand = null
        activeLabel = ""
        active = false
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
