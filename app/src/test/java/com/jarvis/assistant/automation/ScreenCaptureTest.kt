package com.jarvis.assistant.automation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureTest {

    @Test
    fun simulatedCaptureProvider_returnsValidScreenCapture() = runTest {
        val provider = SimulatedScreenCaptureProvider(width = 1080, height = 2400)
        val result = provider.captureCurrentScreen()

        assertTrue(result.isSuccess)
        val capture = result.getOrThrow()
        assertEquals(1080, capture.width)
        assertEquals(2400, capture.height)
        assertEquals("com.jarvis.assistant", capture.packageName)
    }

    @Test
    fun simulatedCaptureProvider_handlesSimulatedFailure() = runTest {
        val provider = SimulatedScreenCaptureProvider(simulatedFailure = true)
        val result = provider.captureCurrentScreen()

        assertTrue(result.isFailure)
        assertEquals("Simulated capture failure.", result.exceptionOrNull()?.message)
    }

    @Test
    fun screenUnderstandingContext_maintainsBoundedMetadata() {
        val elements = (1..50).map {
            VisibleElement(text = "Item $it", clickable = it % 2 == 0)
        }
        val context = ScreenUnderstandingContext(
            currentPackage = "com.android.settings",
            elements = elements,
            screenWidth = 1080,
            screenHeight = 2400
        )

        assertEquals("com.android.settings", context.currentPackage)
        assertEquals(50, context.elements.size)
        assertEquals(1080, context.screenWidth)
        assertEquals(2400, context.screenHeight)
    }
}
