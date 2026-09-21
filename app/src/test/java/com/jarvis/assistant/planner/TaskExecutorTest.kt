package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.SafetyManager
import com.jarvis.assistant.tools.ToolRegistry
import com.jarvis.assistant.tools.ToolResult
import com.jarvis.assistant.tools.ToolRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskExecutorTest {

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var toolRouter: ToolRouter
    private lateinit var executor: TaskExecutor

    private class MockTool(
        override val name: String,
        override val riskLevel: RiskLevel = RiskLevel.LOW,
        var shouldSucceed: Boolean = true
    ) : JarvisTool {
        override val description: String = "Mock tool for testing"
        var executionCount: Int = 0

        override suspend fun execute(arguments: Map<String, Any?>): ToolResult {
            executionCount++
            return if (shouldSucceed) {
                ToolResult(true, "Executed $name successfully.")
            } else {
                ToolResult(false, "Failed to execute $name.")
            }
        }
    }

    @Before
    fun setUp() {
        toolRegistry = ToolRegistry()
        toolRegistry.register(MockTool("open_app"))
        toolRegistry.register(MockTool("click_text"))
        toolRegistry.register(MockTool("type_text", riskLevel = RiskLevel.MEDIUM))

        toolRouter = ToolRouter(toolRegistry, SafetyManager())
        executor = TaskExecutor(
            toolRouter = toolRouter,
            actionVerifier = ActionVerifier(null),
            recoveryStrategy = RecoveryStrategy()
        )
    }

    @Test
    fun executePlan_successfulMultiStepExecution() = runBlocking {
        val plan = TaskPlan(
            taskId = "plan_success",
            userRequest = "Open app and click",
            steps = listOf(
                TaskStep(stepId = 1, action = "open_app", arguments = mapOf("appName" to "YouTube")),
                TaskStep(stepId = 2, action = "click_text", arguments = mapOf("text" to "Search"))
            )
        )

        val statusUpdates = mutableListOf<String>()
        val result = executor.executePlan(
            plan = plan,
            onStatusUpdate = { state, stepIdx, total, msg -> statusUpdates.add(msg) },
            requestConfirmation = { true }
        )

        assertTrue(result.isSuccess)
        assertEquals(2, result.completedSteps)
        assertEquals(TaskExecutionState.COMPLETED, result.state)
    }

    @Test
    fun executePlan_stepRequiresConfirmation_stopsWhenDeclined() = runBlocking {
        val plan = TaskPlan(
            taskId = "plan_confirm",
            userRequest = "Type text",
            steps = listOf(
                TaskStep(
                    stepId = 1,
                    action = "type_text",
                    arguments = mapOf("text" to "Query"),
                    riskLevel = RiskLevel.MEDIUM,
                    requiresConfirmation = true
                )
            )
        )

        var requestedConfirmation = false
        val result = executor.executePlan(
            plan = plan,
            onStatusUpdate = { _, _, _, _ -> },
            requestConfirmation = {
                requestedConfirmation = true
                false // User declines
            }
        )

        assertTrue(requestedConfirmation)
        assertFalse(result.isSuccess)
        assertEquals(TaskExecutionState.CANCELLED, result.state)
        assertEquals(0, result.completedSteps)
    }

    @Test
    fun executePlan_cancellationMidTask_stopsFurtherSteps() = runBlocking {
        val tool1 = toolRegistry.get("open_app") as MockTool
        val tool2 = toolRegistry.get("click_text") as MockTool

        val plan = TaskPlan(
            taskId = "plan_cancel",
            userRequest = "Multi-step cancel",
            steps = listOf(
                TaskStep(stepId = 1, action = "open_app", arguments = mapOf("appName" to "YouTube")),
                TaskStep(stepId = 2, action = "click_text", arguments = mapOf("text" to "Search")),
                TaskStep(stepId = 3, action = "click_text", arguments = mapOf("text" to "Next"))
            )
        )

        val result = executor.executePlan(
            plan = plan,
            onStatusUpdate = { state, stepIdx, _, _ ->
                // Cancel during step 1's EXECUTING callback — the post-callback check in TaskExecutor
                // will detect isCancelled and abort before dispatching any tool.
                if (stepIdx == 1 && state == TaskExecutionState.EXECUTING) {
                    executor.cancel()
                }
            },
            requestConfirmation = { true }
        )

        assertFalse(result.isSuccess)
        assertEquals(TaskExecutionState.CANCELLED, result.state)
        assertEquals(0, tool1.executionCount) // Step 1 tool was never dispatched (cancelled before dispatch)
        assertEquals(0, tool2.executionCount) // Step 2 was never reached
    }

    @Test
    fun executePlan_stepFailureWithRetry_retriesAndFailsSafely() = runBlocking {
        val failingTool = MockTool("open_app", shouldSucceed = false)
        toolRegistry.register(failingTool)

        val plan = TaskPlan(
            taskId = "plan_failing",
            userRequest = "Failing plan",
            steps = listOf(
                TaskStep(stepId = 1, action = "open_app", arguments = mapOf("appName" to "YouTube"), retryCount = 1)
            )
        )

        val result = executor.executePlan(
            plan = plan,
            onStatusUpdate = { _, _, _, _ -> },
            requestConfirmation = { true }
        )

        assertFalse(result.isSuccess)
        assertEquals(TaskExecutionState.FAILED, result.state)
        assertEquals(2, failingTool.executionCount) // Initial execution + 1 retry
    }
}
