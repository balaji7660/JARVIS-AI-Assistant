package com.jarvis.assistant.tools.automation

import com.jarvis.assistant.tools.ToolResult

/**
 * Architectural interface for UI automation providers.
 * Decoupled from the Android framework to allow testing and pluggable execution backends.
 */
interface AndroidAutomationProvider {
    suspend fun execute(action: AutomationAction): ToolResult

    suspend fun getCurrentPackage(): String {
        val result = execute(AutomationAction.ReadVisibleScreen)
        return result.data["packageName"] as? String ?: ""
    }

    suspend fun getScreenSummary(): String {
        val result = execute(AutomationAction.ReadVisibleScreen)
        return result.data["summary"] as? String ?: result.message
    }
}
