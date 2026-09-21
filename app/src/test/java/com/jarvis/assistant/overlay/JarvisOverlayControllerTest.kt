package com.jarvis.assistant.overlay

import android.content.Context
import com.jarvis.assistant.planner.TaskExecutionState
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.home.HomeUiState
import com.jarvis.assistant.ui.home.PendingActionConfirmation
import com.jarvis.assistant.ui.home.ScreenAnalysisState
import com.jarvis.assistant.wakeword.WakeWordState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JarvisOverlayControllerTest {

    private class FakeAssistantBridge : AssistantBridge {
        var micTappedCount = 0
        var lastCommand: String? = null
        var stopTaskCalled = false
        var screenAnalysisRequested = false
        var confirmActionCalled = false
        var cancelActionCalled = false

        override fun onMicTapped() {
            micTappedCount++
        }

        override fun handleCommand(query: String) {
            lastCommand = query
        }

        override fun stopTask() {
            stopTaskCalled = true
        }

        override fun requestScreenAnalysis() {
            screenAnalysisRequested = true
        }

        override fun confirmPendingAction() {
            confirmActionCalled = true
        }

        override fun cancelPendingAction() {
            cancelActionCalled = true
        }
    }

    private val fakeBridge = FakeAssistantBridge()

    @Before
    fun setUp() {
        JarvisOverlayController.registerBridge(fakeBridge)
        JarvisOverlayController.dismissPopups()
    }

    @After
    fun tearDown() {
        JarvisOverlayController.unregisterBridge()
        JarvisOverlayController.notifyServiceDestroyed()
    }

    @Test
    fun computeOrbVisualState_mapsAllAssistantStatesAccurately() {
        // 1. Idle
        assertEquals(
            OrbVisualState.IDLE,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(state = AssistantState.IDLE))
        )

        // 2. Listening
        assertEquals(
            OrbVisualState.LISTENING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(state = AssistantState.LISTENING))
        )
        assertEquals(
            OrbVisualState.LISTENING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(wakeWordState = WakeWordState.COMMAND_LISTENING))
        )

        // 3. Processing
        assertEquals(
            OrbVisualState.PROCESSING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(state = AssistantState.THINKING))
        )
        assertEquals(
            OrbVisualState.PROCESSING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.PLANNING))
        )

        // 4. Analyzing Screen
        assertEquals(
            OrbVisualState.ANALYZING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(screenAnalysisState = ScreenAnalysisState.ANALYZING))
        )

        // 5. Executing
        assertEquals(
            OrbVisualState.EXECUTING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.EXECUTING))
        )
        assertEquals(
            OrbVisualState.EXECUTING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.VERIFYING))
        )
        assertEquals(
            OrbVisualState.EXECUTING,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.RETRYING))
        )

        // 6. Success
        assertEquals(
            OrbVisualState.SUCCESS,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.COMPLETED))
        )

        // 7. Error
        assertEquals(
            OrbVisualState.ERROR,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(taskState = TaskExecutionState.FAILED))
        )
        assertEquals(
            OrbVisualState.ERROR,
            JarvisOverlayController.computeOrbVisualState(HomeUiState(state = AssistantState.ERROR))
        )
    }

    @Test
    fun onOrbTapped_togglesPanelAndClosesQuickActions() {
        assertFalse(JarvisOverlayController.isPanelOpen.value)

        JarvisOverlayController.onOrbTapped()
        assertTrue(JarvisOverlayController.isPanelOpen.value)
        assertFalse(JarvisOverlayController.isQuickActionsOpen.value)

        JarvisOverlayController.onOrbTapped()
        assertFalse(JarvisOverlayController.isPanelOpen.value)
    }

    @Test
    fun onOrbLongPressed_togglesQuickActionsAndClosesPanel() {
        assertFalse(JarvisOverlayController.isQuickActionsOpen.value)

        JarvisOverlayController.onOrbLongPressed()
        assertTrue(JarvisOverlayController.isQuickActionsOpen.value)
        assertFalse(JarvisOverlayController.isPanelOpen.value)

        JarvisOverlayController.onOrbLongPressed()
        assertFalse(JarvisOverlayController.isQuickActionsOpen.value)
    }

    @Test
    fun quickActions_routeThroughAuthoritativeAssistantPipeline() {
        // Create mock or dummy context
        val dummyContext = android.app.Application()

        // 1. Ask JARVIS
        JarvisOverlayController.onQuickAction(QuickAction.ASK_JARVIS, dummyContext)
        assertEquals(1, fakeBridge.micTappedCount)

        // 2. Analyze Screen
        JarvisOverlayController.onQuickAction(QuickAction.ANALYZE_SCREEN, dummyContext)
        assertTrue(fakeBridge.screenAnalysisRequested)

        // 3. Go Home
        JarvisOverlayController.onQuickAction(QuickAction.GO_HOME, dummyContext)
        assertEquals("go home", fakeBridge.lastCommand)

        // 4. Back
        JarvisOverlayController.onQuickAction(QuickAction.BACK, dummyContext)
        assertEquals("press back", fakeBridge.lastCommand)

        // 5. Stop Current Task
        JarvisOverlayController.onQuickAction(QuickAction.STOP_TASK, dummyContext)
        assertTrue(fakeBridge.stopTaskCalled)
    }

    @Test
    fun safetyConfirmations_invokeBridgeExplicitly() {
        JarvisOverlayController.onConfirmSafetyAction()
        assertTrue(fakeBridge.confirmActionCalled)

        JarvisOverlayController.onCancelSafetyAction()
        assertTrue(fakeBridge.cancelActionCalled)
    }

    @Test
    fun onStopTask_invokesBridgeStopTask() {
        JarvisOverlayController.onStopTask()
        assertTrue(fakeBridge.stopTaskCalled)
    }
}
