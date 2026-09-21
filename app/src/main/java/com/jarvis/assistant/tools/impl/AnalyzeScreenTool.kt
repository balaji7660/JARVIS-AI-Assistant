package com.jarvis.assistant.tools.impl

import android.util.Log
import com.jarvis.assistant.automation.ScreenCaptureProvider
import com.jarvis.assistant.automation.ScreenPrivacyFilter
import com.jarvis.assistant.automation.ScreenPrivacyResult
import com.jarvis.assistant.automation.ScreenSnapshot
import com.jarvis.assistant.automation.VisibleElement
import com.jarvis.assistant.data.remote.ApiClient
import com.jarvis.assistant.data.remote.ChatApiService
import com.jarvis.assistant.data.remote.ScreenAnalyzeRequest
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.ui.home.ScreenAnalysisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Screen understanding tool combining on-demand screenshot and sanitized accessibility metadata.
 * Operates at LOW risk level. Never captures sensitive credential screens.
 */
class AnalyzeScreenTool(
    private val screenCaptureProvider: ScreenCaptureProvider,
    private val screenPrivacyFilter: ScreenPrivacyFilter = ScreenPrivacyFilter(),
    private val automationProvider: AndroidAutomationProvider,
    private val chatApiService: ChatApiService = ApiClient.getChatApiService(),
    private val onStatusUpdate: ((ScreenAnalysisState, String?) -> Unit)? = null
) : JarvisTool {

    companion object {
        private const val TAG = "AnalyzeScreenTool"
    }

    override val name: String = "analyze_current_screen"
    override val description: String = "Captures and visually analyzes the user's active foreground screen when explicitly requested."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val focus = arguments["focus"] as? String

        onStatusUpdate?.invoke(ScreenAnalysisState.ANALYZING, "Analyzing screen...")

        try {
            // 1. Retrieve sanitized accessibility snapshot
            val snapshotResult = automationProvider.execute(AutomationAction.ReadVisibleScreen)
            @Suppress("UNCHECKED_CAST")
            val elements = (snapshotResult.data as? List<VisibleElement>) ?: emptyList()
            val snapshot = ScreenSnapshot(
                packageName = null,
                elements = elements
            )

            // 2. Perform on-demand screen capture
            val captureResult = screenCaptureProvider.captureCurrentScreen()
            if (captureResult.isFailure) {
                val errorMsg = captureResult.exceptionOrNull()?.message ?: "Failed to capture screen."
                Log.w(TAG, "Screen capture failed: $errorMsg")
                onStatusUpdate?.invoke(ScreenAnalysisState.FAILED, errorMsg)
                return@withContext ToolResult(
                    success = false,
                    message = "I couldn't capture the screen, boss: $errorMsg"
                )
            }

            val capture = captureResult.getOrThrow()

            // 3. Privacy Filter check & In-memory compression
            val filterResult = screenPrivacyFilter.processAndFilter(capture, snapshot)
            when (filterResult) {
                is ScreenPrivacyResult.Blocked -> {
                    Log.w(TAG, "Screen analysis blocked by privacy filter: ${filterResult.reason}")
                    onStatusUpdate?.invoke(ScreenAnalysisState.PROTECTED, filterResult.reason)
                    return@withContext ToolResult(
                        success = false,
                        message = filterResult.reason
                    )
                }
                is ScreenPrivacyResult.Safe -> {
                    // 4. Send to Backend Vision API
                    val request = ScreenAnalyzeRequest(
                        packageName = capture.packageName,
                        screenWidth = filterResult.width,
                        screenHeight = filterResult.height,
                        accessibilityElements = elements,
                        image = filterResult.base64Image,
                        focus = focus
                    )

                    val response = chatApiService.analyzeScreen(request)
                    if (response.success) {
                        onStatusUpdate?.invoke(ScreenAnalysisState.COMPLETE, "Screen analysis complete.")
                        return@withContext ToolResult(
                            success = true,
                            message = response.summary,
                            data = mapOf(
                                "summary" to response.summary,
                                "elementsCount" to response.detectedElements.size,
                                "suggestedAction" to response.suggestedAction
                            )
                        )
                    } else {
                        val failMsg = response.error ?: "Failed to analyze screen."
                        onStatusUpdate?.invoke(ScreenAnalysisState.FAILED, failMsg)
                        return@withContext ToolResult(
                            success = false,
                            message = failMsg
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AnalyzeScreenTool error", e)
            val msg = e.message ?: "Screen analysis error."
            onStatusUpdate?.invoke(ScreenAnalysisState.FAILED, msg)
            ToolResult(
                success = false,
                message = "I encountered an error analyzing your screen, boss: $msg"
            )
        }
    }
}
