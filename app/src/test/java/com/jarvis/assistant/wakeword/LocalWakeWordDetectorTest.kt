package com.jarvis.assistant.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalWakeWordDetectorTest {

    private lateinit var mockEngine: TestWakeWordEngine
    private lateinit var detector: LocalWakeWordDetector

    class TestWakeWordEngine(
        var simulatedConfidence: Float = 0.90f,
        var shouldDetect: Boolean = false,
        var initializeResult: Boolean = true
    ) : WakeWordEngine {
        override val name: String = "TestEngine"
        override var isInitialized: Boolean = false
        var resetCount: Int = 0
        var releaseCount: Int = 0
        var processedFramesCount: Int = 0

        override fun initialize(): Boolean {
            isInitialized = initializeResult
            return initializeResult
        }

        override fun processFrame(audioBuffer: ShortArray, length: Int): WakeWordResult {
            processedFramesCount++
            return WakeWordResult(
                isDetected = shouldDetect,
                confidence = if (shouldDetect) simulatedConfidence else 0.1f,
                phrase = WakeWordConfig.WAKE_PHRASE
            )
        }

        override fun reset() {
            resetCount++
        }

        override fun release() {
            releaseCount++
            isInitialized = false
        }
    }

    @Before
    fun setUp() {
        mockEngine = TestWakeWordEngine()
        detector = LocalWakeWordDetector(
            context = null,
            engine = mockEngine,
            audioControllerFactory = null
        )
    }

    @Test
    fun initialState_isDisabled() {
        assertEquals(WakeWordState.DISABLED, detector.state.value)
        assertFalse(detector.isListening)
    }

    @Test
    fun startAndStop_updatesStateCorrectly() {
        assertFalse(detector.isListening)

        detector.start()
        assertEquals(WakeWordState.LISTENING, detector.state.value)
        assertTrue(detector.isListening)

        detector.stop()
        assertEquals(WakeWordState.DISABLED, detector.state.value)
        assertFalse(detector.isListening)
        assertTrue(mockEngine.resetCount > 0)
    }

    @Test
    fun detection_transitionsStateAndNotifiesListener() {
        var detectedCount = 0
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                detectedCount++
            }
        })

        detector.start()
        assertEquals(WakeWordState.LISTENING, detector.state.value)

        // Trigger simulation
        detector.simulateDetection()
        assertEquals(1, detectedCount)
        assertEquals(WakeWordState.TRANSITIONING, detector.state.value)
    }

    @Test
    fun cooldown_preventsDuplicateTriggers() {
        var detectedCount = 0
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                detectedCount++
            }
        })

        detector.start()
        detector.simulateDetection()
        assertEquals(1, detectedCount)

        // Immediate subsequent trigger within WAKE_WORD_COOLDOWN_MS must be ignored
        detector.simulateDetection()
        assertEquals(1, detectedCount)
    }

    @Test
    fun audioFrameProcessing_triggersWhenEngineDetects() {
        var detectedCount = 0
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                detectedCount++
            }
        })

        detector.start()
        val buffer = ShortArray(WakeWordConfig.AUDIO_FRAME_SIZE) { 1000 }

        // Engine returns no detection
        mockEngine.shouldDetect = false
        detector.processAudioFrame(buffer, buffer.size)
        assertEquals(0, detectedCount)
        assertEquals(WakeWordState.LISTENING, detector.state.value)

        // Engine returns detection
        mockEngine.shouldDetect = true
        detector.processAudioFrame(buffer, buffer.size)
        assertEquals(1, detectedCount)
        assertEquals(WakeWordState.TRANSITIONING, detector.state.value)
    }

    @Test
    fun notifyCommandListening_stopsWakeDetectionAndUpdatesState() {
        detector.start()
        assertTrue(detector.isListening)

        detector.notifyCommandListeningStarted()
        assertEquals(WakeWordState.COMMAND_LISTENING, detector.state.value)
        assertFalse(detector.isListening)

        // Frame processing ignored during command listening
        mockEngine.shouldDetect = true
        var detected = false
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                detected = true
            }
        })
        val buffer = ShortArray(WakeWordConfig.AUDIO_FRAME_SIZE) { 1000 }
        detector.processAudioFrame(buffer, buffer.size)
        assertFalse(detected)

        // Finishing command listening restores listening state
        detector.notifyCommandFinished()
        assertEquals(WakeWordState.LISTENING, detector.state.value)
        assertTrue(detector.isListening)
    }

    @Test
    fun simulateError_transitionsToErrorStateAndNotifiesListener() {
        var errorMsg: String? = null
        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {}
            override fun onWakeWordError(error: String) {
                errorMsg = error
            }
        })

        detector.start()
        detector.simulateError("Mic buffer overrun")

        assertEquals(WakeWordState.ERROR, detector.state.value)
        assertEquals("Mic buffer overrun", errorMsg)
        assertFalse(detector.isListening)
    }

    @Test
    fun release_cleansUpAllResources() {
        detector.start()
        detector.release()

        assertEquals(WakeWordState.DISABLED, detector.state.value)
        assertFalse(detector.isListening)
        assertTrue(mockEngine.releaseCount > 0)
    }

    @Test
    fun localAcousticEngine_dspFeatureExtraction() {
        val realEngine = LocalAcousticWakeWordEngine(context = null)
        assertTrue(realEngine.isInitialized)
        assertEquals("LocalAcousticWakeWordEngine (Hey JARVIS)", realEngine.name)

        // Silent frame should return false confidence due to VAD gate
        val silentBuffer = ShortArray(WakeWordConfig.AUDIO_FRAME_SIZE) { 0 }
        val resultSilence = realEngine.processFrame(silentBuffer, silentBuffer.size)
        assertFalse(resultSilence.isDetected)
        assertEquals(0f, resultSilence.confidence)

        // Audio frame with non-zero signal processes successfully
        val signalBuffer = ShortArray(WakeWordConfig.AUDIO_FRAME_SIZE) { (it % 1000).toShort() }
        val resultSignal = realEngine.processFrame(signalBuffer, signalBuffer.size)
        assertNotNull(resultSignal)
        assertFalse(resultSignal.isDetected) // Single arbitrary frame will not match complete sequence

        realEngine.reset()
        realEngine.release()
        assertFalse(realEngine.isInitialized)
    }

    @Test
    fun wakeWordConfig_constantsAreValid() {
        assertEquals("Hey JARVIS", WakeWordConfig.WAKE_PHRASE)
        assertEquals(16000, WakeWordConfig.AUDIO_SAMPLE_RATE)
        assertEquals(512, WakeWordConfig.AUDIO_FRAME_SIZE)
        assertTrue(WakeWordConfig.WAKE_WORD_THRESHOLD > 0.5f)
        assertTrue(WakeWordConfig.WAKE_WORD_COOLDOWN_MS >= 2000L)
    }
}
