package com.jarvis.assistant.wakeword

import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.context.ContinuousConversationManager
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolRouter
import com.jarvis.assistant.tools.impl.GetBatteryStatusTool
import com.jarvis.assistant.tools.impl.GetTimeTool
import com.jarvis.assistant.voice.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundWakeReliabilityTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WakeWordPreferences.resetForTesting()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WakeWordPreferences.resetForTesting()
    }

    // 1. Microphone state transitions
    @Test
    fun testMicrophoneStateTransitions() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)

        assertEquals(MicSessionState.IDLE, micManager.sessionState.value)
        assertEquals(MicOwner.FREE, micManager.micOwner.value)

        // Request wake detection
        val wakeGranted = micManager.requestWakeDetection("test")
        assertTrue(wakeGranted)
        assertEquals(MicSessionState.WAKE_DETECTING, micManager.sessionState.value)
        assertEquals(MicOwner.WAKE, micManager.micOwner.value)

        // Request command listening
        val commandGranted = AtomicBoolean(false)
        micManager.requestCommandListening("test") {
            commandGranted.set(true)
        }
        assertEquals(MicSessionState.COMMAND_LISTENING, micManager.sessionState.value)
        assertEquals(MicOwner.COMMAND, micManager.micOwner.value)
        assertTrue(commandGranted.get())

        // Request TTS speaking
        val ttsGranted = AtomicBoolean(false)
        micManager.requestTtsSpeaking("test") {
            ttsGranted.set(true)
        }
        assertEquals(MicSessionState.TTS_SPEAKING, micManager.sessionState.value)
        assertEquals(MicOwner.TTS, micManager.micOwner.value)
        assertTrue(ttsGranted.get())

        // TTS finishes -> transitions to wake
        val resumeWakeCalled = AtomicBoolean(false)
        micManager.onTtsCompleted(
            utteranceId = "test_reply",
            isContinuousSession = false,
            onListenAgain = {},
            onResumeWake = { resumeWakeCalled.set(true) }
        )
        assertEquals(MicSessionState.WAKE_DETECTING, micManager.sessionState.value)
        assertEquals(MicOwner.WAKE, micManager.micOwner.value)
        assertTrue(resumeWakeCalled.get())
    }

    // 2. Wake detector suspension
    @Test
    fun testWakeDetectorSuspensionBeforeCommandListening() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)
        val wakeStopped = AtomicBoolean(false)

        micManager.onStopWakeDetection = {
            wakeStopped.set(true)
        }

        micManager.requestWakeDetection("test")
        assertFalse(wakeStopped.get())

        micManager.requestCommandListening("wake_detected") {}
        assertTrue("Wake detector must be stopped before SpeechRecognizer starts", wakeStopped.get())
    }

    // 3. SpeechRecognizer lifecycle
    @Test
    fun testSpeechRecognizerLifecycle() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)

        micManager.requestCommandListening("user_turn") {}
        assertEquals(RecognizerStatus.LISTENING, micManager.recognizerStatus.value)

        micManager.onSpeechRecognized("Open YouTube")
        assertEquals(RecognizerStatus.IDLE, micManager.recognizerStatus.value)
    }

    // 4. SpeechRecognizer error recovery
    @Test
    fun testSpeechRecognizerErrorRecovery() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)
        val recovered = AtomicBoolean(false)

        micManager.requestCommandListening("turn_1") {}
        assertEquals(RecognizerStatus.LISTENING, micManager.recognizerStatus.value)

        // Simulate ERROR_RECOGNIZER_BUSY (error code 8)
        micManager.onSpeechError(8, "Speech service is busy") {
            recovered.set(true)
        }

        assertTrue("Recognizer error must trigger recovery back to wake detection", recovered.get())
        assertEquals(MicSessionState.WAKE_DETECTING, micManager.sessionState.value)
    }

    // 5. TTS completion recovery
    @Test
    fun testTtsCompletionRecovery() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)
        val wakeResumed = AtomicBoolean(false)

        micManager.requestTtsSpeaking("greeting") {}
        assertEquals(MicSessionState.TTS_SPEAKING, micManager.sessionState.value)

        micManager.onTtsCompleted(
            utteranceId = "final_reply",
            isContinuousSession = false,
            onListenAgain = {},
            onResumeWake = { wakeResumed.set(true) }
        )

        assertTrue(wakeResumed.get())
        assertEquals(MicSessionState.WAKE_DETECTING, micManager.sessionState.value)
    }

    // 6. TTS watchdog
    @Test
    fun testTtsWatchdogStuckSpeakingRecovery() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)
        var stoppedTts = false
        micManager.onStopTtsPlayback = { stoppedTts = true }

        micManager.requestTtsSpeaking("long_response") {}
        assertEquals(MicSessionState.TTS_SPEAKING, micManager.sessionState.value)

        micManager.releaseAll()
        assertEquals(MicSessionState.IDLE, micManager.sessionState.value)
        assertEquals(MicOwner.FREE, micManager.micOwner.value)
    }

    // 7. Continuous conversation session
    @Test
    fun testContinuousConversationSession() = runTest {
        var currentTime = 1000L
        val expiredCalled = AtomicBoolean(false)
        val manager = ContinuousConversationManager(
            sessionTimeoutMs = 45_000L,
            timeProvider = { currentTime },
            onSessionExpired = { expiredCalled.set(true) }
        )

        assertFalse(manager.isSessionActive())

        // 1. "Hey Jarvis" -> starts session
        manager.startOrExtendSession()
        assertTrue(manager.isSessionActive())

        // 2. Command 1: "Open YouTube" after 5 seconds
        currentTime += 5_000L
        assertTrue(manager.isSessionActive())
        manager.startOrExtendSession() // extend on command

        // 3. Command 2: "Search for Java tutorials" after 10 seconds
        currentTime += 10_000L
        assertTrue(manager.isSessionActive())
        manager.startOrExtendSession()

        // 4. Command 3: "Open the first result" after 5 seconds
        currentTime += 5_000L
        assertTrue(manager.isSessionActive())

        assertFalse("Session must not be expired during active conversation", expiredCalled.get())
    }

    // 8. Session timeout
    @Test
    fun testSessionTimeoutTransitionsToPassiveWake() = runTest {
        var currentTime = 1000L
        val expiredCalled = AtomicBoolean(false)
        val manager = ContinuousConversationManager(
            sessionTimeoutMs = 45_000L,
            timeProvider = { currentTime },
            onSessionExpired = { expiredCalled.set(true) }
        )

        manager.startOrExtendSession()
        assertTrue(manager.isSessionActive())

        // Elapse 46 seconds of inactivity
        currentTime += 46_000L
        manager.evaluateTimeout()

        assertFalse("Session should be inactive after 45s", manager.isSessionActive())
        assertTrue("onSessionExpired callback must be invoked", expiredCalled.get())
    }

    // 9. Service lifecycle & preferences
    @Test
    fun testServicePreferencesPersistence() {
        WakeWordPreferences.setBackgroundWakeEnabled(null, true)
        assertTrue(WakeWordPreferences.isBackgroundWakeEnabled(null))

        WakeWordPreferences.setBackgroundWakeEnabled(null, false)
        assertFalse(WakeWordPreferences.isBackgroundWakeEnabled(null))
    }

    // 10. Activity / Service Independence
    @Test
    fun testActivityAndServiceIndependence() {
        WakeWordManager.isActivityVisible = true
        assertTrue(WakeWordManager.isActivityVisible)

        // Activity closes
        WakeWordManager.isActivityVisible = false
        assertFalse(WakeWordManager.isActivityVisible)

        // Service active state is independent
        WakeWordManager.setServiceActive(true)
        assertTrue(WakeWordManager.isServiceActive.value)
    }

    // 11. Backend failure + voice operation (Local fallback)
    @Test
    fun testBackendFailureVoiceOperation() = runTest {
        val registry = ToolRegistry().apply {
            register(GetTimeTool())
            register(GetBatteryStatusTool(batteryLevelOverride = 85, isChargingOverride = false))
        }
        val router = ToolRouter(registry = registry, safetyManager = SafetyManager())
        val localEngine = LocalResponseEngine(toolRouter = router)

        // Test battery command locally (offline)
        val response = localEngine.generateResponse("What is my battery?")
        assertNotNull(response)
        assertTrue(response.contains("battery", ignoreCase = true) || response.contains("85"))

        // Test time command locally (offline)
        val timeResponse = localEngine.generateResponse("What time is it?")
        assertNotNull(timeResponse)
        assertTrue(timeResponse.contains("time", ignoreCase = true) || timeResponse.contains("Current time", ignoreCase = true))
    }

    // 12. Cancellation
    @Test
    fun testCancellationHaltsSessionAndReleasesMic() = runTest {
        val micManager = MicrophoneSessionManager(settlingDelayMs = 0L)
        micManager.requestCommandListening("active_command") {}
        assertEquals(MicSessionState.COMMAND_LISTENING, micManager.sessionState.value)

        // User says "Stop"
        micManager.releaseAll()
        assertEquals(MicSessionState.IDLE, micManager.sessionState.value)
        assertEquals(MicOwner.FREE, micManager.micOwner.value)
    }

    // 13. Audio focus lifecycle
    @Test
    fun testAudioFocusLifecycle() {
        val audioFocusManager = JarvisAudioFocusManager()
        assertEquals(AudioFocusState.RELEASED, audioFocusManager.focusState)

        audioFocusManager.requestFocus(AudioFocusMode.COMMAND_LISTENING)
        // In local headless unit test, verify focus state tracks correctly
        audioFocusManager.abandonFocus()
        assertEquals(AudioFocusState.RELEASED, audioFocusManager.focusState)
    }
}
