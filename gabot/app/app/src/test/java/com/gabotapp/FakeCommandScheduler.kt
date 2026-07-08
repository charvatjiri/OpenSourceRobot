package com.gabotapp

class FakeCommandScheduler : CommandScheduler {
    private data class ScheduledTask(
        val task: Runnable,
        val runAtMs: Long
    )

    private val tasks = mutableListOf<ScheduledTask>()
    private var nowMs = 0L

    override fun postDelayed(task: Runnable, delayMs: Long) {
        tasks += ScheduledTask(task, nowMs + delayMs)
    }

    override fun cancel(task: Runnable) {
        tasks.removeAll { it.task === task }
    }

    fun advanceBy(milliseconds: Long) {
        val target = nowMs + milliseconds
        while (true) {
            val next = tasks.filter { it.runAtMs <= target }.minByOrNull { it.runAtMs } ?: break
            tasks.remove(next)
            nowMs = next.runAtMs
            next.task.run()
        }
        nowMs = target
    }
}
