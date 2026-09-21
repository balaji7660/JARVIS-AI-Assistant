package com.jarvis.assistant.ai

import android.util.Log
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.data.remote.ChatApiService
import com.jarvis.assistant.data.remote.ChatRequest
import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * OpenAI-backed Response Engine supporting structured Tool Calling and multi-step automation.
 *
 * Flow:
 * 1. Sends user voice prompt to backend /api/chat.
 * 2. Enforces a hard overall timeout of 45 seconds (configurable between 30–60 seconds).
 * 3. While model returns a structured toolCall (up to MAX_STEPS = 10):
 *    - Notifies UI of active tool execution.
 *    - Dispatches call through ToolRouter -> SafetyManager -> Tool Handler.
 *    - Sends ToolResult back to backend for next action or finalized natural language response.
 *    - If follow-up fails, safely returns verified tool result message.
 * 4. Returns finalized conversational reply.
 */
class OpenAIResponseEngine(
    private val chatApiService: ChatApiService? = null,
    private val sessionId: String = "default",
    private val toolRouter: ToolRouter? = null,
    private val onToolExecuting: ((String?) -> Unit)? = null,
    private val fallbackEngine: ResponseEngine? = LocalResponseEngine(),
    private val overallTimeoutMs: Long = DEFAULT_OVERALL_TIMEOUT_MS
) : ResponseEngine {

    private fun getApiService(): ChatApiService = chatApiService ?: ApiClient.getChatApiService()

    companion object {
        private const val TAG = "OpenAIResponseEngine"
        const val MAX_AUTOMATION_STEPS = 10
        const val DEFAULT_OVERALL_TIMEOUT_MS = 45_000L // 45-second hard overall automation timeout
        const val ERROR_TIMEOUT = "Automation timed out, boss."
        const val ERROR_NETWORK = "Boss, I'm having trouble connecting to my AI service right now."
        const val ERROR_API_KEY = "Boss, the OpenAI API key is not configured on the server."
        const val ERROR_UPSTREAM = "Boss, I encountered an issue while processing your request with OpenAI."
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return@withContext fallbackEngine?.generateResponse(prompt)
                ?: "I didn't catch that, boss. How can I assist you?"
        }

        try {
            val outcome = withTimeoutOrNull(overallTimeoutMs) {
                executeAutomationLoop(trimmed, prompt)
            }

            if (outcome == null) {
                onToolExecuting?.invoke(null)
                Log.w(TAG, "Overall automation loop timed out after ${overallTimeoutMs}ms")
                ERROR_TIMEOUT
            } else {
                outcome
            }
        } catch (e: TimeoutCancellationException) {
            onToolExecuting?.invoke(null)
            Log.w(TAG, "Overall automation loop timed out after ${overallTimeoutMs}ms", e)
            ERROR_TIMEOUT
        }
    }

    private suspend fun executeAutomationLoop(trimmedPrompt: String, originalPrompt: String): String {
        return try {
            var response = getApiService().sendMessage(
                ChatRequest(
                    message = trimmedPrompt,
                    sessionId = sessionId
                )
            )

            var stepsExecuted = 0
            var activeTool = response.toolCall
            var lastToolResult: ToolResult? = null

            // Bounded automation loop (max 10 steps per user prompt)
            while (activeTool != null && activeTool.name.isNotBlank()) {
                stepsExecuted++
                if (stepsExecuted > MAX_AUTOMATION_STEPS) {
                    Log.w(TAG, "Automation step limit reached ($MAX_AUTOMATION_STEPS)")
                    return "Automation step limit reached, boss."
                }

                val toolName = activeTool.name
                val toolCall = ToolCall(
                    name = toolName,
                    arguments = activeTool.arguments,
                    callId = activeTool.id
                )

                onToolExecuting?.invoke(toolName)

                val toolResult = if (toolRouter != null) {
                    toolRouter.dispatch(toolCall)
                } else {
                    Log.w(TAG, "No ToolRouter available to execute '$toolName'")
                    ToolResult(
                        success = false,
                        message = "Tool '$toolName' cannot be executed because ToolRouter is not available."
                    )
                }
                lastToolResult = toolResult
                onToolExecuting?.invoke(null)

                // If tool requires user confirmation, halt loop and return notice
                val requiresConfirmation = toolResult.data["requiresConfirmation"] as? Boolean ?: false
                if (requiresConfirmation) {
                    return toolResult.message
                }

                // Send tool execution outcome back to backend
                try {
                    response = getApiService().sendMessage(
                        ChatRequest(
                            sessionId = sessionId,
                            toolResult = mapOf(
                                "success" to toolResult.success,
                                "message" to toolResult.message,
                                "data" to toolResult.data
                            ),
                            toolCallId = activeTool.id,
                            toolName = toolName
                        )
                    )
                    activeTool = response.toolCall
                } catch (e: Exception) {
                    Log.w(TAG, "Follow-up turn failed; returning verified tool outcome", e)
                    return toolResult.message
                }
            }

            if (!response.reply.isNullOrBlank()) {
                response.reply.trim()
            } else {
                lastToolResult?.message ?: fallbackEngine?.generateResponse(originalPrompt) ?: ERROR_UPSTREAM
            }
        } catch (e: HttpException) {
            onToolExecuting?.invoke(null)
            Log.w(TAG, "HTTP error communicating with backend: ${e.code()}", e)
            when (e.code()) {
                503 -> ERROR_API_KEY
                400 -> fallbackEngine?.generateResponse(originalPrompt) ?: "I didn't catch that, boss."
                else -> fallbackEngine?.generateResponse(originalPrompt) ?: ERROR_UPSTREAM
            }
        } catch (e: SocketTimeoutException) {
            onToolExecuting?.invoke(null)
            Log.w(TAG, "Socket timeout communicating with backend", e)
            ERROR_NETWORK
        } catch (e: IOException) {
            onToolExecuting?.invoke(null)
            Log.w(TAG, "Network I/O error communicating with backend", e)
            fallbackEngine?.generateResponse(originalPrompt) ?: ERROR_NETWORK
        } catch (e: Exception) {
            onToolExecuting?.invoke(null)
            Log.e(TAG, "Unexpected error in OpenAIResponseEngine", e)
            fallbackEngine?.generateResponse(originalPrompt) ?: ERROR_UPSTREAM
        }
    }
}
