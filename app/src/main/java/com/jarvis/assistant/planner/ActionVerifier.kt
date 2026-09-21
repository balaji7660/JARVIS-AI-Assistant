package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.automation.AndroidAutomationProvider

/**
 * Result of post-action verification.
 */
data class VerificationResult(
    val isVerified: Boolean,
    val observedState: String,
    val reason: String? = null
)

/**
 * Verifies that an executed action produced the expected on-device outcome.
 * Utilizes the sanitized Accessibility hierarchy and package state (without persistent screenshots).
 */
class ActionVerifier(
    private val automationProvider: AndroidAutomationProvider? = null
) {
    /**
     * Verifies post-execution state against step expectations.
     */
    suspend fun verify(step: TaskStep, toolOutputMessage: String): VerificationResult {
        val expected = step.expectedResult

        // If tool output explicitly failed at tool level, verification fails
        if (toolOutputMessage.startsWith("Failed", ignoreCase = true) ||
            toolOutputMessage.startsWith("Error", ignoreCase = true) ||
            toolOutputMessage.startsWith("Denied", ignoreCase = true)
        ) {
            return VerificationResult(
                isVerified = false,
                observedState = toolOutputMessage,
                reason = "Tool execution returned error: $toolOutputMessage"
            )
        }

        if (expected == null) {
            // No explicit verification condition defined; rely on successful tool execution
            return VerificationResult(
                isVerified = true,
                observedState = toolOutputMessage
            )
        }

        val provider = automationProvider
        val currentPackage = provider?.getCurrentPackage() ?: ""

        // 1. Package verification
        if (!expected.expectedPackage.isNullOrBlank()) {
            val expectedPkg = expected.expectedPackage.trim().lowercase()
            val actualPkg = currentPackage.trim().lowercase()

            if (actualPkg.isNotEmpty() && !actualPkg.contains(expectedPkg) && !expectedPkg.contains(actualPkg)) {
                return VerificationResult(
                    isVerified = false,
                    observedState = "Current package is '$currentPackage'",
                    reason = "Expected package '${expected.expectedPackage}' but observed '$currentPackage'"
                )
            }
        }

        // 2. Text visibility verification
        if (!expected.expectedText.isNullOrBlank() && provider != null) {
            val summary = provider.getScreenSummary()
            if (!summary.contains(expected.expectedText, ignoreCase = true)) {
                return VerificationResult(
                    isVerified = false,
                    observedState = "Text '${expected.expectedText}' not found on screen",
                    reason = "Expected text '${expected.expectedText}' was not detected in visible UI hierarchy."
                )
            }
        }

        return VerificationResult(
            isVerified = true,
            observedState = if (currentPackage.isNotBlank()) "Package: $currentPackage" else toolOutputMessage
        )
    }
}
