package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.automation.AndroidAutomationProvider
import com.jarvis.assistant.tools.automation.AutomationAction
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionVerifierTest {

    private class TestAutomationProvider(
        private val currentPackage: String = "com.google.android.youtube",
        private val visibleSummary: String = "YouTube Home Screen Search Subscriptions"
    ) : AndroidAutomationProvider {
        override suspend fun execute(action: AutomationAction): ToolResult = ToolResult(
            success = true,
            message = visibleSummary,
            data = mapOf("packageName" to currentPackage, "summary" to visibleSummary)
        )
    }

    @Test
    fun verify_matchingPackage_succeeds() = runBlocking {
        val verifier = ActionVerifier(TestAutomationProvider(currentPackage = "com.google.android.youtube"))
        val step = TaskStep(
            stepId = 1,
            action = "open_app",
            arguments = mapOf("appName" to "YouTube"),
            expectedResult = ExpectedResult(expectedPackage = "com.google.android.youtube")
        )

        val result = verifier.verify(step, "Opened YouTube successfully.")
        assertTrue(result.isVerified)
    }

    @Test
    fun verify_mismatchedPackage_fails() = runBlocking {
        val verifier = ActionVerifier(TestAutomationProvider(currentPackage = "com.android.settings"))
        val step = TaskStep(
            stepId = 1,
            action = "open_app",
            arguments = mapOf("appName" to "YouTube"),
            expectedResult = ExpectedResult(expectedPackage = "com.google.android.youtube")
        )

        val result = verifier.verify(step, "Opened YouTube successfully.")
        assertFalse(result.isVerified)
        assertTrue(result.reason!!.contains("Expected package"))
    }

    @Test
    fun verify_textInSummary_succeeds() = runBlocking {
        val verifier = ActionVerifier(TestAutomationProvider(visibleSummary = "Results for Kotlin Spring Boot"))
        val step = TaskStep(
            stepId = 2,
            action = "type_text",
            expectedResult = ExpectedResult(expectedText = "Kotlin Spring Boot")
        )

        val result = verifier.verify(step, "Typed text.")
        assertTrue(result.isVerified)
    }

    @Test
    fun verify_failedToolMessage_failsImmediately() = runBlocking {
        val verifier = ActionVerifier(TestAutomationProvider())
        val step = TaskStep(stepId = 1, action = "click_text")

        val result = verifier.verify(step, "Failed to locate element.")
        assertFalse(result.isVerified)
    }
}
