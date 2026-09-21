package com.jarvis.assistant

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRouterTest {

    private lateinit var registry: ToolRegistry
    private lateinit var router: ToolRouter

    private val sampleTool = object : JarvisTool {
        override val name: String = "sample_tool"
        override val description: String = "A sample safe tool"
        override val riskLevel: RiskLevel = RiskLevel.LOW

        override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
            val echo = arguments["echo"]?.toString() ?: "default"
            return ToolResult(success = true, message = "Executed: $echo")
        }
    }

    private val failingTool = object : JarvisTool {
        override val name: String = "failing_tool"
        override val description: String = "A tool that throws an unexpected error"
        override val riskLevel: RiskLevel = RiskLevel.LOW

        override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
            throw RuntimeException("Simulated unexpected failure")
        }
    }

    @Before
    fun setUp() {
        registry = ToolRegistry()
        registry.register(sampleTool)
        registry.register(failingTool)
        router = ToolRouter(registry, SafetyManager())
    }

    @Test
    fun dispatchValidTool_returnsSuccessToolResult() = runTest {
        val call = ToolCall("sample_tool", mapOf("echo" to "hello_world"))
        val result = router.dispatch(call)

        assertTrue(result.success)
        assertEquals("Executed: hello_world", result.message)
    }

    @Test
    fun dispatchUnknownTool_returnsFailureWithAccurateExplanation() = runTest {
        val call = ToolCall("unregistered_tool", emptyMap())
        val result = router.dispatch(call)

        assertFalse(result.success)
        assertTrue(result.message.contains("not registered or approved"))
    }

    @Test
    fun dispatchToolWithDangerousSyntax_blockedBySafetyManager() = runTest {
        val call = ToolCall("sample_tool", mapOf("echo" to "rm -rf /"))
        val result = router.dispatch(call)

        assertFalse(result.success)
        assertTrue(result.message.contains("safety policy"))
    }

    @Test
    fun dispatchToolWithException_returnsControlledFailureResult() = runTest {
        val call = ToolCall("failing_tool", emptyMap())
        val result = router.dispatch(call)

        assertFalse(result.success)
        assertTrue(result.message.contains("Simulated unexpected failure"))
    }
}
