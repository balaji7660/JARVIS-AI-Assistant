package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskPlanValidatorTest {

    private lateinit var validator: TaskPlanValidator

    @Before
    fun setUp() {
        validator = TaskPlanValidator()
    }

    @Test
    fun emptyPlan_isRejected() {
        val plan = TaskPlan(
            taskId = "test_1",
            userRequest = "do nothing",
            steps = emptyList()
        )
        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Invalid)
        assertEquals("TaskPlan contains 0 steps.", (result as PlanValidationResult.Invalid).reason)
    }

    @Test
    fun planExceedingMaxSteps_isRejected() {
        val steps = (1..13).map {
            TaskStep(stepId = it, action = "go_home")
        }
        val plan = TaskPlan(
            taskId = "test_max_steps",
            userRequest = "too many steps",
            steps = steps
        )
        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Invalid)
        assertTrue((result as PlanValidationResult.Invalid).reason.contains("exceeds maximum step limit"))
    }

    @Test
    fun planWithUnsupportedTool_isRejected() {
        val plan = TaskPlan(
            taskId = "test_unsupported",
            userRequest = "run command",
            steps = listOf(
                TaskStep(stepId = 1, action = "execute_arbitrary_shell", arguments = mapOf("cmd" to "ls"))
            )
        )
        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Invalid)
        assertTrue((result as PlanValidationResult.Invalid).reason.contains("is not in approved tool registry"))
    }

    @Test
    fun planWithDangerousPattern_isRejected() {
        val plan = TaskPlan(
            taskId = "test_dangerous",
            userRequest = "delete root",
            steps = listOf(
                TaskStep(stepId = 1, action = "open_url", arguments = mapOf("url" to "https://rm -rf /"))
            )
        )
        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Invalid)
        assertTrue((result as PlanValidationResult.Invalid).reason.contains("Dangerous pattern"))
    }

    @Test
    fun planWithPasswordCredentials_isRejected() {
        val plan = TaskPlan(
            taskId = "test_password",
            userRequest = "type password",
            steps = listOf(
                TaskStep(stepId = 1, action = "type_text", arguments = mapOf("text" to "my secret password123"))
            )
        )
        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Invalid)
        assertTrue((result as PlanValidationResult.Invalid).reason.contains("Automating sensitive credentials"))
    }

    @Test
    fun validMultiStepPlan_isSanitizedAndAccepted() {
        val plan = TaskPlan(
            taskId = "test_valid",
            userRequest = "Open YouTube and search",
            steps = listOf(
                TaskStep(stepId = 1, action = "open_app", arguments = mapOf("appName" to "YouTube"), riskLevel = RiskLevel.LOW),
                TaskStep(stepId = 2, action = "wait_for_screen", arguments = mapOf("expectedPackage" to "com.google.android.youtube", "timeoutMs" to 3000L)),
                TaskStep(stepId = 3, action = "click_text", arguments = mapOf("text" to "Search")),
                TaskStep(stepId = 4, action = "type_text", arguments = mapOf("text" to "Kotlin Coroutines Tutorial"))
            )
        )

        val result = validator.validate(plan)
        assertTrue(result is PlanValidationResult.Valid)

        val sanitized = (result as PlanValidationResult.Valid).sanitizedPlan
        assertEquals(4, sanitized.steps.size)
        // type_text must be MEDIUM risk and requires confirmation
        assertEquals(RiskLevel.MEDIUM, sanitized.steps[3].riskLevel)
        assertTrue(sanitized.steps[3].requiresConfirmation)
        assertTrue(sanitized.requiresConfirmation)
    }

    @Test
    fun timeoutsAndRetries_areClampedToSafeLimits() {
        // timeoutMs=10000 exceeds MAX_STEP_TIMEOUT_MS (5000) but total=10000ms is within MAX_TOTAL_TASK_TIMEOUT_MS.
        // Verifies that timeoutMs is clamped to MAX_STEP_TIMEOUT_MS.
        val plan = TaskPlan(
            taskId = "test_clamps",
            userRequest = "test clamps",
            steps = listOf(
                TaskStep(
                    stepId = 1,
                    action = "go_home",
                    timeoutMs = 10000L,
                    retryCount = 0
                )
            )
        )

        val result = validator.validate(plan)
        assertTrue("Plan should be Valid after clamping", result is PlanValidationResult.Valid)
        val sanitized = (result as PlanValidationResult.Valid).sanitizedPlan
        assertEquals(TaskPlanValidator.MAX_STEP_TIMEOUT_MS, sanitized.steps[0].timeoutMs)
        assertEquals(0, sanitized.steps[0].retryCount)
    }

    @Test
    fun planWithExcessiveRetries_isRejectedByTotalTimeout() {
        // retryCount=100 with large timeout makes total estimated timeout exceed MAX_TOTAL_TASK_TIMEOUT_MS
        val plan = TaskPlan(
            taskId = "test_total_timeout",
            userRequest = "test total timeout",
            steps = listOf(
                TaskStep(
                    stepId = 1,
                    action = "go_home",
                    timeoutMs = 999999L,
                    retryCount = 100
                )
            )
        )

        val result = validator.validate(plan)
        assertTrue("Plan should be rejected due to total timeout", result is PlanValidationResult.Invalid)
        assertTrue((result as PlanValidationResult.Invalid).reason.contains("Total task timeout"))
    }
}
