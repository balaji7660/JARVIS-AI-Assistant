package com.jarvis.assistant.context

import android.util.Log
import com.jarvis.assistant.planner.TaskExecutionState

/**
 * Manages short-term conversational context and active task execution state.
 *
 * Enforces strict boundaries:
 * - [ContextConstants.MAX_RECENT_TURNS] maximum conversation turns.
 * - [ContextConstants.MAX_RECENT_ENTITIES] maximum tracked entities.
 * - [ContextConstants.MAX_CONTEXT_TEXT_LENGTH] maximum total text length.
 * - [ContextConstants.CONTEXT_EXPIRY_MS] lazy expiration after 5 minutes of inactivity.
 *
 * Long-term Room memory and short-term context are strictly decoupled.
 * Clearing context here NEVER deletes or touches permanent Room memories.
 */
class ConversationContextManager(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private const val TAG = "ContextManager"
    }

    private val lock = Any()

    @Volatile
    private var conversationContext: ConversationContext = ConversationContext(timestamp = timeProvider())

    @Volatile
    private var activeTaskContext: ActiveTaskContext = ActiveTaskContext(timestamp = timeProvider())

    /**
     * Retrieves the current conversation context, performing lazy expiration if the context is older than [ContextConstants.CONTEXT_EXPIRY_MS].
     */
    fun getConversationContext(): ConversationContext {
        synchronized(lock) {
            checkAndApplyLazyExpiration()
            return conversationContext
        }
    }

    /**
     * Retrieves the active task context, performing lazy expiration if older than [ContextConstants.CONTEXT_EXPIRY_MS].
     */
    fun getActiveTaskContext(): ActiveTaskContext {
        synchronized(lock) {
            checkAndApplyLazyExpiration()
            return activeTaskContext
        }
    }

    /**
     * Records a new user utterance turn.
     * Sanitizes input to strip credentials, OTPs, or passwords before storage.
     */
    fun recordUserTurn(text: String, intent: String? = null) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedText = ContextPrivacyFilter.sanitize(text.trim())
            val turn = ConversationTurn(role = "user", text = sanitizedText, timestamp = timeProvider())
            val updatedTurns = (conversationContext.recentTurns + turn).takeLast(ContextConstants.MAX_RECENT_TURNS)

            // Extract potential entity/app mention
            val updatedEntities = extractAndAppendEntities(sanitizedText, conversationContext.recentEntities)

            conversationContext = conversationContext.copy(
                lastUserIntent = intent ?: sanitizedText,
                recentTurns = updatedTurns,
                recentEntities = updatedEntities,
                timestamp = timeProvider()
            )
            Log.d(TAG, "Recorded user turn. Total turns: ${updatedTurns.size}")
        }
    }

    /**
     * Records assistant response turn.
     */
    fun recordAssistantTurn(text: String) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedText = ContextPrivacyFilter.sanitize(text.trim())
            val turn = ConversationTurn(role = "assistant", text = sanitizedText, timestamp = timeProvider())
            val updatedTurns = (conversationContext.recentTurns + turn).takeLast(ContextConstants.MAX_RECENT_TURNS)

            conversationContext = conversationContext.copy(
                lastAssistantResponse = sanitizedText,
                recentTurns = updatedTurns,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Updates current foreground application details.
     */
    fun updateCurrentApp(appName: String?, packageName: String?, activityName: String? = null) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedAppName = appName?.let { ContextPrivacyFilter.sanitize(it) }
            val sanitizedPkg = packageName?.let { ContextPrivacyFilter.sanitize(it) }

            conversationContext = conversationContext.copy(
                currentApp = sanitizedAppName ?: conversationContext.currentApp,
                currentPackage = sanitizedPkg ?: conversationContext.currentPackage,
                currentActivity = activityName ?: conversationContext.currentActivity,
                timestamp = timeProvider()
            )

            // Also update active task app context if active
            if (activeTaskContext.taskState != TaskExecutionState.IDLE) {
                activeTaskContext = activeTaskContext.copy(
                    currentApp = sanitizedAppName ?: activeTaskContext.currentApp,
                    timestamp = timeProvider()
                )
            }
        }
    }

    /**
     * Updates screen summary and detected elements.
     * Sanitizes all element labels and text.
     */
    fun updateScreenSummary(summary: String?, elements: List<ScreenElementContext> = emptyList()) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedSummary = summary?.let { ContextPrivacyFilter.sanitize(it) }
            val sanitizedElements = elements.take(ContextConstants.MAX_TRACKED_SCREEN_ELEMENTS).map { elem ->
                elem.copy(label = ContextPrivacyFilter.sanitize(elem.label))
            }

            conversationContext = conversationContext.copy(
                currentScreenSummary = sanitizedSummary,
                detectedElements = sanitizedElements,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Sets the current active interaction target (e.g., search result, button label).
     */
    fun setCurrentTarget(target: String?) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedTarget = target?.let { ContextPrivacyFilter.sanitize(it) }
            conversationContext = conversationContext.copy(
                currentTarget = sanitizedTarget,
                timestamp = timeProvider()
            )
            activeTaskContext = activeTaskContext.copy(
                currentTarget = sanitizedTarget,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Updates the status of an ongoing automation task.
     */
    fun updateTaskProgress(
        taskId: String?,
        description: String?,
        currentStep: Int,
        totalSteps: Int,
        lastSuccessfulStep: Int,
        state: TaskExecutionState,
        expectedScreen: String? = null
    ) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            activeTaskContext = activeTaskContext.copy(
                activeTaskId = taskId,
                taskDescription = description?.let { ContextPrivacyFilter.sanitize(it) },
                currentStep = currentStep,
                totalSteps = totalSteps,
                lastSuccessfulStep = lastSuccessfulStep,
                taskState = state,
                expectedScreen = expectedScreen ?: activeTaskContext.expectedScreen,
                currentApp = conversationContext.currentApp,
                timestamp = timeProvider()
            )

            conversationContext = conversationContext.copy(
                currentTaskId = taskId,
                currentTaskDescription = description?.let { ContextPrivacyFilter.sanitize(it) },
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Records the execution outcome of the last tool action.
     */
    fun recordLastAction(actionName: String, resultMessage: String) {
        synchronized(lock) {
            checkAndApplyLazyExpiration()

            val sanitizedMsg = ContextPrivacyFilter.sanitize(resultMessage)
            conversationContext = conversationContext.copy(
                lastAction = actionName,
                lastActionResult = sanitizedMsg,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Marks the active task as cancelled (e.g. from "Stop" command).
     */
    fun cancelActiveTask() {
        synchronized(lock) {
            activeTaskContext = activeTaskContext.copy(
                taskState = TaskExecutionState.CANCELLED,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Clears only the active task context while retaining conversational context.
     */
    fun clearActiveTask() {
        synchronized(lock) {
            activeTaskContext = ActiveTaskContext(timestamp = timeProvider())
            conversationContext = conversationContext.copy(
                currentTaskId = null,
                currentTaskDescription = null,
                timestamp = timeProvider()
            )
        }
    }

    /**
     * Resets both short-term conversation context and active task context.
     *
     * CRITICAL: Does NOT modify, touch, or delete Room-based long-term memories.
     */
    fun clearContext() {
        synchronized(lock) {
            val now = timeProvider()
            conversationContext = ConversationContext(timestamp = now)
            activeTaskContext = ActiveTaskContext(timestamp = now)
            Log.i(TAG, "Short-term conversation and active task context cleared.")
        }
    }

    /**
     * Checks if context has expired. If so, resets it automatically.
     */
    private fun checkAndApplyLazyExpiration() {
        val now = timeProvider()
        if (conversationContext.isExpired(now)) {
            Log.i(TAG, "ConversationContext expired (${now - conversationContext.timestamp}ms > ${ContextConstants.CONTEXT_EXPIRY_MS}ms). Resetting.")
            conversationContext = ConversationContext(timestamp = now)
        }
        if (activeTaskContext.isExpired(now)) {
            Log.i(TAG, "ActiveTaskContext expired. Resetting.")
            activeTaskContext = ActiveTaskContext(timestamp = now)
        }
    }

    private fun extractAndAppendEntities(text: String, existing: List<String>): List<String> {
        val candidateEntities = mutableListOf<String>()
        val lower = text.lowercase()

        val triggers = listOf("for ", "on ", "in ", "search ", "open ")
        for (trigger in triggers) {
            if (lower.contains(trigger)) {
                val sub = text.substring(lower.indexOf(trigger) + trigger.length).trim()
                if (sub.isNotBlank() && sub.length in 2..40 && !sub.contains("and ") && !candidateEntities.contains(sub)) {
                    candidateEntities.add(sub)
                }
            }
        }

        val combined = (existing + candidateEntities).distinct()
        return combined.takeLast(ContextConstants.MAX_RECENT_ENTITIES)
    }
}
