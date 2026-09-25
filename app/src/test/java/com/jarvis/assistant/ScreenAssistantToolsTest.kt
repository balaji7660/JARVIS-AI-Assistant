package com.jarvis.assistant

import com.jarvis.assistant.automation.VisibleElement
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.ClickScreenElementTool
import com.jarvis.assistant.tools.impl.DiagnoseScreenErrorTool
import com.jarvis.assistant.tools.impl.FindScreenElementTool
import com.jarvis.assistant.tools.impl.ReadCurrentScreenTool
import com.jarvis.assistant.tools.impl.ScrollScreenTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAssistantToolsTest {

    private class FakeAutomationProvider(
        var summary: String = "[Button: Login]; [Text: Welcome back]; [Button: Search]",
        var elements: List<VisibleElement> = listOf(
            VisibleElement(text = "Login", clickable = true, viewId = "com.app:id/btn_login"),
            VisibleElement(text = "Welcome back", clickable = false),
            VisibleElement(text = "Search", clickable = true, viewId = "com.app:id/search_box")
        )
    ) : AndroidAutomationProvider {
        var lastAction: AutomationAction? = null

        override suspend fun execute(action: AutomationAction): ToolResult {
            lastAction = action
            return when (action) {
                is AutomationAction.ReadVisibleScreen -> ToolResult(
                    success = true,
                    message = summary,
                    data = mapOf("summary" to summary, "elements" to elements)
                )
                is AutomationAction.ClickText -> ToolResult(success = true, message = "Clicked ${action.text}")
                is AutomationAction.ClickView -> ToolResult(success = true, message = "Clicked view ${action.viewId}")
                is AutomationAction.ScrollForward -> ToolResult(success = true, message = "Scrolled forward")
                is AutomationAction.ScrollBackward -> ToolResult(success = true, message = "Scrolled backward")
                else -> ToolResult(success = true, message = "Executed")
            }
        }
    }

    @Test
    fun testFindScreenElement_found() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = FindScreenElementTool(fake)

        val result = tool.execute(mapOf("query" to "Login"))
        assertTrue(result.success)
        assertTrue(result.message.contains("Login"))
    }

    @Test
    fun testFindScreenElement_notFound() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = FindScreenElementTool(fake)

        val result = tool.execute(mapOf("query" to "NonExistentButton"))
        assertFalse(result.success)
    }

    @Test
    fun testReadCurrentScreen_returnsSummary() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = ReadCurrentScreenTool(fake)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("Login"))
    }

    @Test
    fun testDiagnoseScreenError_noError() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = DiagnoseScreenErrorTool(fake)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("didn't find any visible error"))
    }

    @Test
    fun testDiagnoseScreenError_detectsError() = runBlocking {
        val fake = FakeAutomationProvider(summary = "[Text: Network error: Failed to connect to server]; [Button: Retry]")
        val tool = DiagnoseScreenErrorTool(fake)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("connectivity issue") || result.message.contains("error"))
    }

    @Test
    fun testClickScreenElement_byText() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = ClickScreenElementTool(fake)

        val result = tool.execute(mapOf("text" to "Login"))
        assertTrue(result.success)
        assertTrue(fake.lastAction is AutomationAction.ClickText)
    }

    @Test
    fun testScrollScreen_forwardAndBackward() = runBlocking {
        val fake = FakeAutomationProvider()
        val tool = ScrollScreenTool(fake)

        val downRes = tool.execute(mapOf("direction" to "down"))
        assertTrue(downRes.success)
        assertTrue(fake.lastAction is AutomationAction.ScrollForward)

        val upRes = tool.execute(mapOf("direction" to "up"))
        assertTrue(upRes.success)
        assertTrue(fake.lastAction is AutomationAction.ScrollBackward)
    }
}
