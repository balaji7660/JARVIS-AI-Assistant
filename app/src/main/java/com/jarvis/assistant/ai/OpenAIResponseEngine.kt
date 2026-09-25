package com.jarvis.assistant.ai

import android.util.Log
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.data.remote.BackendHealthManager
import com.jarvis.assistant.data.remote.ChatApiService
import com.jarvis.assistant.data.remote.ChatRequest
import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
    private val fallbackEngine: ResponseEngine? = null,
    private val overallTimeoutMs: Long = DEFAULT_OVERALL_TIMEOUT_MS,
    private val maxRetries: Int = 0
) : ResponseEngine {

    private fun getApiService(): ChatApiService = chatApiService ?: ApiClient.getChatApiService()

    companion object {
        private const val TAG = "OpenAIResponseEngine"
        const val MAX_AUTOMATION_STEPS = 10
        const val DEFAULT_OVERALL_TIMEOUT_MS = 45_000L // 45-second hard overall automation timeout
        const val ERROR_TIMEOUT = "Automation timed out, boss."
        const val ERROR_BACKEND_UNAVAILABLE = "JARVIS backend is currently unavailable. Please try again."
        const val ERROR_NETWORK = ERROR_BACKEND_UNAVAILABLE
        const val ERROR_API_KEY = ERROR_BACKEND_UNAVAILABLE
        const val ERROR_UPSTREAM = "JARVIS backend is currently unavailable. Please try again."
    }

    override suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) {
            return@withContext fallbackEngine?.generateResponse(prompt)
                ?: "I didn't catch that, boss. How can I assist you?"
        }

        // Circuit-breaker check: If cloud is currently marked unavailable, check health first
        if (!BackendHealthManager.isCloudAvailable()) {
            val isRecovered = try {
                BackendHealthManager.checkHealth()
            } catch (_: Throwable) {
                false
            }
            if (!isRecovered) {
                return@withContext fallbackEngine?.generateResponse(prompt)
                    ?: "JARVIS cloud intelligence is temporarily unavailable. Local functions remain ready, boss."
            }
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

    private val selfSufficientTools = setOf(
        "open_app",
        "go_home",
        "press_back",
        "get_time",
        "get_date",
        "call_contact",
        "get_battery_status",
        "set_volume",
        "get_volume",
        "set_brightness",
        "get_brightness",
        "toggle_flashlight",
        "open_camera",
        "get_device_info",
        "get_network_status",
        "open_wifi_settings",
        "open_bluetooth_settings",
        "lock_screen",
        "play_media",
        "pause_media",
        "resume_media",
        "next_track",
        "previous_track",
        "get_media_state",
        "send_sms",
        "find_screen_element",
        "read_current_screen",
        "diagnose_screen_error",
        "click_screen_element",
        "scroll_screen",
        "search_web",
        "search_youtube",
        "read_current_webpage",
        "summarize_webpage",
        "create_note",
        "search_notes",
        "list_notes",
        "update_note",
        "delete_note",
        "create_timer",
        "cancel_timer",
        "list_timers",
        "create_reminder",
        "cancel_reminder",
        "list_reminders",
        "create_calendar_event",
        "list_calendar_events",
        "delete_calendar_event"
    )

    private fun isSafeToRetry(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val dangerousKeywords = listOf("send", "call", "delete", "remove", "pay", "buy", "type", "click", "post")
        return !dangerousKeywords.any { lower.contains(it) }
    }

    private suspend fun executeAutomationLoop(trimmedPrompt: String, originalPrompt: String): String {
        val t1 = System.currentTimeMillis()
        val canRetry = maxRetries > 0 && isSafeToRetry(trimmedPrompt)
        val maxAttempts = if (canRetry) (1 + maxRetries) else 1
        var attempt = 0
        var response: com.jarvis.assistant.data.remote.ChatResponse? = null

        return try {
            while (attempt < maxAttempts) {
                attempt++
                try {
                    response = getApiService().sendMessage(
                        ChatRequest(
                            message = trimmedPrompt,
                            sessionId = sessionId,
                            clientTimestamp = t1
                        )
                    )
                    BackendHealthManager.recordSuccess()
                    break
                } catch (e: Throwable) {
                    val failure = BackendHealthManager.classifyFailure(e)
                    if (attempt < maxAttempts && failure.isRetryable) {
                        BackendHealthManager.recordRetry()
                        val backoff = if (attempt == 1) 1000L else 2000L
                        Log.w(TAG, "Transient network issue ($failure) on attempt $attempt. Retrying in ${backoff}ms...")
                        delay(backoff)
                    } else {
                        throw e
                    }
                }
            }

            if (response == null) {
                return ERROR_BACKEND_UNAVAILABLE
            }

            val t4 = System.currentTimeMillis()
            val backendMs = response.timing?.get("backendProcessingMs")
            val puterMs = response.timing?.get("puterLatencyMs")
            Log.i(TAG, "[PipelineTiming] T1->T4 Roundtrip: ${t4 - t1}ms | Backend: ${backendMs}ms | Puter: ${puterMs}ms")

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

                val t6 = System.currentTimeMillis()
                val toolResult = if (toolRouter != null) {
                    toolRouter.dispatch(toolCall)
                } else {
                    Log.w(TAG, "No ToolRouter available to execute '$toolName'")
                    ToolResult(
                        success = false,
                        message = "Tool '$toolName' cannot be executed because ToolRouter is not available."
                    )
                }
                val t7 = System.currentTimeMillis()
                lastToolResult = toolResult
                onToolExecuting?.invoke(null)
                Log.i(TAG, "[PipelineTiming] Tool '$toolName' execution (T6->T7): ${t7 - t6}ms | Success: ${toolResult.success}")

                // If tool requires user confirmation, halt loop and return notice
                val requiresConfirmation = toolResult.data["requiresConfirmation"] as? Boolean ?: false
                if (requiresConfirmation) {
                    return toolResult.message
                }

                // Phase 3 Optimization: If the tool result is self-sufficient (marked with directResponse)
                val isDirectResponse = toolResult.data["directResponse"] as? Boolean ?: false
                if (isDirectResponse && toolResult.message.isNotBlank()) {
                    Log.i(TAG, "Fast-path: direct local tool '$toolName' response returned without redundant AI follow-up.")
                    return toolResult.message
                }

                // Send tool execution outcome back to backend for tools requiring natural language synthesis
                val t7Followup = System.currentTimeMillis()
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
                            toolName = toolName,
                            clientTimestamp = t7Followup
                        )
                    )
                    BackendHealthManager.recordSuccess()
                    val t8 = System.currentTimeMillis()
                    Log.i(TAG, "[PipelineTiming] Follow-up turn roundtrip (T7->T8): ${t8 - t7Followup}ms")
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
            BackendHealthManager.recordFailure(e)
            Log.w(TAG, "HTTP error communicating with backend: ${e.code()}", e)
            when (e.code()) {
                503 -> ERROR_BACKEND_UNAVAILABLE
                400 -> "I didn't catch that, boss."
                else -> ERROR_UPSTREAM
            }
        } catch (e: SocketTimeoutException) {
            onToolExecuting?.invoke(null)
            BackendHealthManager.recordFailure(e)
            Log.w(TAG, "Socket timeout communicating with backend", e)
            ERROR_NETWORK
        } catch (e: IOException) {
            onToolExecuting?.invoke(null)
            BackendHealthManager.recordFailure(e)
            Log.w(TAG, "Network I/O error communicating with backend", e)
            ERROR_NETWORK
        } catch (e: Exception) {
            onToolExecuting?.invoke(null)
            BackendHealthManager.recordFailure(e)
            Log.e(TAG, "Unexpected error in OpenAIResponseEngine", e)
            ERROR_UPSTREAM
        }
    }
}
