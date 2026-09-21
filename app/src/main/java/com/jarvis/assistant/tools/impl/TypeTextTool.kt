package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction

class TypeTextTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "type_text"
    override val description: String = "Inputs text into the currently focused editable field."
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val text = arguments["text"]?.toString()
        if (text == null) {
            return ToolResult(
                success = false,
                message = "The 'text' argument is required."
            )
        }

        return automationProvider.execute(AutomationAction.TypeText(text))
    }
}
