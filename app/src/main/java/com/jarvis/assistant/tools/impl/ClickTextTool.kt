package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction

class ClickTextTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "click_text"
    override val description: String = "Clicks a visible, clickable UI element matching the requested text."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val targetText = arguments["text"]?.toString()?.trim()
        if (targetText.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "The 'text' argument is required and cannot be empty."
            )
        }

        return automationProvider.execute(AutomationAction.ClickText(targetText))
    }
}
