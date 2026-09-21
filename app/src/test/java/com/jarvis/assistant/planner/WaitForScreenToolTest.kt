package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.WaitForScreenTool
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitForScreenToolTest {

    private class MockAutomationProvider(
        var currentPkg: String = "com.android.launcher",
        var summary: String = "Home Screen"
    ) : AndroidAutomationProvider {
        override suspend fun execute(action: AutomationAction): ToolResult = ToolResult(
            success = true,
            message = summary,
            data = mapOf("packageName" to currentPkg, "summary" to summary)
        )
    }

    @Test
    fun waitForScreen_matchingPackage_succeedsImmediately() = runBlocking {
        val provider = MockAutomationProvider(currentPkg = "com.google.android.youtube")
        val tool = WaitForScreenTool(provider)

        val result = tool.execute(mapOf("expectedPackage" to "com.google.android.youtube", "timeoutMs" to 1000L))
        assertTrue(result.success)
        assertTrue(result.message.contains("Screen matched"))
    }

    @Test
    fun waitForScreen_timeout_failsSafely() = runBlocking {
        val provider = MockAutomationProvider(currentPkg = "com.android.settings")
        val tool = WaitForScreenTool(provider)

        val result = tool.execute(mapOf("expectedPackage" to "com.google.android.youtube", "timeoutMs" to 500L))
        assertFalse(result.success)
        assertTrue(result.message.contains("Timed out"))
    }

    @Test
    fun waitForScreen_missingArguments_returnsError() = runBlocking {
        val provider = MockAutomationProvider()
        val tool = WaitForScreenTool(provider)

        val result = tool.execute(emptyMap())
        assertFalse(result.success)
    }
}
