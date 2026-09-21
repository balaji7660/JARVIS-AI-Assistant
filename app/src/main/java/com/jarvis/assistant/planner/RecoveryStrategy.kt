package com.jarvis.assistant.planner

/**
 * Recovery outcomes when a task step execution or verification encounters an issue.
 */
enum class RecoveryOutcome {
    SUCCESS,
    RETRY,
    ASK_USER,
    ABORT
}

/**
 * Evaluates step failures and determines the appropriate bounded recovery strategy.
 */
class RecoveryStrategy(
    private val maxRetriesPerStep: Int = TaskPlanValidator.MAX_RETRIES_PER_STEP
) {
    /**
     * Determines next action on step verification failure.
     */
    fun evaluate(
        step: TaskStep,
        currentRetry: Int,
        failureReason: String,
        isAmbiguous: Boolean = false
    ): RecoveryOutcome {
        if (isAmbiguous) {
            return RecoveryOutcome.ASK_USER
        }

        // Unrecoverable errors (security, safety denied, missing essential permissions)
        val lower = failureReason.lowercase()
        if (lower.contains("denied") ||
            lower.contains("blocked") ||
            lower.contains("password") ||
            lower.contains("sensitive") ||
            lower.contains("unauthorized")
        ) {
            return RecoveryOutcome.ABORT
        }

        val allowedRetries = minOf(step.retryCount, maxRetriesPerStep)
        return if (currentRetry < allowedRetries) {
            RecoveryOutcome.RETRY
        } else {
            RecoveryOutcome.ABORT
        }
    }
}
