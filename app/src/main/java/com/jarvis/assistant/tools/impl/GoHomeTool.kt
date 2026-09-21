package com.jarvis.assistant.tools.impl

import android.content.Context
import android.content.Intent
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult

class GoHomeTool(
    private val context: Context? = null,
    private val actionLauncher: (() -> Boolean)? = null
) : JarvisTool {

    override val name: String = "go_home"
    override val description: String = "Returns to the Android home screen."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return try {
            if (actionLauncher != null) {
                val launched = actionLauncher.invoke()
                if (launched) {
                    ToolResult(success = true, message = "Navigating to home screen.")
                } else {
                    ToolResult(success = false, message = "Unable to navigate to home screen.")
                }
            } else if (context != null) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                ToolResult(success = true, message = "Navigating to home screen.")
            } else {
                ToolResult(success = false, message = "Android context not available for home navigation.")
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                message = "Failed to navigate to home screen: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}
