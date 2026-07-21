package com.gabotapp

class HighLevelController(
    private val planner: CommandPlanner,
    private val serialExecutor: SerialCommandExecutor,
    private val stateProvider: () -> RobotState,
    private val responseFormatter: HighLevelResponseFormatter = HighLevelResponseFormatter(),
    private val sendResponse: (String) -> Unit,
    private val onLog: (String) -> Unit
) {
    private var activeCommand: HighLevelCommand? = null
    private var activePlan: CommandPlan? = null
    private var currentStep = 0
    private var searchAttempts = 0
    private var collectStage: CollectStage? = null
    private var collectCenterAttempts = 0
    private var collectApproachAttempts = 0
    private var collectVerifyAttempts = 0
    private var planIterations = 0
    var state: State = State.IDLE
        private set

    val isActive: Boolean
        get() = state == State.RUNNING || state == State.REPLANNING ||
            state == State.STOPPING || state == State.PAUSED

    fun handle(command: HighLevelCommand) {
        if (command == HighLevelCommand.Status) {
            sendResponse(responseFormatter.status(currentState(), activeCommand?.let(::label)))
            return
        }
        if (command == HighLevelCommand.Cancel) {
            handleCancel()
            return
        }
        if (command == HighLevelCommand.Pause) {
            handlePause()
            return
        }
        if (command == HighLevelCommand.Resume) {
            handleResume()
            return
        }
        if (command == HighLevelCommand.Stop) {
            state = State.STOPPING
            serialExecutor.cancel("high-level stop requested")
            clearActiveState(finalState = State.STOPPING)
            state = State.STOPPING
            activeCommand = command
            sendResponse(responseFormatter.started(label(command)))
            executeNextPlan()
            return
        }
        if (isActive) {
            sendResponse(responseFormatter.error("high-level controller busy"))
            return
        }

        clearActiveState(finalState = State.IDLE)
        state = State.RUNNING
        activeCommand = command
        if (command is HighLevelCommand.Collect) {
            collectStage = CollectStage.SEARCH_OBJECT
        }
        onLog("High-level command started: ${label(command)}")
        sendResponse(responseFormatter.started(label(command)))
        executeNextPlan()
    }

    private fun handleCancel() {
        if (!isActive) {
            clearActiveState(finalState = State.IDLE)
            sendResponse(responseFormatter.success("cancel"))
            return
        }
        onLog("High-level command canceled by client")
        state = State.STOPPING
        serialExecutor.cancelAndFailStop("high-level cancel requested")
        clearActiveState(finalState = State.IDLE)
        sendResponse(responseFormatter.success("cancel"))
    }

    private fun handlePause() {
        if (!isActive || state == State.STOPPING) {
            sendResponse(responseFormatter.error("no active high-level command to pause"))
            return
        }
        if (state == State.PAUSED) {
            sendResponse(responseFormatter.success("pause"))
            return
        }
        onLog("High-level command paused")
        serialExecutor.cancelAndFailStop("high-level pause requested")
        activePlan = null
        currentStep = 0
        planIterations = 0
        state = State.PAUSED
        sendResponse(responseFormatter.success("pause"))
    }

    private fun handleResume() {
        val command = activeCommand
        if (state != State.PAUSED || command == null) {
            sendResponse(responseFormatter.error("no paused high-level command to resume"))
            return
        }
        onLog("High-level command resumed: ${label(command)}")
        state = State.RUNNING
        planIterations = 0
        sendResponse(responseFormatter.started("resume ${label(command)}"))
        executeNextPlan()
    }

    fun cancel(reason: String, failStop: Boolean) {
        if (!isActive) {
            return
        }
        onLog("High-level command canceled: $reason")
        if (failStop) {
            state = State.STOPPING
            serialExecutor.cancelAndFailStop(reason)
        } else {
            serialExecutor.cancel(reason)
        }
        clearActiveState(finalState = State.FAILED)
    }

    fun currentState(): RobotState = stateProvider().copy(
        activePlan = activePlan?.label,
        currentStep = currentStep,
        searchAttempts = searchAttempts,
        collectStage = collectStage,
        collectCenterAttempts = collectCenterAttempts,
        collectApproachAttempts = collectApproachAttempts,
        collectVerifyAttempts = collectVerifyAttempts,
        highLevelState = state.name
    )

    private fun executeNextPlan() {
        val command = activeCommand ?: return
        if (state == State.PAUSED) {
            return
        }
        if (state != State.STOPPING) {
            state = if (planIterations == 0) State.RUNNING else State.REPLANNING
        }
        planIterations++
        if (planIterations > MAX_PLAN_ITERATIONS) {
            fail("planning iteration limit reached", failStopAlreadySent = false)
            return
        }

        val state = currentState()
        when (val result = planner.plan(command, state)) {
            is PlanningResult.Error -> fail(result.message, failStopAlreadySent = false)
            is PlanningResult.Complete -> complete(result.message)
            is PlanningResult.Execute -> execute(result.plan)
        }
    }

    private fun execute(plan: CommandPlan) {
        activePlan = plan
        currentStep = 0
        if (plan.countsAsSearchAttempt) {
            searchAttempts++
        }
        if (plan.countsAsCollectCenterAttempt) {
            collectCenterAttempts++
        }
        if (plan.countsAsCollectApproachAttempt) {
            collectApproachAttempts++
        }
        if (plan.countsAsCollectVerifyAttempt) {
            collectVerifyAttempts++
        }
        onLog("High-level plan: ${plan.label}")
        if (plan.replanAfterCompletion) {
            sendResponse(responseFormatter.replan(plan.label, searchAttempts))
        }
        if (state != State.STOPPING) {
            state = State.RUNNING
        }
        val accepted = serialExecutor.executePlan(
            plan = plan,
            onProgress = { step, total, command ->
                currentStep = step
                onLog("High-level step $step/$total: ${command.command}")
                sendResponse(responseFormatter.progress(step, total, command.command))
            },
            onPlanComplete = { result -> handleExecutionResult(plan, result) }
        )
        if (!accepted) {
            fail("serial executor busy", failStopAlreadySent = false)
        }
    }

    private fun handleExecutionResult(
        plan: CommandPlan,
        result: SerialCommandExecutor.ExecutionResult
    ) {
        when (result) {
            SerialCommandExecutor.ExecutionResult.Success -> {
                plan.collectStageAfterCompletion?.let { stage ->
                    collectStage = stage
                }
                activePlan = null
                currentStep = 0
                if (plan.replanAfterCompletion) {
                    state = State.REPLANNING
                    executeNextPlan()
                } else {
                    complete(label(activeCommand ?: return))
                }
            }
            is SerialCommandExecutor.ExecutionResult.Error ->
                fail(result.response, failStopAlreadySent = false)
            is SerialCommandExecutor.ExecutionResult.Timeout ->
                fail("timeout waiting for ${result.command}", failStopAlreadySent = true)
            is SerialCommandExecutor.ExecutionResult.SendFailed ->
                fail("failed to send ${result.command}", failStopAlreadySent = false)
        }
    }

    private fun complete(message: String) {
        onLog("High-level command complete: $message")
        state = State.COMPLETED
        sendResponse(responseFormatter.success(message))
        clearActiveState(finalState = State.COMPLETED)
    }

    private fun fail(message: String, failStopAlreadySent: Boolean) {
        onLog("High-level command failed: $message")
        if (!failStopAlreadySent) {
            state = State.STOPPING
            serialExecutor.cancelAndFailStop(message)
        }
        state = State.FAILED
        sendResponse(responseFormatter.error(message))
        clearActiveState(finalState = State.FAILED)
    }

    private fun clearActiveState(finalState: State) {
        activeCommand = null
        activePlan = null
        currentStep = 0
        searchAttempts = 0
        collectStage = null
        collectCenterAttempts = 0
        collectApproachAttempts = 0
        collectVerifyAttempts = 0
        planIterations = 0
        state = finalState
    }

    private fun label(command: HighLevelCommand): String = when (command) {
        HighLevelCommand.Status -> "status"
        HighLevelCommand.Cancel -> "cancel"
        HighLevelCommand.Pause -> "pause"
        HighLevelCommand.Resume -> "resume"
        HighLevelCommand.Stop -> "stop"
        is HighLevelCommand.Look -> "look ${command.direction.name.lowercase()}"
        is HighLevelCommand.GoTo -> "goto ${command.objectName ?: command.target}"
        is HighLevelCommand.Collect -> "collect ${command.objectName}"
    }

    companion object {
        private const val MAX_PLAN_ITERATIONS = 20
    }

    enum class State {
        IDLE,
        RUNNING,
        REPLANNING,
        STOPPING,
        PAUSED,
        FAILED,
        COMPLETED
    }
}
