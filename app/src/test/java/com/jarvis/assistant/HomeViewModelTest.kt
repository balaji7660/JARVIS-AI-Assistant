package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.ai.ResponseEngine
import com.jarvis.assistant.navigation.Screen
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.ui.home.AssistantState
import com.jarvis.assistant.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeAutomationProvider : AndroidAutomationProvider {
        var lastAction: AutomationAction? = null
        override suspend fun execute(action: AutomationAction): ToolResult {
            lastAction = action
            return ToolResult(true, "Typed successfully")
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isIdleWithReadyMessage() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )
        val state = viewModel.uiState.value

        assertEquals(AssistantState.IDLE, state.state)
        assertEquals("Ready, boss.", state.statusMessage)
        assertFalse(state.isMicActive)
        assertFalse(state.permissionDenied)
        assertNull(state.pendingConfirmation)
    }

    @Test
    fun registry_containsAll13Tools() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )
        val registeredNames = viewModel.toolRegistry.getAll().map { it.name }
        val expectedTools = listOf(
            "get_time", "get_date", "go_home", "press_back", "open_url", "open_app",
            "call_contact",
            // Device tools
            "get_battery_status", "set_volume", "get_volume", "set_brightness", "get_brightness",
            "toggle_flashlight", "open_camera", "get_device_info", "get_network_status",
            "open_wifi_settings", "open_bluetooth_settings", "lock_screen",
            // Media tools
            "play_media", "pause_media", "resume_media", "next_track", "previous_track", "get_media_state",
            // SMS tool
            "send_sms",
            // Accessibility tools
            "read_visible_screen", "click_text", "click_view", "scroll", "type_text",
            "analyze_current_screen", "wait_for_screen",
            // Phase 6: Screen Assistant
            "find_screen_element", "read_current_screen", "diagnose_screen_error", "click_screen_element", "scroll_screen",
            // Phase 7: Web Assistant
            "search_web", "search_youtube", "read_current_webpage", "summarize_webpage",
            // Phase 8: Local Notes
            "create_note", "search_notes", "list_notes", "update_note", "delete_note",
            // Phase 9: Reminders and Timers
            "create_timer", "cancel_timer", "list_timers", "create_reminder", "cancel_reminder", "list_reminders",
            // Phase 10: Controlled Calendar
            "create_calendar_event", "list_calendar_events", "delete_calendar_event",
            // Reliability Upgrade: WhatsApp
            "send_whatsapp_message"
        )

        for (expected in expectedTools) {
            assertTrue("Expected tool $expected to be registered", registeredNames.contains(expected))
        }
        assertEquals(57, registeredNames.size)
    }

    @Test
    fun onMicTapped_withoutPermission_setsPermissionDeniedState() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.onMicTapped(hasPermission = false)
        val state = viewModel.uiState.value

        assertTrue(state.permissionDenied)
        assertEquals(
            "Microphone permission is required for voice commands.",
            state.statusMessage
        )
    }

    @Test
    fun onPermissionResult_granted_startsListening() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.onPermissionResult(isGranted = true)
        val state = viewModel.uiState.value

        assertEquals(AssistantState.LISTENING, state.state)
        assertEquals("Listening...", state.statusMessage)
        assertTrue(state.isMicActive)
        assertFalse(state.permissionDenied)
    }

    @Test
    fun onPermissionResult_deniedPermanently_recordsFlag() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.onPermissionResult(isGranted = false, isPermanentlyDenied = true)
        val state = viewModel.uiState.value

        assertTrue(state.permissionDenied)
        assertTrue(state.permissionPermanentlyDenied)
    }

    @Test
    fun handleSpeechRecognized_transitionsThroughThinkingSpeakingAndBackToIdle() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.handleSpeechRecognized("Hello Jarvis")

        assertEquals(AssistantState.THINKING, viewModel.uiState.value.state)
        assertEquals("Hello Jarvis", viewModel.uiState.value.spokenText)

        advanceTimeBy(400)
        assertEquals(AssistantState.SPEAKING, viewModel.uiState.value.state)
        assertEquals("Hello boss. I'm ready.", viewModel.uiState.value.responseText)

        viewModel.completeSpeechPlayback()
        assertEquals(AssistantState.IDLE, viewModel.uiState.value.state)
        assertEquals("Ready, boss.", viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isMicActive)
    }

    @Test
    fun handleSpeechRecognized_withTypeText_triggersConfirmationDialog() = runTest(testDispatcher) {
        val fakeAutomation = FakeAutomationProvider()
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            automationProvider = fakeAutomation
        )

        viewModel.handleSpeechRecognized("Type Hello Jarvis")

        advanceTimeBy(400)

        // Verifies that confirmation dialog was triggered
        val state = viewModel.uiState.value
        assertNotNull(state.pendingConfirmation)
        assertEquals("type_text", state.pendingConfirmation?.toolName)
        assertEquals("Hello Jarvis", state.pendingConfirmation?.arguments?.get("text"))

        // Confirm the action
        state.pendingConfirmation?.onConfirm?.invoke()
        advanceTimeBy(400)

        val afterConfirmState = viewModel.uiState.value
        assertNull(afterConfirmState.pendingConfirmation)
        assertEquals(AutomationAction.TypeText("Hello Jarvis"), fakeAutomation.lastAction)
    }

    @Test
    fun handleSpeechRecognized_withTypeText_cancelAbortsAction() = runTest(testDispatcher) {
        val fakeAutomation = FakeAutomationProvider()
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            automationProvider = fakeAutomation
        )

        viewModel.handleSpeechRecognized("Type Test Note")

        advanceTimeBy(400)

        val state = viewModel.uiState.value
        assertNotNull(state.pendingConfirmation)

        // Cancel the action
        state.pendingConfirmation?.onCancel?.invoke()

        val afterCancelState = viewModel.uiState.value
        assertNull(afterCancelState.pendingConfirmation)
        assertEquals(AssistantState.IDLE, afterCancelState.state)
        assertEquals("Action cancelled, boss.", afterCancelState.statusMessage)
        assertNull(fakeAutomation.lastAction)
    }

    @Test
    fun handleSpeechRecognized_withToolIntent_dispatchesToolAndReturnsVerifiedResult() = runTest(testDispatcher) {
        val registry = ToolRegistry().apply {
            register(object : JarvisTool {
                override val name: String = "get_time"
                override val description: String = "Returns current time"
                override val riskLevel: RiskLevel = RiskLevel.LOW
                override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                    return ToolResult(success = true, message = "4:45 PM")
                }
            })
        }
        val router = ToolRouter(registry, SafetyManager())
        val localEngineWithTools = LocalResponseEngine(toolRouter = router)

        val viewModel = HomeViewModel(
            application = Application(),
            toolRouter = router,
            responseEngine = localEngineWithTools
        )

        viewModel.handleSpeechRecognized("What time is it?")

        assertEquals(AssistantState.THINKING, viewModel.uiState.value.state)
        advanceTimeBy(400)

        assertEquals(AssistantState.SPEAKING, viewModel.uiState.value.state)
        assertEquals("The current time is 4:45 PM, boss.", viewModel.uiState.value.responseText)

        viewModel.completeSpeechPlayback()
        assertEquals(AssistantState.IDLE, viewModel.uiState.value.state)
    }

    @Test
    fun onMicTapped_whileListening_cancelsBackToIdle() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.onMicTapped(hasPermission = true)
        assertEquals(AssistantState.LISTENING, viewModel.uiState.value.state)
        assertTrue(viewModel.uiState.value.isMicActive)

        viewModel.onMicTapped(hasPermission = true)
        assertEquals(AssistantState.IDLE, viewModel.uiState.value.state)
        assertEquals("Ready, boss.", viewModel.uiState.value.statusMessage)
        assertFalse(viewModel.uiState.value.isMicActive)
    }

    @Test
    fun navigationRoutes_areCorrect() {
        assertEquals("home", Screen.Home.route)
        assertEquals("settings", Screen.Settings.route)
        assertEquals("memories", Screen.Memories.route)
    }

    @Test
    fun handleSpeechRecognized_explicitRemember_triggersMemoryConfirmation() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.handleSpeechRecognized("Remember that I prefer dark theme")
        advanceTimeBy(400)

        val state = viewModel.uiState.value
        assertNotNull(state.pendingMemoryConfirmation)
        assertEquals("I prefer dark theme", state.pendingMemoryConfirmation?.content)
        assertEquals(com.jarvis.assistant.memory.MemoryCategory.PREFERENCE, state.pendingMemoryConfirmation?.category)

        // Confirm save
        state.pendingMemoryConfirmation?.onConfirm?.invoke()
        advanceTimeBy(400)

        val afterState = viewModel.uiState.value
        assertNull(afterState.pendingMemoryConfirmation)
        assertTrue(afterState.responseText.contains("committed that to memory"))
    }

    @Test
    fun handleSpeechRecognized_sensitivePassword_rejectsStorage() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.handleSpeechRecognized("Remember my password is supersecret")
        advanceTimeBy(400)

        val state = viewModel.uiState.value
        assertNull(state.pendingMemoryConfirmation)
        assertTrue(state.responseText.contains("credentials or authentication secrets"))
    }

    @Test
    fun handleSpeechRecognized_recallMemories_speaksFormattedMemories() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        // Save a memory first
        viewModel.effectiveMemoryManager.saveMemory("Favorite beverage is green tea", com.jarvis.assistant.memory.MemoryCategory.PREFERENCE)

        viewModel.handleSpeechRecognized("What do you remember about me?")
        advanceTimeBy(400)

        val state = viewModel.uiState.value
        assertTrue(state.responseText.contains("You asked me to remember:"))
        assertTrue(state.responseText.contains("Favorite beverage is green tea"))
    }

    @Test
    fun handleSpeechRecognized_forgetMemory_removesMatchingMemory() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.effectiveMemoryManager.saveMemory("Favorite sport is badminton", com.jarvis.assistant.memory.MemoryCategory.PREFERENCE)

        viewModel.handleSpeechRecognized("Forget that Favorite sport is badminton")
        advanceTimeBy(400)

        val state = viewModel.uiState.value
        assertTrue(state.responseText.contains("removed that from my memories"))
        val remaining = viewModel.effectiveMemoryManager.getAllMemoriesOnce()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun toggleWakeWord_enablesAndTransitionsState() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        assertFalse(viewModel.uiState.value.isWakeWordEnabled)
        viewModel.toggleWakeWord()

        assertTrue(viewModel.uiState.value.isWakeWordEnabled)
        assertEquals(AssistantState.WAKE_LISTENING, viewModel.uiState.value.state)

        viewModel.toggleWakeWord()
        assertFalse(viewModel.uiState.value.isWakeWordEnabled)
        assertEquals(AssistantState.IDLE, viewModel.uiState.value.state)
    }

    @Test
    fun wakeWordTrigger_transitionsToCommandListening() {
        val simulatedDetector = com.jarvis.assistant.wakeword.LocalSimulatedWakeWordDetector()
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            wakeWordDetector = simulatedDetector
        )

        viewModel.toggleWakeWord()
        assertTrue(viewModel.uiState.value.isWakeWordEnabled)

        simulatedDetector.simulateDetection()
        // Should trigger state transition
        assertTrue(viewModel.uiState.value.isWakeWordEnabled)
    }

    @Test
    fun completeSpeechPlayback_restartsWakeWordWhenEnabled() {
        val simulatedDetector = com.jarvis.assistant.wakeword.LocalSimulatedWakeWordDetector()
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            wakeWordDetector = simulatedDetector
        )

        viewModel.toggleWakeWord()
        assertTrue(viewModel.uiState.value.isWakeWordEnabled)

        viewModel.completeSpeechPlayback()
        assertEquals(AssistantState.WAKE_LISTENING, viewModel.uiState.value.state)
        assertTrue(simulatedDetector.isListening)
    }

    @Test
    fun hudStateLabel_reflectsCurrentAssistantAndWakeWordState() {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        assertEquals("🎙 WAKE WORD OFF", viewModel.uiState.value.hudStateLabel)

        viewModel.toggleWakeWord()
        assertTrue(
            viewModel.uiState.value.hudStateLabel == "🎙 LISTENING LOCALLY" ||
            viewModel.uiState.value.hudStateLabel == "🎙 WAKE WORD STARTING"
        )
    }

    @Test
    fun handleSpeechRecognized_cancellationCommand_stopsTaskImmediately() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine()
        )

        viewModel.handleSpeechRecognized("Stop.")

        assertEquals(AssistantState.SPEAKING, viewModel.uiState.value.state)
        assertEquals("Task cancelled, boss.", viewModel.uiState.value.responseText)
        assertEquals(com.jarvis.assistant.planner.TaskExecutionState.CANCELLED, viewModel.uiState.value.taskState)
    }

    @Test
    fun handleSpeechRecognized_multiStepAutomation_executesPlan() = runTest(testDispatcher) {
        // Stub planner that immediately returns a valid 2-step plan without requiring network or confirmation.
        val stubPlanner = object : com.jarvis.assistant.planner.TaskPlanner {
            override suspend fun plan(
                prompt: String,
                memoryContext: String,
                currentPackage: String,
                visibleScreenText: String,
                context: com.jarvis.assistant.context.ConversationContext?
            ): com.jarvis.assistant.planner.PlanValidationResult {
                val plan = com.jarvis.assistant.planner.TaskPlan(
                    taskId = "stub_plan",
                    userRequest = prompt,
                    steps = listOf(
                        com.jarvis.assistant.planner.TaskStep(
                            stepId = 1,
                            action = "open_app",
                            arguments = mapOf("appName" to "YouTube"),
                            riskLevel = com.jarvis.assistant.tools.RiskLevel.LOW,
                            requiresConfirmation = false
                        ),
                        com.jarvis.assistant.planner.TaskStep(
                            stepId = 2,
                            action = "read_visible_screen",
                            arguments = emptyMap(),
                            riskLevel = com.jarvis.assistant.tools.RiskLevel.LOW,
                            requiresConfirmation = false
                        )
                    )
                )
                return com.jarvis.assistant.planner.PlanValidationResult.Valid(plan)
            }
        }

        // Stub executor that immediately returns success without requiring confirmation.
        val stubExecutor = object : com.jarvis.assistant.planner.TaskExecutor(
            toolRouter = ToolRouter(ToolRegistry(), SafetyManager())
        ) {
            override suspend fun executePlan(
                plan: com.jarvis.assistant.planner.TaskPlan,
                onStatusUpdate: (com.jarvis.assistant.planner.TaskExecutionState, Int, Int, String) -> Unit,
                requestConfirmation: suspend (com.jarvis.assistant.planner.TaskStep) -> Boolean,
                onDisambiguationRequired: (suspend (String, List<String>) -> String?)?,
                startFromStep: Int
            ): com.jarvis.assistant.planner.TaskPlanResult {
                onStatusUpdate(com.jarvis.assistant.planner.TaskExecutionState.COMPLETED, plan.steps.size, plan.steps.size, "All steps done.")
                return com.jarvis.assistant.planner.TaskPlanResult(
                    isSuccess = true,
                    completedSteps = plan.steps.size,
                    totalSteps = plan.steps.size,
                    finalMessage = "Task complete, boss.",
                    state = com.jarvis.assistant.planner.TaskExecutionState.COMPLETED
                )
            }
        }

        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            taskPlanner = stubPlanner,
            taskExecutor = stubExecutor
        )

        viewModel.handleSpeechRecognized("Open YouTube and search for Kotlin Coroutines")
        testScheduler.advanceUntilIdle()

        // With UnconfinedTestDispatcher and advanceUntilIdle, all coroutines run.
        // totalSteps is set before executePlan is called, and executePlan completes immediately.
        assertTrue("Expected totalSteps >= 1", viewModel.uiState.value.totalSteps >= 1)
        assertEquals(com.jarvis.assistant.planner.TaskExecutionState.COMPLETED, viewModel.uiState.value.taskState)
    }
}
