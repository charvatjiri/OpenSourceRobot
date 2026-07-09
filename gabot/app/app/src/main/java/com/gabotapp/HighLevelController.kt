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
    private var planIterations = 0

    val isActive: Boolean
        get() = activeCommand != null

    fun handle(command: HighLevelCommand) {
        if (command == HighLevelCommand.Status) {
            sendResponse(responseFormatter.status(currentState(), activeCommand?.let(::label)))
            return
        }
        if (command == HighLevelCommand.Stop) {
            serialExecutor.cancel("high-level stop requested")
            resetActiveState()
            activeCommand = command
            executeNextPlan()
            return
        }
        if (activeCommand != null) {
            sendResponse(responseFormatter.error("high-level controller busy"))
            return
        }

        activeCommand = command
        onLog("High-level command started: ${label(command)}")
        sendResponse(responseFormatter.started(label(command)))
        executeNextPlan()
    }

    fun cancel(reason: String, failStop: Boolean) {
        if (activeCommand == null) {
            return
        }
        onLog("High-level command canceled: $reason")
        if (failStop) {
            serialExecutor.cancelAndFailStop(reason)
        } else {
            serialExecutor.cancel(reason)
        }
        resetActiveState()
    }

    fun currentState(): RobotState = stateProvider().copy(
        activePlan = activePlan?.label,
        currentStep = currentStep,
        searchAttempts = searchAttempts
    )

    private fun executeNextPlan() {
        val command = activeCommand ?: return
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
        onLog("High-level plan: ${plan.label}")
        if (plan.replanAfterCompletion) {
            sendResponse(responseFormatter.replan(plan.label, searchAttempts))
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
                activePlan = null
                currentStep = 0
                if (plan.replanAfterCompletion) {
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
        sendResponse(responseFormatter.success(message))
        resetActiveState()
    }

    private fun fail(message: String, failStopAlreadySent: Boolean) {
        onLog("High-level command failed: $message")
        if (!failStopAlreadySent) {
            serialExecutor.cancelAndFailStop(message)
        }
        sendResponse(responseFormatter.error(message))
        resetActiveState()
    }

    private fun resetActiveState() {
        activeCommand = null
        activePlan = null
        currentStep = 0
        searchAttempts = 0
        planIterations = 0
    }

    private fun label(command: HighLevelCommand): String = when (command) {
        HighLevelCommand.Status -> "status"
        HighLevelCommand.Stop -> "stop"
        is HighLevelCommand.Look -> "look ${command.direction.name.lowercase()}"
        is HighLevelCommand.GoTo -> "goto ${command.objectName ?: command.target}"
        is HighLevelCommand.Collect -> "collect ${command.objectName}"
    }

    companion object {
        private const val MAX_PLAN_ITERATIONS = 20
    }
}
