package com.jarvis.assistant

import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.ClickTextTool
import com.jarvis.assistant.tools.impl.ClickViewTool
import com.jarvis.assistant.tools.impl.ReadVisibleScreenTool
import com.jarvis.assistant.tools.impl.ScrollTool
import com.jarvis.assistant.tools.impl.TypeTextTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccessibilityToolsTest {

    private class TestAutomationProvider : AndroidAutomationProvider {
        var lastAction: AutomationAction? = null
        var shouldSucceed: Boolean = true
        var messageToReturn: String = "Success"

        override suspend fun execute(action: AutomationAction): ToolResult {
            lastAction = action
            return ToolResult(
                success = shouldSucceed,
                message = messageToReturn
            )
        }
    }

    private lateinit var provider: TestAutomationProvider

    @Before
    fun setUp() {
        provider = TestAutomationProvider()
    }

    @Test
    fun readVisibleScreenTool_callsAutomationProvider() = runBlocking {
        val tool = ReadVisibleScreenTool(provider)
        assertEquals("read_visible_screen", tool.name)
        assertEquals(RiskLevel.LOW, tool.riskLevel)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertEquals(AutomationAction.ReadVisibleScreen, provider.lastAction)
    }

    @Test
    fun clickTextTool_validText_executesClickTextAction() = runBlocking {
        val tool = ClickTextTool(provider)
        assertEquals("click_text", tool.name)
        assertEquals(RiskLevel.LOW, tool.riskLevel)

        val result = tool.execute(mapOf("text" to "Settings"))
        assertTrue(result.success)
        val action = provider.lastAction as? AutomationAction.ClickText
        assertEquals("Settings", action?.text)
    }

    @Test
    fun clickTextTool_emptyText_failsWithoutCallingProvider() = runBlocking {
        val tool = ClickTextTool(provider)
        val result = tool.execute(mapOf("text" to "   "))
        assertFalse(result.success)
        assertEquals("The 'text' argument is required and cannot be empty.", result.message)
        assertEquals(null, provider.lastAction)
    }

    @Test
    fun clickViewTool_validViewId_executesClickViewAction() = runBlocking {
        val tool = ClickViewTool(provider)
        assertEquals("click_view", tool.name)
        assertEquals(RiskLevel.LOW, tool.riskLevel)

        val result = tool.execute(mapOf("viewId" to "com.android.settings:id/search_box"))
        assertTrue(result.success)
        val action = provider.lastAction as? AutomationAction.ClickView
        assertEquals("com.android.settings:id/search_box", action?.viewId)
    }

    @Test
    fun clickViewTool_emptyViewId_failsWithoutCallingProvider() = runBlocking {
        val tool = ClickViewTool(provider)
        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertEquals("The 'viewId' argument is required and cannot be empty.", result.message)
        assertEquals(null, provider.lastAction)
    }

    @Test
    fun scrollTool_forwardAndBackwardDirections() = runBlocking {
        val tool = ScrollTool(provider)
        assertEquals("scroll", tool.name)
        assertEquals(RiskLevel.LOW, tool.riskLevel)

        // Forward
        tool.execute(mapOf("direction" to "forward"))
        assertEquals(AutomationAction.ScrollForward, provider.lastAction)

        // Backward
        tool.execute(mapOf("direction" to "backward"))
        assertEquals(AutomationAction.ScrollBackward, provider.lastAction)

        // Default to forward
        tool.execute(emptyMap())
        assertEquals(AutomationAction.ScrollForward, provider.lastAction)
    }

    @Test
    fun typeTextTool_isMediumRisk_executesTypeTextAction() = runBlocking {
        val tool = TypeTextTool(provider)
        assertEquals("type_text", tool.name)
        assertEquals(RiskLevel.MEDIUM, tool.riskLevel)

        val result = tool.execute(mapOf("text" to "Hello World"))
        assertTrue(result.success)
        val action = provider.lastAction as? AutomationAction.TypeText
        assertEquals("Hello World", action?.text)
    }

    @Test
    fun typeTextTool_missingText_failsWithoutCallingProvider() = runBlocking {
        val tool = TypeTextTool(provider)
        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertEquals("The 'text' argument is required.", result.message)
        assertEquals(null, provider.lastAction)
    }
}
