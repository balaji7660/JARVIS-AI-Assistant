package com.jarvis.assistant.automation

import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.WaitForScreenTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSynchronizationTest {

    private class PollingMockAutomationProvider(
        private val packageSequence: List<String>
    ) : AndroidAutomationProvider {
        private var callCount = 0

        override suspend fun execute(action: AutomationAction): ToolResult {
            val pkg = if (callCount < packageSequence.size) packageSequence[callCount++] else packageSequence.last()
            return ToolResult(
                success = true,
                message = "Current package: $pkg",
                data = mapOf("packageName" to pkg, "summary" to "Window of $pkg")
            )
        }
    }

    @Test
    fun waitForTargetWindow_transientNullAndDelayedPackage_synchronizesSuccessfully() = runTest {
        // Simulates app launch: initially launcher, then empty/transient null, then Chrome attaches
        val provider = PollingMockAutomationProvider(
            listOf("com.android.launcher", "", "com.android.chrome")
        )

        val matched = provider.waitForTargetWindow("com.android.chrome", timeoutMs = 3000L)
        assertTrue("Must synchronize with target window after transient delay", matched)
    }

    @Test
    fun waitForTargetWindow_differentPackage_timesOutSafely() = runTest {
        val provider = PollingMockAutomationProvider(
            listOf("com.android.launcher", "com.google.android.youtube")
        )

        val matched = provider.waitForTargetWindow("com.android.chrome", timeoutMs = 800L)
        assertFalse("Must time out if target package never appears", matched)
    }

    @Test
    fun waitForScreenTool_chromePackage_succeedsThroughDelegation() = runTest {
        val provider = PollingMockAutomationProvider(
            listOf("", "com.android.chrome")
        )
        val tool = WaitForScreenTool(provider)

        val result = tool.execute(mapOf("expectedPackage" to "com.android.chrome", "timeoutMs" to 2000L))
        assertTrue(result.success)
        assertTrue(result.message.contains("Screen matched"))
    }
}
