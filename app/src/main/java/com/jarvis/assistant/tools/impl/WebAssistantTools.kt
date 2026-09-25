package com.jarvis.assistant.tools.impl

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jarvis.assistant.context.ContextPrivacyFilter
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Web Assistant Tools: controlled web searching, YouTube search, and active webpage reader.
 */

class SearchWebTool(
    private val context: Context,
    private val automationProvider: AndroidAutomationProvider? = null
) : JarvisTool {

    companion object {
        private const val TAG = "SearchWebTool"
    }

    override val name: String = "search_web"
    override val description: String = "Searches the web for queries, topics, or questions using the default browser (e.g. 'Search for Java Spring Boot jobs')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = (arguments["query"] as? String ?: arguments["searchQuery"] as? String ?: arguments["text"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "Please provide what you want to search for on the web, boss."
            )
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            automationProvider?.waitForTargetWindow("chrome", 4000L)

            ToolResult(
                success = true,
                message = "Searching Google for \"$query\", boss.",
                data = mapOf("query" to query, "url" to searchUrl)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch web search for: $query", e)
            ToolResult(
                success = false,
                message = "I couldn't launch the web search, boss: ${e.message}"
            )
        }
    }
}

class SearchYouTubeTool(
    private val context: Context? = null,
    private val automationProvider: AndroidAutomationProvider? = null,
    private val intentLauncher: ((Intent) -> Boolean)? = null
) : JarvisTool {

    companion object {
        private const val TAG = "SearchYouTubeTool"
    }

    override val name: String = "search_youtube"
    override val description: String = "Searches YouTube for videos, tutorials, or music tracks (e.g. 'Search YouTube for Java tutorials')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = (arguments["query"] as? String ?: arguments["searchQuery"] as? String ?: arguments["text"] as? String)?.trim()
        if (query.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "What would you like me to search for on YouTube, boss?"
            )
        }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Try YouTube native app search intent first
            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                `package` = "com.google.android.youtube"
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            var launched = false
            if (intentLauncher != null) {
                launched = intentLauncher.invoke(appIntent)
            } else if (context != null) {
                try {
                    if (context.packageManager.queryIntentActivities(appIntent, 0).isNotEmpty()) {
                        context.startActivity(appIntent)
                        launched = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "YouTube app search intent failed, falling back to browser URL", e)
                }

                if (!launched) {
                    // Fallback to YouTube web search URL
                    val webUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                }
            }

            // Central app launch synchronization
            val synchronizer = com.jarvis.assistant.automation.AppLaunchSynchronizer(automationProvider, context)
            synchronizer.launchAndSynchronize(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                timeoutMs = 8000L
            )

            // Allow search results to render
            kotlinx.coroutines.delay(1500L)

            // Verify visible screen results
            val summary = automationProvider?.getScreenSummary() ?: ""
            Log.i(TAG, "YouTube screen summary after search: $summary")

            ToolResult(
                success = true,
                message = "Searching YouTube for \"$query\", boss.",
                data = mapOf(
                    "query" to query,
                    "searchQuery" to query,
                    "screenSummary" to summary,
                    "targetApp" to "YouTube",
                    "targetPackage" to "com.google.android.youtube"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search YouTube for: $query", e)
            ToolResult(
                success = false,
                message = "I encountered an error searching YouTube, boss: ${e.message}"
            )
        }
    }
}

class ReadCurrentWebpageTool(
    private val automationProvider: AndroidAutomationProvider
) : JarvisTool {

    override val name: String = "read_current_webpage"
    override val description: String = "Extracts and reads the main text and content from the currently open webpage in the browser."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val snapshot = automationProvider.execute(AutomationAction.ReadVisibleScreen)
        val rawSummary = snapshot.data["summary"] as? String ?: snapshot.message

        if (rawSummary.isBlank() || rawSummary.contains("no readable text controls", ignoreCase = true)) {
            return@withContext ToolResult(
                success = false,
                message = "I couldn't find any readable webpage content on your screen, boss."
            )
        }

        val sanitized = ContextPrivacyFilter.sanitize(rawSummary)
        val filteredContent = extractCleanWebpageContent(sanitized)

        ToolResult(
            success = true,
            message = filteredContent,
            data = mapOf("content" to filteredContent)
        )
    }

    private fun extractCleanWebpageContent(raw: String): String {
        val lines = raw.split("; ")
        val cleanedLines = lines.mapNotNull { line ->
            val clean = line
                .replace("[Button: ", "")
                .replace("[Text: ", "")
                .replace("]", "")
                .trim()

            // Filter out common browser noise
            val lower = clean.lowercase()
            if (lower in listOf("back", "forward", "tabs", "menu", "search", "more options", "close tab", "new tab", "home", "refresh", "share")) {
                null
            } else if (clean.length > 3) {
                clean
            } else null
        }

        val combined = cleanedLines.joinToString(". ").take(400)
        return if (combined.isNotBlank()) {
            "Here is what the page says: $combined"
        } else {
            "The webpage is loaded, but no main text body was detected."
        }
    }
}

class SummarizeWebpageTool(
    private val readTool: ReadCurrentWebpageTool
) : JarvisTool {

    override val name: String = "summarize_webpage"
    override val description: String = "Summarizes the key highlights of the active webpage."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        return readTool.execute(arguments)
    }
}
