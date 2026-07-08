package com.gabotapp

data class PlannedCommand(
    val command: String,
    val delayAfterSuccessMs: Long = 0L
)

data class CommandPlan(
    val label: String,
    val commands: List<PlannedCommand>,
    val replanAfterCompletion: Boolean = false,
    val countsAsSearchAttempt: Boolean = false
)

sealed class PlanningResult {
    data class Execute(val plan: CommandPlan) : PlanningResult()
    data class Complete(val message: String) : PlanningResult()
    data class Error(val message: String) : PlanningResult()
}
