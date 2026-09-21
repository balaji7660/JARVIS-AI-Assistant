package com.jarvis.assistant.context

import com.jarvis.assistant.planner.TaskExecutionState

/**
 * Tracks the state of currently executing or recently paused/cancelled automation tasks.
 *
 * Keeps bounded information regarding execution progression, allowing safe task resumption
 * or cooperative cancellation. Lazily expires when older than [ContextConstants.CONTEXT_EXPIRY_MS].
 */
data class ActiveTaskContext(
    val activeTaskId: String? = null,
    val taskDescription: String? = null,
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val lastSuccessfulStep: Int = 0,
    val currentApp: String? = null,
    val expectedScreen: String? = null,
    val currentTarget: String? = null,
    val taskState: TaskExecutionState = TaskExecutionState.IDLE,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this task context has expired.
     */
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return (currentTime - timestamp) > ContextConstants.CONTEXT_EXPIRY_MS
    }

    /**
     * Determines whether the task is eligible for safe resumption.
     * Can resume ONLY if:
     * - Context has not expired
     * - Task has an activeTaskId and remaining steps
     * - Task was cancelled or paused
     * - Current app is compatible with the expected app (if expected)
     */
    fun canResume(currentForegroundPackage: String? = null, currentTime: Long = System.currentTimeMillis()): Boolean {
        if (isExpired(currentTime)) return false
        if (activeTaskId == null || totalSteps <= 0 || currentStep >= totalSteps) return false
        if (taskState != TaskExecutionState.CANCELLED && taskState != TaskExecutionState.WAITING_USER) return false

        // If an expectedScreen package is registered, verify compatibility if currentPackage is provided
        if (expectedScreen != null && currentForegroundPackage != null && currentForegroundPackage.isNotBlank()) {
            if (!expectedScreen.equals(currentForegroundPackage, ignoreCase = true) &&
                !currentForegroundPackage.contains(expectedScreen, ignoreCase = true)) {
                return false
            }
        }
        return true
    }
}
