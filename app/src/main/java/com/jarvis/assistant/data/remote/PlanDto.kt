package com.jarvis.assistant.data.remote

import com.google.gson.annotations.SerializedName
import com.jarvis.assistant.planner.ExpectedResult
import com.jarvis.assistant.planner.TaskPlan
import com.jarvis.assistant.planner.TaskStep
import com.jarvis.assistant.tools.RiskLevel

/**
 * Request payload for POST /api/plan
 */
data class PlanRequest(
    @SerializedName("prompt")
    val prompt: String,

    @SerializedName("memoryContext")
    val memoryContext: String? = null,

    @SerializedName("currentPackage")
    val currentPackage: String? = null,

    @SerializedName("visibleScreenText")
    val visibleScreenText: String? = null,

    @SerializedName("context")
    val context: ContextDto? = null
)

/**
 * Bounded context metadata passed to backend planning service.
 */
data class ContextDto(
    @SerializedName("currentApp")
    val currentApp: String? = null,

    @SerializedName("currentPackage")
    val currentPackage: String? = null,

    @SerializedName("screenSummary")
    val screenSummary: String? = null,

    @SerializedName("currentTarget")
    val currentTarget: String? = null,

    @SerializedName("lastAction")
    val lastAction: String? = null,

    @SerializedName("recentEntities")
    val recentEntities: List<String>? = null
)

/**
 * Response payload from POST /api/plan
 */
data class PlanResponseDto(
    @SerializedName("type")
    val type: String? = "task_plan",

    @SerializedName("taskId")
    val taskId: String? = null,

    @SerializedName("userRequest")
    val userRequest: String? = null,

    @SerializedName("estimatedRisk")
    val estimatedRisk: String? = "LOW",

    @SerializedName("requiresConfirmation")
    val requiresConfirmation: Boolean = false,

    @SerializedName("steps")
    val steps: List<PlanStepDto>? = emptyList()
) {
    fun toDomain(): TaskPlan {
        val parsedRisk = when (estimatedRisk?.uppercase()) {
            "HIGH" -> RiskLevel.HIGH
            "MEDIUM" -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val domainSteps = steps?.mapIndexed { index, s ->
            val stepRisk = when (s.riskLevel?.uppercase()) {
                "HIGH" -> RiskLevel.HIGH
                "MEDIUM" -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            TaskStep(
                stepId = s.stepId ?: (index + 1),
                action = s.action ?: "read_visible_screen",
                arguments = s.arguments ?: emptyMap(),
                expectedResult = s.expectedResult?.let { exp ->
                    ExpectedResult(
                        expectedPackage = exp.expectedPackage,
                        expectedText = exp.expectedText,
                        expectedScreenState = exp.expectedScreenState,
                        customCriteria = exp.customCriteria
                    )
                },
                riskLevel = stepRisk,
                requiresConfirmation = s.requiresConfirmation,
                timeoutMs = s.timeoutMs ?: 3000L,
                retryCount = s.retryCount ?: 1
            )
        } ?: emptyList()

        return TaskPlan(
            taskId = taskId ?: "plan_${System.currentTimeMillis()}",
            userRequest = userRequest ?: "",
            steps = domainSteps,
            estimatedRisk = parsedRisk,
            requiresConfirmation = requiresConfirmation
        )
    }
}

data class PlanStepDto(
    @SerializedName("stepId")
    val stepId: Int? = null,

    @SerializedName("action")
    val action: String? = null,

    @SerializedName("arguments")
    val arguments: Map<String, Any?>? = null,

    @SerializedName("expectedResult")
    val expectedResult: ExpectedResultDto? = null,

    @SerializedName("riskLevel")
    val riskLevel: String? = "LOW",

    @SerializedName("requiresConfirmation")
    val requiresConfirmation: Boolean = false,

    @SerializedName("timeoutMs")
    val timeoutMs: Long? = 3000L,

    @SerializedName("retryCount")
    val retryCount: Int? = 1
)

data class ExpectedResultDto(
    @SerializedName("expectedPackage")
    val expectedPackage: String? = null,

    @SerializedName("expectedText")
    val expectedText: String? = null,

    @SerializedName("expectedScreenState")
    val expectedScreenState: String? = null,

    @SerializedName("customCriteria")
    val customCriteria: String? = null
)
