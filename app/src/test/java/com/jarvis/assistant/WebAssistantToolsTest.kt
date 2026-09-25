package com.jarvis.assistant

import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.impl.ReadCurrentWebpageTool
import com.jarvis.assistant.tools.impl.SummarizeWebpageTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAssistantToolsTest {

    private class FakeWebAutomationProvider(
        var pageSummary: String = "[Button: Back]; [Button: Search]; [Text: Kotlin 2.0 Released with New K2 Compiler]; [Text: The JetBrains team announces major performance improvements]; [Button: Share]"
    ) : AndroidAutomationProvider {
        override suspend fun execute(action: AutomationAction): ToolResult {
            return when (action) {
                is AutomationAction.ReadVisibleScreen -> ToolResult(
                    success = true,
                    message = pageSummary,
                    data = mapOf("summary" to pageSummary)
                )
                else -> ToolResult(success = true, message = "Executed")
            }
        }
    }

    @Test
    fun testReadCurrentWebpage_filtersNavNoiseAndExtractsMainText() = runBlocking {
        val fake = FakeWebAutomationProvider()
        val tool = ReadCurrentWebpageTool(fake)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("Kotlin 2.0 Released"))
        assertTrue(result.message.contains("K2 Compiler"))
        // Navigation buttons like "Back", "Share", "Search" should be omitted or filtered
        assertFalse(result.message.contains("[Button: Back]"))
    }

    @Test
    fun testSummarizeWebpage_delegatesToWebpageReader() = runBlocking {
        val fake = FakeWebAutomationProvider()
        val readTool = ReadCurrentWebpageTool(fake)
        val summarizeTool = SummarizeWebpageTool(readTool)

        val result = summarizeTool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("Kotlin 2.0"))
    }
}
