package com.jarvis.assistant.context

import com.jarvis.assistant.planner.TaskExecutionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationContextManagerTest {

    private var simulatedTime: Long = 1_000_000L
    private lateinit var manager: ConversationContextManager

    @Before
    fun setUp() {
        simulatedTime = 1_000_000L
        manager = ConversationContextManager(timeProvider = { simulatedTime })
    }

    @Test
    fun recordTurns_accumulatesTurnsUpToMaxLimit() {
        // Record 15 turns; should retain only the last 10 (MAX_RECENT_TURNS)
        for (i in 1..15) {
            manager.recordUserTurn("User query $i")
            manager.recordAssistantTurn("Assistant reply $i")
        }

        val context = manager.getConversationContext()
        assertEquals(ContextConstants.MAX_RECENT_TURNS, context.recentTurns.size)
        // Last turn should be from turn 15
        assertEquals("Assistant reply 15", context.recentTurns.last().text)
    }

    @Test
    fun recordTurns_sanitizesSensitiveCredentials() {
        manager.recordUserTurn("My password is SuperSecretPassword123")
        val context = manager.getConversationContext()

        assertFalse(context.recentTurns.last().text.contains("SuperSecretPassword123"))
        assertTrue(context.recentTurns.last().text.contains("[REDACTED_PASSWORD]"))
    }

    @Test
    fun updateCurrentApp_storesForegroundDetails() {
        manager.updateCurrentApp("YouTube", "com.google.android.youtube", "WatchActivity")
        val context = manager.getConversationContext()

        assertEquals("YouTube", context.currentApp)
        assertEquals("com.google.android.youtube", context.currentPackage)
        assertEquals("WatchActivity", context.currentActivity)
    }

    @Test
    fun updateScreenSummary_boundsAndSanitizesElements() {
        val elements = (1..25).map { idx ->
            ScreenElementContext(label = "Element $idx", type = "result")
        }
        manager.updateScreenSummary("Search results for Android", elements)

        val context = manager.getConversationContext()
        assertEquals("Search results for Android", context.currentScreenSummary)
        // Should be capped to MAX_TRACKED_SCREEN_ELEMENTS (15)
        assertEquals(ContextConstants.MAX_TRACKED_SCREEN_ELEMENTS, context.detectedElements.size)
    }

    @Test
    fun lazyExpiration_resetsContextAfterExpiryThreshold() {
        manager.recordUserTurn("Initial user prompt")
        manager.updateCurrentApp("YouTube", "com.google.android.youtube")

        val initialContext = manager.getConversationContext()
        assertEquals("YouTube", initialContext.currentApp)

        // Advance simulated time past 5 minutes (300,000 ms)
        simulatedTime += ContextConstants.CONTEXT_EXPIRY_MS + 1000L

        // Accessing context should lazily reset it
        val expiredContext = manager.getConversationContext()
        assertNull(expiredContext.currentApp)
        assertTrue(expiredContext.recentTurns.isEmpty())
    }

    @Test
    fun clearContext_resetsShortTermAndTaskContext() {
        manager.recordUserTurn("Find tutorial")
        manager.updateCurrentApp("YouTube", "com.google.android.youtube")
        manager.updateTaskProgress(
            taskId = "task_99",
            description = "Download file",
            currentStep = 1,
            totalSteps = 3,
            lastSuccessfulStep = 0,
            state = TaskExecutionState.EXECUTING
        )

        manager.clearContext()

        val context = manager.getConversationContext()
        val task = manager.getActiveTaskContext()

        assertNull(context.currentApp)
        assertTrue(context.recentTurns.isEmpty())
        assertNull(task.activeTaskId)
        assertEquals(TaskExecutionState.IDLE, task.taskState)
    }

    @Test
    fun cancelActiveTask_updatesTaskStateToCancelled() {
        manager.updateTaskProgress(
            taskId = "task_abc",
            description = "Search YouTube",
            currentStep = 2,
            totalSteps = 4,
            lastSuccessfulStep = 1,
            state = TaskExecutionState.EXECUTING
        )

        manager.cancelActiveTask()

        val task = manager.getActiveTaskContext()
        assertEquals(TaskExecutionState.CANCELLED, task.taskState)
    }

    @Test
    fun toBoundedSummary_enforcesMaxLength() {
        for (i in 1..10) {
            manager.recordUserTurn("A".repeat(600))
        }

        val summary = manager.getConversationContext().toBoundedSummary()
        assertTrue(summary.length <= ContextConstants.MAX_CONTEXT_TEXT_LENGTH + 3) // +3 for trailing "..."
    }
}
