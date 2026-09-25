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

    suspend fun waitForTargetWindow(expectedPackage: String, timeoutMs: Long = 7000L): Boolean {
        val startTime = System.currentTimeMillis()
        val initialDelayMs = 300L
        val pollIntervalMs = 200L
        kotlinx.coroutines.delay(initialDelayMs)

        val target = expectedPackage.trim().lowercase()
        if (target.isBlank()) return true

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val current = getCurrentPackage().lowercase()
            if (current.isNotEmpty() && (current.contains(target) || target.contains(current))) {
                return true
            }
            kotlinx.coroutines.delay(pollIntervalMs)
        }
        return false
    }
}
