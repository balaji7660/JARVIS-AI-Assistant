package com.jarvis.assistant.tools

import android.util.Log

/**
 * ToolRouter validates incoming tool requests, verifies safety with SafetyManager,
 * and safely dispatches execution to registered JarvisTool handlers.
 */
class ToolRouter(
    private val registry: ToolRegistry,
    private val safetyManager: SafetyManager = SafetyManager()
) {

    fun getTool(name: String): JarvisTool? = registry.get(name)

    companion object {
        private const val TAG = "ToolRouter"
    }

    /**
     * Safely routes and dispatches a tool call.
     */
    suspend fun dispatch(toolCall: ToolCall, isUserConfirmed: Boolean = false): ToolResult {
        val toolName = toolCall.name.trim()

        // 1. Structured Logging: TOOL_REQUEST
        Log.i(TAG, "[TOOL_REQUEST] Received call for tool: '$toolName' (UserConfirmed: $isUserConfirmed)")

        // 2. Structured Logging & Step: TOOL_VALIDATION
        Log.i(TAG, "[TOOL_VALIDATION] Validating tool registration for: '$toolName'")
        val tool = registry.get(toolName)
        if (tool == null) {
            val failureMessage = "Tool '$toolName' is not registered or approved."
            Log.w(TAG, "[TOOL_VALIDATION] Failed: $failureMessage")
            Log.i(TAG, "[TOOL_RESULT] Success: false, Message: '$failureMessage'")
            return ToolResult(
                success = false,
                message = failureMessage
            )
        }

        // 3. Structured Logging & Step: SAFETY_CHECK
        Log.i(TAG, "[SAFETY_CHECK] Requesting safety evaluation for tool: '${tool.name}'")
        val decision = safetyManager.evaluate(tool, toolCall.arguments, isUserConfirmed)
        when (decision) {
            is SafetyDecision.Denied -> {
                val failureMessage = "Action blocked by safety policy: ${decision.reason}"
                Log.w(TAG, "[SAFETY_CHECK] Denied: $failureMessage")
                Log.i(TAG, "[TOOL_RESULT] Success: false, Message: '$failureMessage'")
                return ToolResult(
                    success = false,
                    message = failureMessage
                )
            }
            is SafetyDecision.RequiresConfirmation -> {
                val failureMessage = "Action requires user confirmation: ${decision.reason}"
                Log.w(TAG, "[SAFETY_CHECK] Requires confirmation: $failureMessage")
                Log.i(TAG, "[TOOL_RESULT] Success: false, Message: '$failureMessage'")
                return ToolResult(
                    success = false,
                    message = failureMessage,
                    data = mapOf("requiresConfirmation" to true, "riskLevel" to decision.riskLevel.name)
                )
            }
            is SafetyDecision.Allowed -> {
                Log.i(TAG, "[SAFETY_CHECK] Approved for execution (${decision.riskLevel})")
            }
        }

        // 4. Structured Logging & Step: TOOL_EXECUTION
        Log.i(TAG, "[TOOL_EXECUTION] Executing handler for '${tool.name}'")
        return try {
            val result = tool.execute(toolCall.arguments)
            // 5. Structured Logging & Step: TOOL_RESULT
            Log.i(TAG, "[TOOL_RESULT] Success: ${result.success}, Message: '${result.message}'")
            result
        } catch (e: Exception) {
            val errorMessage = "Tool '${tool.name}' encountered an error: ${e.localizedMessage ?: "Unknown error"}"
            Log.e(TAG, "[TOOL_EXECUTION] Exception executing '${tool.name}'", e)
            Log.i(TAG, "[TOOL_RESULT] Success: false, Message: '$errorMessage'")
            ToolResult(
                success = false,
                message = errorMessage
            )
        }
    }
}
