package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.planner.TaskPlanValidator
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import kotlinx.coroutines.delay

/**
 * Tool to wait for expected screen state (package or text) within a strict bounded timeout (max 5000ms).
 */
class WaitForScreenTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "wait_for_screen"
    override val description: String = "Waits for a target package or text to appear on the foreground screen with bounded timeout."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val expectedPackage = (arguments["expectedPackage"] as? String)?.trim()
        val expectedText = (arguments["expectedText"] as? String)?.trim()

        val rawTimeout = (arguments["timeoutMs"] as? Number)?.toLong() ?: 3000L
        val timeoutMs = rawTimeout.coerceIn(
            TaskPlanValidator.MIN_STEP_TIMEOUT_MS,
            TaskPlanValidator.MAX_STEP_TIMEOUT_MS
        )

        if (expectedPackage.isNullOrBlank() && expectedText.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "wait_for_screen requires at least 'expectedPackage' or 'expectedText'."
            )
        }

        val startTime = System.currentTimeMillis()
        val pollIntervalMs = 250L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            var packageMatched = true
            var textMatched = true

            // Check package
            if (!expectedPackage.isNullOrBlank()) {
                val currentPkg = automationProvider.getCurrentPackage().lowercase()
                packageMatched = currentPkg.contains(expectedPackage.lowercase())
            }

            // Check text
            if (!expectedText.isNullOrBlank()) {
                val summary = automationProvider.getScreenSummary()
                textMatched = summary.contains(expectedText, ignoreCase = true)
            }

            if (packageMatched && textMatched) {
                val elapsed = System.currentTimeMillis() - startTime
                return ToolResult(
                    success = true,
                    message = "Screen matched expectations in ${elapsed}ms.",
                    data = mapOf(
                        "elapsedMs" to elapsed,
                        "package" to automationProvider.getCurrentPackage()
                    )
                )
            }

            delay(pollIntervalMs)
        }

        val elapsed = System.currentTimeMillis() - startTime
        val currentPackage = automationProvider.getCurrentPackage()
        return ToolResult(
            success = false,
            message = "Timed out after ${elapsed}ms waiting for screen ($expectedPackage / $expectedText). Current: '$currentPackage'",
            data = mapOf(
                "elapsedMs" to elapsed,
                "currentPackage" to currentPackage
            )
        )
    }
}
