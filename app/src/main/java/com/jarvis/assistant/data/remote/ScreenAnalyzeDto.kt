package com.jarvis.assistant.data.remote

import com.google.gson.annotations.SerializedName
import com.jarvis.assistant.automation.DetectedElement
import com.jarvis.assistant.automation.VisibleElement

data class ScreenAnalyzeRequest(
    @SerializedName("packageName") val packageName: String?,
    @SerializedName("screenWidth") val screenWidth: Int,
    @SerializedName("screenHeight") val screenHeight: Int,
    @SerializedName("accessibilityElements") val accessibilityElements: List<VisibleElement> = emptyList(),
    @SerializedName("image") val image: String? = null,
    @SerializedName("focus") val focus: String? = null
)

data class ScreenAnalyzeResponse(
    @SerializedName("success") val success: Boolean = true,
    @SerializedName("summary") val summary: String = "",
    @SerializedName("detectedElements") val detectedElements: List<DetectedElement> = emptyList(),
    @SerializedName("relevantElement") val relevantElement: DetectedElement? = null,
    @SerializedName("confidence") val confidence: Float = 0.85f,
    @SerializedName("suggestedAction") val suggestedAction: String? = null,
    @SerializedName("error") val error: String? = null
)
