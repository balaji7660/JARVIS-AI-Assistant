package com.jarvis.assistant.tools.impl

import android.util.Log
import com.jarvis.assistant.automation.VisibleElement
import com.jarvis.assistant.context.ContextPrivacyFilter
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screen Assistant tools providing on-device screen inspection, element resolution,
 * error diagnosis, and interactive navigation without unnecessary remote data leakage.
 */

class FindScreenElementTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    companion object {
        private const val TAG = "FindScreenElementTool"
    }

    override val name: String = "find_screen_element"
    override val description: String = "Finds and locates a UI control or button on screen by label, description, or role (e.g. 'search bar', 'login button', 'download icon')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = (arguments["query"] as? String ?: arguments["text"] as? String ?: arguments["element"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "Please specify the element or text you are looking for, boss."
            )
        }

        val snapshotResult = automationProvider.execute(AutomationAction.ReadVisibleScreen)
        @Suppress("UNCHECKED_CAST")
        val elements = (snapshotResult.data["elements"] as? List<VisibleElement>)
            ?: (snapshotResult.data as? List<VisibleElement>)
            ?: parseElementsFromSummary(snapshotResult.data["summary"] as? String ?: snapshotResult.message)

        if (elements.isEmpty()) {
            return@withContext ToolResult(
                success = false,
                message = "I couldn't detect any readable elements on this screen, boss."
            )
        }

        val match = resolveBestElement(query, elements)
        return@withContext if (match != null) {
            val label = match.text ?: match.contentDescription ?: match.viewId ?: "element"
            val typeStr = if (match.clickable) "clickable button" else "text element"
            ToolResult(
                success = true,
                message = "Found $typeStr \"$label\" on screen.",
                data = mapOf(
                    "label" to label,
                    "clickable" to match.clickable,
                    "viewId" to (match.viewId ?: ""),
                    "className" to (match.className ?: "")
                )
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't find \"$query\" on the current screen, boss."
            )
        }
    }

    private fun resolveBestElement(query: String, elements: List<VisibleElement>): VisibleElement? {
        val q = query.lowercase().trim()
        val strippedQuery = q.replace("button", "").replace("icon", "").replace("the", "").replace("bar", "").trim()

        // 1. Exact view ID
        elements.firstOrNull { it.viewId?.equals(query, ignoreCase = true) == true || it.viewId?.endsWith("/$query", ignoreCase = true) == true }?.let { return it }

        // 2. Exact text
        elements.firstOrNull { it.text?.trim()?.equals(query, ignoreCase = true) == true }?.let { return it }

        // 3. Exact content description
        elements.firstOrNull { it.contentDescription?.trim()?.equals(query, ignoreCase = true) == true }?.let { return it }

        // 4. Normalized / stripped text or description
        if (strippedQuery.isNotBlank()) {
            elements.firstOrNull {
                val t = it.text?.lowercase()?.trim() ?: ""
                val d = it.contentDescription?.lowercase()?.trim() ?: ""
                t == strippedQuery || d == strippedQuery
            }?.let { return it }
        }

        // 5. Partial text contains
        elements.firstOrNull {
            val t = it.text?.lowercase() ?: ""
            val d = it.contentDescription?.lowercase() ?: ""
            t.contains(q) || d.contains(q) || (strippedQuery.isNotBlank() && (t.contains(strippedQuery) || d.contains(strippedQuery)))
        }?.let { return it }

        // 6. Role-based fallback (e.g. "search" / "search bar")
        if (q.contains("search")) {
            elements.firstOrNull {
                val vid = it.viewId?.lowercase() ?: ""
                val d = it.contentDescription?.lowercase() ?: ""
                val t = it.text?.lowercase() ?: ""
                vid.contains("search") || vid.contains("url_bar") || d.contains("search") || t.contains("search")
            }?.let { return it }
        }

        return null
    }

    private fun parseElementsFromSummary(summary: String): List<VisibleElement> {
        if (summary.isBlank()) return emptyList()
        val items = summary.split(";")
        return items.mapNotNull { item ->
            val trimmed = item.trim()
            if (trimmed.startsWith("[Button: ") && trimmed.endsWith("]")) {
                val text = trimmed.removePrefix("[Button: ").removeSuffix("]")
                VisibleElement(text = text, clickable = true)
            } else if (trimmed.startsWith("[Text: ") && trimmed.endsWith("]")) {
                val text = trimmed.removePrefix("[Text: ").removeSuffix("]")
                VisibleElement(text = text, clickable = false)
            } else if (trimmed.isNotBlank()) {
                VisibleElement(text = trimmed, clickable = false)
            } else null
        }
    }
}

class ReadCurrentScreenTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "read_current_screen"
    override val description: String = "Reads, summarizes, and extracts visible content on the active screen."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val result = automationProvider.execute(AutomationAction.ReadVisibleScreen)
        val summary = result.data["summary"] as? String ?: result.message

        if (summary.isBlank() || summary.contains("no readable text controls", ignoreCase = true)) {
            return@withContext ToolResult(
                success = true,
                message = "The current screen is active, but there is no readable text visible right now, boss."
            )
        }

        val sanitized = ContextPrivacyFilter.sanitize(summary)
        val speakable = formatSummaryForSpeech(sanitized)

        ToolResult(
            success = true,
            message = speakable,
            data = mapOf(
                "summary" to sanitized,
                "packageName" to (result.data["packageName"] as? String ?: "")
            )
        )
    }

    private fun formatSummaryForSpeech(raw: String): String {
        val cleaned = raw
            .replace("[Button: ", "")
            .replace("[Text: ", "")
            .replace("]", "")
            .replace(";", ",")
            .trim()
        val truncated = if (cleaned.length > 300) cleaned.take(297) + "..." else cleaned
        return "On your screen, I see: $truncated"
    }
}

class DiagnoseScreenErrorTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    companion object {
        private val ERROR_KEYWORDS = listOf(
            "error", "failed", "exception", "cannot connect", "unable to",
            "something went wrong", "denied", "invalid", "timed out", "retry",
            "not found", "404", "500", "fatal", "unauthorized", "network error"
        )
    }

    override val name: String = "diagnose_screen_error"
    override val description: String = "Detects and explains visible errors, alert dialogs, or failure messages on the current screen."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val result = automationProvider.execute(AutomationAction.ReadVisibleScreen)
        val summary = result.data["summary"] as? String ?: result.message
        val sanitized = ContextPrivacyFilter.sanitize(summary)

        val detectedErrors = mutableListOf<String>()
        val segments = sanitized.split(";", "\n")

        for (segment in segments) {
            val lower = segment.lowercase()
            if (ERROR_KEYWORDS.any { lower.contains(it) }) {
                val clean = segment.replace("[Button: ", "").replace("[Text: ", "").replace("]", "").trim()
                if (clean.isNotBlank() && clean.length > 3) {
                    detectedErrors.add(clean)
                }
            }
        }

        if (detectedErrors.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = "I inspected the screen and didn't find any visible error messages or crash alerts, boss."
            )
        }

        val primaryError = detectedErrors.joinToString("; ").take(250)
        val explanation = when {
            primaryError.contains("network", ignoreCase = true) || primaryError.contains("cannot connect", ignoreCase = true) ->
                "It looks like a connectivity issue: \"$primaryError\". Check your Wi-Fi or mobile data."
            primaryError.contains("denied", ignoreCase = true) || primaryError.contains("permission", ignoreCase = true) || primaryError.contains("unauthorized", ignoreCase = true) ->
                "This appears to be a permissions or access error: \"$primaryError\"."
            primaryError.contains("not found", ignoreCase = true) || primaryError.contains("404", ignoreCase = true) ->
                "The requested resource was not found: \"$primaryError\"."
            else ->
                "The screen is displaying this error: \"$primaryError\"."
        }

        ToolResult(
            success = true,
            message = explanation,
            data = mapOf("detectedErrors" to detectedErrors)
        )
    }
}

class ClickScreenElementTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "click_screen_element"
    override val description: String = "Clicks a button or control on screen by text, content description, or resource ID."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val text = (arguments["text"] as? String ?: arguments["label"] as? String ?: arguments["query"] as? String)?.trim()
        val viewId = (arguments["viewId"] as? String)?.trim()

        if (!viewId.isNullOrBlank()) {
            return automationProvider.execute(AutomationAction.ClickView(viewId))
        }
        if (!text.isNullOrBlank()) {
            return automationProvider.execute(AutomationAction.ClickText(text))
        }
        return ToolResult(
            success = false,
            message = "Please provide the text label or view ID of the element to click."
        )
    }
}

class ScrollScreenTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "scroll_screen"
    override val description: String = "Scrolls the screen container forward, backward, up, or down."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val dir = (arguments["direction"] as? String ?: "down").lowercase().trim()
        val isForward = dir == "down" || dir == "forward" || dir == "next"
        val action = if (isForward) AutomationAction.ScrollForward else AutomationAction.ScrollBackward
        return automationProvider.execute(action)
    }
}
