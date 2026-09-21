package com.jarvis.assistant

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.SafetyDecision
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SafetyManagerTest {

    private lateinit var safetyManager: SafetyManager

    private fun createTool(risk: RiskLevel, name: String = "tool_${risk.name.lowercase()}"): JarvisTool {
        return object : JarvisTool {
            override val name: String = name
            override val description: String = "Test description"
            override val riskLevel: RiskLevel = risk
            override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
                return ToolResult(true, "OK")
            }
        }
    }

    @Before
    fun setUp() {
        safetyManager = SafetyManager()
    }

    @Test
    fun lowRiskTool_isAllowedAutomatically() {
        val tool = createTool(RiskLevel.LOW)
        val decision = safetyManager.evaluate(tool, emptyMap())

        assertTrue(decision is SafetyDecision.Allowed)
        assertEquals(RiskLevel.LOW, (decision as SafetyDecision.Allowed).riskLevel)
    }

    @Test
    fun mediumRiskTool_withoutConfirmation_requiresConfirmation() {
        val tool = createTool(RiskLevel.MEDIUM)
        val decision = safetyManager.evaluate(tool, emptyMap(), isUserConfirmed = false)

        assertTrue(decision is SafetyDecision.RequiresConfirmation)
        assertEquals(RiskLevel.MEDIUM, (decision as SafetyDecision.RequiresConfirmation).riskLevel)
    }

    @Test
    fun mediumRiskTool_withConfirmation_isAllowed() {
        val tool = createTool(RiskLevel.MEDIUM)
        val decision = safetyManager.evaluate(tool, emptyMap(), isUserConfirmed = true)

        assertTrue(decision is SafetyDecision.Allowed)
        assertEquals(RiskLevel.MEDIUM, (decision as SafetyDecision.Allowed).riskLevel)
    }

    @Test
    fun typeTextTool_requiresConfirmationWhenUnconfirmed() {
        val tool = createTool(RiskLevel.MEDIUM, name = "type_text")
        val decision = safetyManager.evaluate(tool, mapOf("text" to "Search Query"), isUserConfirmed = false)

        assertTrue(decision is SafetyDecision.RequiresConfirmation)
        assertEquals("Type \"Search Query\" into the current field", (decision as SafetyDecision.RequiresConfirmation).reason)
    }

    @Test
    fun typeTextTool_isAllowedWhenConfirmed() {
        val tool = createTool(RiskLevel.MEDIUM, name = "type_text")
        val decision = safetyManager.evaluate(tool, mapOf("text" to "Search Query"), isUserConfirmed = true)

        assertTrue(decision is SafetyDecision.Allowed)
    }

    @Test
    fun typeTextTool_deniesPasswordPatterns() {
        val tool = createTool(RiskLevel.MEDIUM, name = "type_text")
        val decision = safetyManager.evaluate(tool, mapOf("text" to "My password is secret123"))

        assertTrue(decision is SafetyDecision.Denied)
        assertEquals("Input contains potential password pattern.", (decision as SafetyDecision.Denied).reason)
    }

    @Test
    fun highRiskTool_withoutConfirmation_requiresConfirmation() {
        val tool = createTool(RiskLevel.HIGH)
        val decision = safetyManager.evaluate(tool, emptyMap(), isUserConfirmed = false)

        assertTrue(decision is SafetyDecision.RequiresConfirmation)
        assertEquals(RiskLevel.HIGH, (decision as SafetyDecision.RequiresConfirmation).riskLevel)
    }

    @Test
    fun highRiskTool_withConfirmation_isAllowed() {
        val tool = createTool(RiskLevel.HIGH)
        val decision = safetyManager.evaluate(tool, emptyMap(), isUserConfirmed = true)

        assertTrue(decision is SafetyDecision.Allowed)
        assertEquals(RiskLevel.HIGH, (decision as SafetyDecision.Allowed).riskLevel)
    }

    @Test
    fun dangerousArgument_isDenied() {
        val tool = createTool(RiskLevel.LOW)
        val decision = safetyManager.evaluate(tool, mapOf("cmd" to "rm -rf /"))

        assertTrue(decision is SafetyDecision.Denied)
    }

    @Test
    fun analyzeCurrentScreenTool_isAllowedAsLowRisk() {
        val tool = createTool(RiskLevel.LOW, name = "analyze_current_screen")
        val decision = safetyManager.evaluate(tool, mapOf("focus" to "settings"))

        assertTrue(decision is SafetyDecision.Allowed)
        assertEquals(RiskLevel.LOW, (decision as SafetyDecision.Allowed).riskLevel)
    }

    @Test
    fun validateCoordinates_acceptsValidBoundsAndRejectsInvalid() {
        // Valid bounds within 1080x2400
        assertTrue(safetyManager.validateCoordinates(listOf(100, 200, 500, 600), 1080, 2400))

        // Negative bounds
        org.junit.Assert.assertFalse(safetyManager.validateCoordinates(listOf(-10, 200, 500, 600), 1080, 2400))

        // Out of screen bounds
        org.junit.Assert.assertFalse(safetyManager.validateCoordinates(listOf(100, 200, 1500, 600), 1080, 2400))

        // Inverted bounds (left >= right)
        org.junit.Assert.assertFalse(safetyManager.validateCoordinates(listOf(500, 200, 100, 600), 1080, 2400))

        // Invalid list size
        org.junit.Assert.assertFalse(safetyManager.validateCoordinates(listOf(100, 200, 500), 1080, 2400))
    }
}
