package com.jarvis.assistant.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordDetectorTest {

    @Test
    fun localDetector_startAndStopLifecycle() {
        val detector = LocalSimulatedWakeWordDetector(context = null)
        assertFalse(detector.isListening)

        detector.start()
        assertTrue(detector.isListening)

        detector.stop()
        assertFalse(detector.isListening)
    }

    @Test
    fun localDetector_notifiesListenerOnTrigger() {
        val detector = LocalSimulatedWakeWordDetector(context = null)
        var triggeredCount = 0

        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {
                triggeredCount++
            }

            override fun onWakeWordError(error: String) {}
        })

        detector.start()
        detector.simulateDetection()
        assertEquals(1, triggeredCount)

        // Stopping should prevent simulation detection
        detector.stop()
        detector.simulateDetection()
        assertEquals(1, triggeredCount)
    }

    @Test
    fun localDetector_notifiesError() {
        val detector = LocalSimulatedWakeWordDetector(context = null)
        var receivedError: String? = null

        detector.setListener(object : WakeWordListener {
            override fun onWakeWordDetected() {}
            override fun onWakeWordError(error: String) {
                receivedError = error
            }
        })

        detector.simulateError("Mic buffer overrun")
        assertEquals("Mic buffer overrun", receivedError)
    }
}
