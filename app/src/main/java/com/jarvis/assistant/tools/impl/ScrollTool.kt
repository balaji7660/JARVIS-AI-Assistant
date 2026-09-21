package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction

class ScrollTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "scroll"
    override val description: String = "Scrolls the currently visible scrollable container forward or backward."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val direction = arguments["direction"]?.toString()?.trim()?.lowercase() ?: "forward"
        val action = if (direction == "backward") {
            AutomationAction.ScrollBackward
        } else {
            AutomationAction.ScrollForward
        }

        return automationProvider.execute(action)
    }
}
