package com.jarvis.assistant.planner

/**
 * Valid states for the autonomous TaskExecutor state machine.
 */
enum class TaskExecutionState {
    IDLE,
    PLANNING,
    VALIDATING,
    WAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING,
    RETRYING,
    WAITING_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}
