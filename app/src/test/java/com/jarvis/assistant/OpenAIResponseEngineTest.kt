package com.jarvis.assistant

import com.jarvis.assistant.ai.OpenAIResponseEngine
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAIResponseEngineTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var engine: OpenAIResponseEngine
    private lateinit var toolRouter: ToolRouter
    private var executedToolName: String? = null

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/").toString()
        val apiService = ApiClient.createChatApiService(baseUrl)

        val registry = ToolRegistry().apply {
            register(object : JarvisTool {
                override val name: String = "get_time"
                override val description: String = "Returns time"
                override val riskLevel: RiskLevel = RiskLevel.LOW
                override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                    return ToolResult(success = true, message = "5:30 PM", data = mapOf("time" to "5:30 PM"))
                }
            })
        }
        toolRouter = ToolRouter(registry, SafetyManager())

        engine = OpenAIResponseEngine(
            chatApiService = apiService,
            sessionId = "test-session",
            toolRouter = toolRouter,
            onToolExecuting = { executedToolName = it },
            fallbackEngine = null
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun successfulChatResponse_returnsModelReply() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"reply":"Good afternoon, boss. All systems online.","sessionId":"test-session"}""")
        )

        val response = engine.generateResponse("Status report")
        assertEquals("Good afternoon, boss. All systems online.", response)

        val recordedRequest = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("/api/chat", recordedRequest?.path)
        assertTrue(recordedRequest?.body?.readUtf8()?.contains("Status report") == true)
    }

    @Test
    fun toolCallResponse_executesToolAndCompletesFollowUpCycle() = runTest {
        // Turn 1: AI sends toolCall
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"toolCall":{"id":"call_01","name":"get_time","arguments":{}},"sessionId":"test-session"}""")
        )

        // Turn 2: Follow-up reply with tool execution context
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"reply":"The time is currently 5:30 PM, boss.","sessionId":"test-session"}""")
        )

        val response = engine.generateResponse("What time is it?")
        assertEquals("The time is currently 5:30 PM, boss.", response)

        // Verify requests
        val request1 = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        assertTrue(request1?.body?.readUtf8()?.contains("What time is it?") == true)

        val request2 = mockWebServer.takeRequest(2, TimeUnit.SECONDS)
        val body2 = request2?.body?.readUtf8() ?: ""
        assertTrue(body2.contains("toolResult"))
        assertTrue(body2.contains("5:30 PM"))
    }

    @Test
    fun backendServiceUnavailable503_returnsErrorNotice() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"AI backend is currently unavailable."}""")
        )

        val response = engine.generateResponse("Hello")
        assertEquals(OpenAIResponseEngine.ERROR_BACKEND_UNAVAILABLE, response)
    }

    @Test
    fun backendInternalError500_returnsUpstreamErrorMessage() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"OpenAI upstream timeout"}""")
        )

        val response = engine.generateResponse("Hello")
        assertEquals(OpenAIResponseEngine.ERROR_UPSTREAM, response)
    }

    @Test
    fun emptyOrWhitespacePrompt_returnsDefaultNoticeWithoutNetwork() = runTest {
        val response = engine.generateResponse("   ")
        assertEquals("I didn't catch that, boss. How can I assist you?", response)
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun networkFailure_returnsNetworkErrorMessage() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val response = engine.generateResponse("Hello")
        assertEquals(OpenAIResponseEngine.ERROR_NETWORK, response)
    }

    @Test
    fun overallTimeout_triggersWhenExecutionExceedsConfiguredLimit() = runTest {
        val baseUrl = mockWebServer.url("/").toString()
        val slowApiService = ApiClient.createChatApiService(baseUrl)

        val timeoutEngine = OpenAIResponseEngine(
            chatApiService = slowApiService,
            sessionId = "timeout-session",
            toolRouter = toolRouter,
            onToolExecuting = { executedToolName = it },
            fallbackEngine = null,
            overallTimeoutMs = 150L
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBodyDelay(800, TimeUnit.MILLISECONDS)
                .setBody("""{"reply":"Too late"}""")
        )

        val response = timeoutEngine.generateResponse("Status report")
        assertEquals(OpenAIResponseEngine.ERROR_TIMEOUT, response)
        assertEquals(null, executedToolName)
    }
}
