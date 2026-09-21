package com.jarvis.assistant.ui.home

import com.jarvis.assistant.wakeword.WakeWordState

data class HomeUiState(
    val state: AssistantState = AssistantState.IDLE,
    val statusMessage: String = "Ready, boss.",
    val spokenText: String = "",
    val responseText: String = "",
    val isMicActive: Boolean = false,
    val permissionDenied: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val errorMessage: String? = null,
    val rmsLevel: Float = 0f,
    val activeToolName: String? = null,
    val isAccessibilityEnabled: Boolean = false,
    val pendingConfirmation: PendingActionConfirmation? = null,
    val isWakeWordEnabled: Boolean = false,
    val wakeWordState: WakeWordState = WakeWordState.DISABLED,
    val pendingMemoryConfirmation: PendingMemoryConfirmation? = null,
    val storedMemoryCount: Int = 0,
    val screenAnalysisState: ScreenAnalysisState = ScreenAnalysisState.IDLE,
    val screenStatusMessage: String? = null,
    val taskState: com.jarvis.assistant.planner.TaskExecutionState = com.jarvis.assistant.planner.TaskExecutionState.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val taskStatusDetail: String? = null,
    val pendingDisambiguation: List<String>? = null
) {
    /**
     * Exact HUD status string matching Milestone 8 & Milestone 9 specifications.
     */
    val hudStateLabel: String
        get() = when {
            taskState == com.jarvis.assistant.planner.TaskExecutionState.PLANNING -> "🤖 TASK PLANNING"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.WAITING_CONFIRMATION -> "🎤 CONFIRMATION REQUIRED"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.EXECUTING -> "⚙ STEP $currentStepIndex/$totalSteps"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.VERIFYING -> "✓ STEP $currentStepIndex VERIFYING"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.RETRYING -> "↻ RETRYING STEP $currentStepIndex"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.COMPLETED -> "✓ TASK COMPLETE"
            taskState == com.jarvis.assistant.planner.TaskExecutionState.CANCELLED || taskState == com.jarvis.assistant.planner.TaskExecutionState.FAILED -> "⚠ TASK STOPPED"
            wakeWordState == WakeWordState.ERROR -> "⚠ WAKE WORD ERROR"
            state == AssistantState.SPEAKING -> "🔊 SPEAKING"
            state == AssistantState.THINKING || state == AssistantState.EXECUTING -> "🤖 PROCESSING"
            state == AssistantState.LISTENING || wakeWordState == WakeWordState.COMMAND_LISTENING -> "🎤 LISTENING FOR COMMAND"
            wakeWordState == WakeWordState.WAKE_DETECTED || wakeWordState == WakeWordState.TRANSITIONING -> "⚡ WAKE WORD DETECTED"
            wakeWordState == WakeWordState.STARTING -> "🎙 WAKE WORD STARTING"
            isWakeWordEnabled && (wakeWordState == WakeWordState.LISTENING || state == AssistantState.WAKE_LISTENING) -> "🎙 LISTENING LOCALLY"
            else -> "🎙 WAKE WORD OFF"
        }
}

enum class ScreenAnalysisState {
    IDLE,
    ANALYZING,
    PROTECTED,
    COMPLETE,
    FAILED
}
