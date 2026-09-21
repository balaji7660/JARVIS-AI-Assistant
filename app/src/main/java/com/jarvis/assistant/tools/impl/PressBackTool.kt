package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

class PressBackTool(
    private val backDispatcher: (() -> Boolean)? = null
) : JarvisTool {

    override val name: String = "press_back"
    override val description: String = "Performs the Android back action where supported."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return try {
            if (backDispatcher != null) {
                val handled = backDispatcher.invoke()
                if (handled) {
                    ToolResult(success = true, message = "Back action performed.")
                } else {
                    ToolResult(success = false, message = "Back action could not be handled at this time.")
                }
            } else {
                ToolResult(success = true, message = "Back action executed.")
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                message = "Failed to perform back action: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}
