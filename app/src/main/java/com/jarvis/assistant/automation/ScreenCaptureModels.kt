package com.jarvis.assistant.automation

import android.graphics.Bitmap

/**
 * In-memory representation of a captured screen frame.
 * Does NOT persist any image data to disk or external storage.
 */
data class ScreenCapture(
    val bitmap: Bitmap? = null,
    val base64Data: String? = null,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String? = null
)

/**
 * Combined bounded model uniting sanitized accessibility metadata with visual screen capture.
 */
data class ScreenUnderstandingContext(
    val currentPackage: String? = null,
    val currentActivity: String? = null,
    val elements: List<VisibleElement> = emptyList(),
    val capture: ScreenCapture? = null,
    val screenWidth: Int = 1080,
    val screenHeight: Int = 2400,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A UI element visually detected or inferred from the screen.
 */
data class DetectedElement(
    val label: String,
    val type: String = "element",
    val bounds: List<Int> = emptyList(), // [left, top, right, bottom]
    val resourceId: String? = null,
    val text: String? = null,
    val confidence: Float = 0.8f
) {
    fun isValidBounds(screenWidth: Int, screenHeight: Int): Boolean {
        if (bounds.size != 4) return false
        val (left, top, right, bottom) = bounds
        return left >= 0 && top >= 0 && right <= screenWidth && bottom <= screenHeight && left < right && top < bottom
    }
}

/**
 * Structured AI vision analysis result.
 */
data class ScreenAnalysisResult(
    val success: Boolean,
    val summary: String,
    val detectedElements: List<DetectedElement> = emptyList(),
    val relevantElement: DetectedElement? = null,
    val confidence: Float = 0.85f,
    val suggestedAction: String? = null
)
