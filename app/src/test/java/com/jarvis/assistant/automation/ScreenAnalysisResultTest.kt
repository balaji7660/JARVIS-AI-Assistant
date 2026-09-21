package com.jarvis.assistant.automation

import com.google.gson.Gson
import com.jarvis.assistant.data.remote.ScreenAnalyzeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenAnalysisResultTest {

    private val gson = Gson()

    @Test
    fun detectedElement_isValidBounds() {
        val validElement = DetectedElement(
            label = "Settings",
            bounds = listOf(100, 200, 300, 400)
        )
        assertTrue(validElement.isValidBounds(1080, 2400))

        val outOfBoundsElement = DetectedElement(
            label = "Offscreen",
            bounds = listOf(100, 200, 1200, 400) // right > 1080
        )
        assertFalse(outOfBoundsElement.isValidBounds(1080, 2400))

        val invertedElement = DetectedElement(
            label = "Inverted",
            bounds = listOf(300, 400, 100, 200) // left > right
        )
        assertFalse(invertedElement.isValidBounds(1080, 2400))

        val invalidSizeElement = DetectedElement(
            label = "Short",
            bounds = listOf(100, 200, 300) // 3 elements instead of 4
        )
        assertFalse(invalidSizeElement.isValidBounds(1080, 2400))
    }

    @Test
    fun screenAnalyzeResponse_parsesValidJson() {
        val json = """
            {
                "success": true,
                "summary": "You are viewing Settings with 3 visible items.",
                "detectedElements": [
                    {
                        "label": "Network & internet",
                        "type": "button",
                        "bounds": [50, 100, 1000, 200],
                        "resourceId": "network_btn",
                        "text": "Network & internet",
                        "confidence": 0.95
                    }
                ],
                "relevantElement": {
                    "label": "Network & internet",
                    "type": "button",
                    "bounds": [50, 100, 1000, 200],
                    "confidence": 0.95
                },
                "confidence": 0.95,
                "suggestedAction": "click"
            }
        """.trimIndent()

        val response = gson.fromJson(json, ScreenAnalyzeResponse::class.java)
        assertTrue(response.success)
        assertEquals("You are viewing Settings with 3 visible items.", response.summary)
        assertEquals(1, response.detectedElements.size)
        assertEquals("Network & internet", response.detectedElements[0].label)
        assertNotNull(response.relevantElement)
        assertEquals("click", response.suggestedAction)
    }

    @Test
    fun screenAnalyzeResponse_handlesMissingOrPartialFields() {
        val json = """
            {
                "success": true,
                "summary": "Minimal screen view"
            }
        """.trimIndent()

        val response = gson.fromJson(json, ScreenAnalyzeResponse::class.java)
        assertTrue(response.success)
        assertEquals("Minimal screen view", response.summary)
        assertTrue(response.detectedElements.isEmpty())
        assertEquals(null, response.relevantElement)
    }
}
