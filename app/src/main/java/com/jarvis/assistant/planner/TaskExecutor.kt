package com.jarvis.assistant.planner

import android.util.Log
import com.jarvis.assistant.tools.SafetyDecision
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes multi-step TaskPlans sequentially with verification, bounded retries, and safety checks.
 */
open class TaskExecutor(
    private val toolRouter: ToolRouter,
    private val safetyManager: SafetyManager = SafetyManager(),
    private val actionVerifier: ActionVerifier = ActionVerifier(),
    private val recoveryStrategy: RecoveryStrategy = RecoveryStrategy()
) {

    companion object {
        private const val TAG = "TaskExecutor"
    }

    private var activeJob: Job? = null
    private val isCancelled = AtomicBoolean(false)
    private var currentExecutingPlan: TaskPlan? = null
    private var lastCompletedStepCount: Int = 0

    val isRunning: Boolean
        get() = activeJob?.isActive == true

    fun getCurrentPlan(): TaskPlan? = currentExecutingPlan
    fun getLastCompletedStep(): Int = lastCompletedStepCount

    /**
     * Executes the validated plan.
     */
    open suspend fun executePlan(
        plan: TaskPlan,
        onStatusUpdate: (state: TaskExecutionState, stepIndex: Int, totalSteps: Int, message: String) -> Unit,
        requestConfirmation: suspend (step: TaskStep) -> Boolean,
        onDisambiguationRequired: (suspend (query: String, candidates: List<String>) -> String?)? = null,
        startFromStep: Int = 1
    ): TaskPlanResult {
        isCancelled.set(false)
        currentExecutingPlan = plan
        val totalSteps = plan.steps.size
        var completedSteps = 0

        Log.i(TAG, "Starting execution of plan '${plan.taskId}' with $totalSteps steps (from step $startFromStep).")
        onStatusUpdate(TaskExecutionState.EXECUTING, 0, totalSteps, "Beginning task execution...")

        val overallTimeoutResult = withTimeoutOrNull(TaskPlanValidator.MAX_TOTAL_TASK_TIMEOUT_MS) {
            for ((index, step) in plan.steps.withIndex()) {
                val stepNumber = index + 1
                if (stepNumber < startFromStep) {
                    completedSteps++
                    lastCompletedStepCount = completedSteps
                    continue
                }
                if (isCancelled.get()) {
                    onStatusUpdate(TaskExecutionState.CANCELLED, completedSteps, totalSteps, "Task cancelled by user.")
                    return@withTimeoutOrNull TaskPlanResult(
                        isSuccess = false,
                        completedSteps = completedSteps,
                        totalSteps = totalSteps,
                        finalMessage = "Task execution cancelled.",
                        state = TaskExecutionState.CANCELLED
                    )
                }

                onStatusUpdate(
                    TaskExecutionState.EXECUTING,
                    stepNumber,
                    totalSteps,
                    "Step $stepNumber/$totalSteps: Executing ${step.action.replace('_', ' ')}..."
                )

                // Immediate post-callback cancellation check — handles cancellation called within onStatusUpdate
                if (isCancelled.get()) {
                    onStatusUpdate(TaskExecutionState.CANCELLED, completedSteps, totalSteps, "Task cancelled by user.")
                    return@withTimeoutOrNull TaskPlanResult(
                        isSuccess = false,
                        completedSteps = completedSteps,
                        totalSteps = totalSteps,
                        finalMessage = "Task execution cancelled.",
                        state = TaskExecutionState.CANCELLED
                    )
                }

                // 1. Handle confirmation if step requires it
                var isUserConfirmed = false
                if (step.requiresConfirmation) {
                    onStatusUpdate(
                        TaskExecutionState.WAITING_CONFIRMATION,
                        stepNumber,
                        totalSteps,
                        "Confirmation required for ${step.action}..."
                    )
                    val confirmed = requestConfirmation(step)
                    if (!confirmed) {
                        onStatusUpdate(TaskExecutionState.CANCELLED, completedSteps, totalSteps, "Step $stepNumber declined by user.")
                        return@withTimeoutOrNull TaskPlanResult(
                            isSuccess = false,
                            completedSteps = completedSteps,
                            totalSteps = totalSteps,
                            finalMessage = "Task stopped: Confirmation was declined.",
                            state = TaskExecutionState.CANCELLED
                        )
                    }
                    isUserConfirmed = true
                }

                // 2. SafetyManager Pre-check
                val tool = toolRouter.getTool(step.action)
                if (tool == null) {
                    onStatusUpdate(TaskExecutionState.FAILED, completedSteps, totalSteps, "Tool '${step.action}' not found.")
                    return@withTimeoutOrNull TaskPlanResult(
                        isSuccess = false,
                        completedSteps = completedSteps,
                        totalSteps = totalSteps,
                        finalMessage = "Task failed: Unknown tool '${step.action}'.",
                        state = TaskExecutionState.FAILED
                    )
                }

                val safetyDecision = safetyManager.evaluate(tool, step.arguments, isUserConfirmed = isUserConfirmed)
                when (safetyDecision) {
                    is SafetyDecision.Denied -> {
                        onStatusUpdate(TaskExecutionState.FAILED, completedSteps, totalSteps, "Safety policy blocked step: ${safetyDecision.reason}")
                        return@withTimeoutOrNull TaskPlanResult(
                            isSuccess = false,
                            completedSteps = completedSteps,
                            totalSteps = totalSteps,
                            finalMessage = "Action blocked by safety policy: ${safetyDecision.reason}",
                            state = TaskExecutionState.FAILED
                        )
                    }
                    is SafetyDecision.RequiresConfirmation -> {
                        if (!isUserConfirmed) {
                            val confirmed = requestConfirmation(step)
                            if (!confirmed) {
                                onStatusUpdate(TaskExecutionState.CANCELLED, completedSteps, totalSteps, "Confirmation declined.")
                                return@withTimeoutOrNull TaskPlanResult(
                                    isSuccess = false,
                                    completedSteps = completedSteps,
                                    totalSteps = totalSteps,
                                    finalMessage = "Task stopped: Confirmation declined.",
                                    state = TaskExecutionState.CANCELLED
                                )
                            }
                            isUserConfirmed = true
                        }
                    }
                    is SafetyDecision.Allowed -> {
                        // Proceed
                    }
                }

                // 3. Execute step with bounded retries
                var stepSuccess = false
                var retryAttempt = 0
                val maxRetries = minOf(step.retryCount, TaskPlanValidator.MAX_RETRIES_PER_STEP)

                while (retryAttempt <= maxRetries && !stepSuccess && !isCancelled.get()) {
                    if (retryAttempt > 0) {
                        onStatusUpdate(
                            TaskExecutionState.RETRYING,
                            stepNumber,
                            totalSteps,
                            "Retrying step $stepNumber (Attempt $retryAttempt/$maxRetries)..."
                        )
                        delay(500)
                    }

                    val toolResult = toolRouter.dispatch(
                        ToolCall(name = step.action, arguments = step.arguments),
                        isUserConfirmed = isUserConfirmed
                    )

                    // 4. Verify post-action state
                    onStatusUpdate(
                        TaskExecutionState.VERIFYING,
                        stepNumber,
                        totalSteps,
                        "Verifying step $stepNumber outcome..."
                    )

                    val verification = actionVerifier.verify(step, toolResult.message)

                    if (toolResult.success && verification.isVerified) {
                        stepSuccess = true
                        completedSteps++
                        lastCompletedStepCount = completedSteps
                        onStatusUpdate(
                            TaskExecutionState.EXECUTING,
                            stepNumber,
                            totalSteps,
                            "✓ Step $stepNumber verified: ${toolResult.message}"
                        )
                    } else {
                        val failReason = verification.reason ?: toolResult.message
                        Log.w(TAG, "Step $stepNumber verification failed: $failReason")

                        val recovery = recoveryStrategy.evaluate(step, retryAttempt, failReason)
                        when (recovery) {
                            RecoveryOutcome.RETRY -> {
                                retryAttempt++
                            }
                            RecoveryOutcome.ASK_USER -> {
                                onStatusUpdate(TaskExecutionState.WAITING_USER, stepNumber, totalSteps, "Ambiguity encountered.")
                                return@withTimeoutOrNull TaskPlanResult(
                                    isSuccess = false,
                                    completedSteps = completedSteps,
                                    totalSteps = totalSteps,
                                    finalMessage = "Task paused: User clarification needed.",
                                    state = TaskExecutionState.WAITING_USER
                                )
                            }
                            RecoveryOutcome.ABORT, RecoveryOutcome.SUCCESS -> {
                                onStatusUpdate(TaskExecutionState.FAILED, completedSteps, totalSteps, "Step $stepNumber failed: $failReason")
                                return@withTimeoutOrNull TaskPlanResult(
                                    isSuccess = false,
                                    completedSteps = completedSteps,
                                    totalSteps = totalSteps,
                                    finalMessage = "Task failed at step $stepNumber: $failReason",
                                    state = TaskExecutionState.FAILED
                                )
                            }
                        }
                    }
                }

                if (!stepSuccess) {
                    onStatusUpdate(TaskExecutionState.FAILED, completedSteps, totalSteps, "Step $stepNumber failed after retries.")
                    return@withTimeoutOrNull TaskPlanResult(
                        isSuccess = false,
                        completedSteps = completedSteps,
                        totalSteps = totalSteps,
                        finalMessage = "Task failed at step $stepNumber.",
                        state = TaskExecutionState.FAILED
                    )
                }
            }

            onStatusUpdate(TaskExecutionState.COMPLETED, completedSteps, totalSteps, "✓ Task completed successfully.")
            TaskPlanResult(
                isSuccess = true,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                finalMessage = "Task completed successfully, boss.",
                state = TaskExecutionState.COMPLETED
            )
        }

        return overallTimeoutResult ?: run {
            onStatusUpdate(TaskExecutionState.FAILED, completedSteps, totalSteps, "Task exceeded maximum timeout.")
            TaskPlanResult(
                isSuccess = false,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                finalMessage = "Task stopped: Execution timed out after ${TaskPlanValidator.MAX_TOTAL_TASK_TIMEOUT_MS / 1000}s.",
                state = TaskExecutionState.FAILED
            )
        }
    }

    /**
     * Cooperatively cancels active task execution immediately.
     */
    fun cancel() {
        isCancelled.set(true)
        activeJob?.cancel()
        activeJob = null
        Log.i(TAG, "TaskExecutor cancelled.")
    }
}
