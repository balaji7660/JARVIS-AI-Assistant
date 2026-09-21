package com.jarvis.assistant.context

import com.jarvis.assistant.planner.TaskExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTaskContextTest {

    @Test
    fun defaultState_isIdleAndCannotResume() {
        val task = ActiveTaskContext()
        assertEquals(TaskExecutionState.IDLE, task.taskState)
        assertEquals(0, task.currentStep)
        assertEquals(0, task.totalSteps)
        assertFalse(task.canResume())
    }

    @Test
    fun isExpired_returnsTrueWhenPastDuration() {
        val baseTime = 1000L
        val task = ActiveTaskContext(timestamp = baseTime)

        assertFalse(task.isExpired(currentTime = baseTime + 10_000L))
        assertTrue(task.isExpired(currentTime = baseTime + ContextConstants.CONTEXT_EXPIRY_MS + 1L))
    }

    @Test
    fun canResume_allowsResumptionWhenCancelledAndValid() {
        val baseTime = 1000L
        val task = ActiveTaskContext(
            activeTaskId = "task_123",
            taskDescription = "Search YouTube for Kotlin",
            currentStep = 1,
            totalSteps = 4,
            lastSuccessfulStep = 1,
            currentApp = "YouTube",
            expectedScreen = "com.google.android.youtube",
            taskState = TaskExecutionState.CANCELLED,
            timestamp = baseTime
        )

        // Compatible current app
        assertTrue(task.canResume(currentForegroundPackage = "com.google.android.youtube", currentTime = baseTime + 10_000L))

        // Incompatible app
        assertFalse(task.canResume(currentForegroundPackage = "com.android.settings", currentTime = baseTime + 10_000L))

        // Expired task
        assertFalse(task.canResume(currentForegroundPackage = "com.google.android.youtube", currentTime = baseTime + ContextConstants.CONTEXT_EXPIRY_MS + 100L))
    }

    @Test
    fun canResume_rejectsAlreadyCompletedOrExecutingTasks() {
        val completedTask = ActiveTaskContext(
            activeTaskId = "task_done",
            currentStep = 3,
            totalSteps = 3,
            taskState = TaskExecutionState.COMPLETED
        )
        assertFalse(completedTask.canResume())

        val runningTask = ActiveTaskContext(
            activeTaskId = "task_running",
            currentStep = 1,
            totalSteps = 3,
            taskState = TaskExecutionState.EXECUTING
        )
        assertFalse(runningTask.canResume())
    }
}
