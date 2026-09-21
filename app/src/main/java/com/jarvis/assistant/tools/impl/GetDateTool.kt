package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GetDateTool(
    private val dateProvider: () -> Date = { Date() }
) : JarvisTool {

    override val name: String = "get_date"
    override val description: String = "Returns the current device date."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val now = dateProvider()
        val format = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val formattedDate = format.format(now)

        return ToolResult(
            success = true,
            message = formattedDate,
            data = mapOf(
                "date" to formattedDate,
                "timestamp" to now.time
            )
        )
    }
}
