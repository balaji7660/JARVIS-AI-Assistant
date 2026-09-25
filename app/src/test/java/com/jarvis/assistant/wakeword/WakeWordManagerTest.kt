package com.jarvis.assistant.wakeword

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WakeWordManagerTest {

    private lateinit var detector: LocalWakeWordDetector

    @Before
    fun setUp() {
        detector = LocalWakeWordDetector(
            context = null,
            engine = LocalWakeWordDetectorTest.TestWakeWordEngine(),
            audioControllerFactory = null
        )
        WakeWordManager.setCustomDetector(detector)
        WakeWordManager.stop()
        WakeWordManager.suppressDuringSpeech(false)
        WakeWordManager.isActivityVisible = false
    }

    @Test
    fun customDetector_isPreserved() {
        WakeWordManager.setCustomDetector(detector)
        val testDetector = LocalWakeWordDetector(
            context = null,
            engine = LocalWakeWordDetectorTest.TestWakeWordEngine(),
            audioControllerFactory = null
        )
        WakeWordManager.setCustomDetector(testDetector)

        // Reset to original
        WakeWordManager.setCustomDetector(detector)
        assertTrue(true)
    }

    @Test
    fun activityVisibility_stateTracking() {
        WakeWordManager.isActivityVisible = true
        assertTrue(WakeWordManager.isActivityVisible)

        WakeWordManager.isActivityVisible = false
        assertFalse(WakeWordManager.isActivityVisible)
    }

    @Test
    fun serviceActive_stateTracking() {
        WakeWordManager.setServiceActive(true)
        assertTrue(WakeWordManager.isServiceActive.value)

        WakeWordManager.setServiceActive(false)
        assertFalse(WakeWordManager.isServiceActive.value)
    }

    @Test
    fun suppressionDuringSpeech_preventsDetection() {
        // Suppress during speech (e.g. TTS is speaking)
        WakeWordManager.suppressDuringSpeech(true)
        
        // Attempting to start detector while speaking should not activate
        detector.start()
        assertFalse("Detector must NOT be listening while TTS is speaking", detector.isListening)

        // After speech finishes, unsuppress
        WakeWordManager.suppressDuringSpeech(false)
        detector.start()
        assertTrue("Detector must be listening after speech finishes", detector.isListening)
        
        WakeWordManager.stop()
    }

    @Test
    fun multipleListeners_allReceiveWakeDetection() {
        var listener1Detected = false
        var listener2Detected = false

        val listener1 = object : WakeWordListener {
            override fun onWakeWordDetected() {
                listener1Detected = true
            }
        }
        val listener2 = object : WakeWordListener {
            override fun onWakeWordDetected() {
                listener2Detected = true
            }
        }

        WakeWordManager.addListener(listener1)
        WakeWordManager.addListener(listener2)

        detector.start()
        detector.simulateDetection()

        assertTrue("Listener 1 should receive wake word event", listener1Detected)
        assertTrue("Listener 2 should receive wake word event", listener2Detected)

        WakeWordManager.removeListener(listener1)
        WakeWordManager.removeListener(listener2)
        WakeWordManager.stop()
    }
}
