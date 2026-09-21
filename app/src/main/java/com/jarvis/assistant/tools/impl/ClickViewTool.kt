package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction

class ClickViewTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "click_view"
    override val description: String = "Clicks a visible UI element matching the exact Android view ID."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val viewId = arguments["viewId"]?.toString()?.trim()
        if (viewId.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "The 'viewId' argument is required and cannot be empty."
            )
        }

        return automationProvider.execute(AutomationAction.ClickView(viewId))
    }
}
