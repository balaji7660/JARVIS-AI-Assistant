package com.jarvis.assistant.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import java.net.URI

class OpenUrlTool(
    private val context: Context? = null,
    private val urlLauncher: ((String) -> Boolean)? = null
) : JarvisTool {

    override val name: String = "open_url"
    override val description: String = "Opens a validated HTTP or HTTPS web URL."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
        val rawUrl = arguments["url"]?.toString()?.trim()

        if (rawUrl.isNullOrBlank()) {
            return ToolResult(
                success = false,
                message = "URL argument is missing or empty."
            )
        }

        // Strict scheme validation: ONLY http:// and https:// allowed
        val lowerUrl = rawUrl.lowercase()
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return ToolResult(
                success = false,
                message = "Invalid URL scheme. Only HTTP and HTTPS URLs are permitted."
            )
        }

        // Validate syntax and host
        val uri: URI
        try {
            uri = URI(rawUrl)
            if (uri.host.isNullOrBlank()) {
                return ToolResult(
                    success = false,
                    message = "Malformed URL: missing valid host."
                )
            }
        } catch (e: Exception) {
            return ToolResult(
                success = false,
                message = "Malformed URL format: ${e.message}"
            )
        }

        return try {
            if (urlLauncher != null) {
                val opened = urlLauncher.invoke(rawUrl)
                if (opened) {
                    ToolResult(
                        success = true,
                        message = "Opening URL in browser.",
                        data = mapOf("url" to rawUrl)
                    )
                } else {
                    ToolResult(
                        success = false,
                        message = "Unable to open browser for URL: $rawUrl"
                    )
                }
            } else if (context != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ToolResult(
                    success = true,
                    message = "Opening $rawUrl in browser.",
                    data = mapOf("url" to rawUrl)
                )
            } else {
                ToolResult(
                    success = false,
                    message = "Android context not available to launch URL."
                )
            }
        } catch (e: Exception) {
            ToolResult(
                success = false,
                message = "Failed to open URL: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }
}
