package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GetTimeTool(
    private val timeProvider: () -> Date = { Date() }
) : JarvisTool {

    override val name: String = "get_time"
    override val description: String = "Returns the current device time."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val now = timeProvider()
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = format.format(now)

        return ToolResult(
            success = true,
            message = formattedTime,
            data = mapOf(
                "time" to formattedTime,
                "timestamp" to now.time
            )
        )
    }
}
