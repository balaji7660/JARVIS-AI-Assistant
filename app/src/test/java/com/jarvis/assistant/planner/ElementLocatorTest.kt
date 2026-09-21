package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementLocatorTest {

    private class MockAutomationProvider(
        private val summary: String = "Test screen"
    ) : AndroidAutomationProvider {
        override suspend fun execute(action: AutomationAction): ToolResult = ToolResult(
            success = true,
            message = summary,
            data = mapOf("summary" to summary, "packageName" to "com.test.app")
        )
    }

    @Test
    fun locate_singleExactTextMatch_returnsFound() = runBlocking {
        val provider = MockAutomationProvider(summary = "[Button: Settings]; [Text: Battery]; [Button: Wi-Fi]")
        val locator = ElementLocator(provider)
        val result = locator.locate("Settings")

        assertTrue(result is LocateResult.Found)
        assertTrue((result as LocateResult.Found).description.contains("Settings"))
    }

    @Test
    fun locate_multipleExactTextMatches_returnsAmbiguous() = runBlocking {
        val provider = MockAutomationProvider(summary = "[Button: Settings]; [Text: Battery]; [Button: Settings]")
        val locator = ElementLocator(provider)
        val result = locator.locate("Settings")

        assertTrue(result is LocateResult.Ambiguous)
        val ambiguous = result as LocateResult.Ambiguous
        assertEquals(2, ambiguous.candidateCount)
        assertEquals(2, ambiguous.candidateDescriptions.size)
    }

    @Test
    fun locate_noMatchingElement_returnsNotFound() = runBlocking {
        val provider = MockAutomationProvider(summary = "[Button: Wi-Fi]; [Text: Bluetooth]")
        val locator = ElementLocator(provider)
        val result = locator.locate("NonExistentButton")

        assertTrue(result is LocateResult.NotFound)
    }
}
