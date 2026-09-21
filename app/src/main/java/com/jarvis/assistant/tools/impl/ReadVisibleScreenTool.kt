package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction

class ReadVisibleScreenTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "read_visible_screen"
    override val description: String = "Inspects the visible screen controls and returns a sanitized snapshot of visible elements."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return automationProvider.execute(AutomationAction.ReadVisibleScreen)
    }
}
