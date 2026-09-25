package com.jarvis.assistant.planner

import android.util.Log
import com.jarvis.assistant.context.ConversationContext
import com.jarvis.assistant.context.ReferenceResolutionResult
import com.jarvis.assistant.context.ReferenceResolver
import com.jarvis.assistant.data.remote.ChatApiService
import com.jarvis.assistant.data.remote.ContextDto
import com.jarvis.assistant.data.remote.PlanRequest
import com.jarvis.assistant.tools.RiskLevel

/**
 * High-level Task Planner interface.
 */
interface TaskPlanner {
    suspend fun plan(
        prompt: String,
        memoryContext: String = "",
        currentPackage: String = "",
        visibleScreenText: String = "",
        context: ConversationContext? = null
    ): PlanValidationResult
}

/**
 * Production Task Planner with remote AI planning and local deterministic fallback.
 */
class DefaultTaskPlanner(
    private val chatApiService: ChatApiService? = null,
    private val validator: TaskPlanValidator = TaskPlanValidator(),
    private val referenceResolver: ReferenceResolver = ReferenceResolver()
) : TaskPlanner {

    companion object {
        private const val TAG = "TaskPlanner"
    }

    override suspend fun plan(
        prompt: String,
        memoryContext: String,
        currentPackage: String,
        visibleScreenText: String,
        context: ConversationContext?
    ): PlanValidationResult {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) {
            return PlanValidationResult.Invalid("User prompt is empty.")
        }

        // 1. Check for ambiguous reference upfront
        if (context != null) {
            val resolved = referenceResolver.resolve(trimmed, context)
            if (resolved is ReferenceResolutionResult.Ambiguous) {
                val candidateLabels = resolved.candidates.joinToString(", ") { "\"${it.label}\"" }
                return PlanValidationResult.Invalid(
                    "Ambiguous reference: multiple elements match ($candidateLabels). Which one would you like?"
                )
            }
        }

        // 2. Attempt remote AI planning if service is available
        if (chatApiService != null) {
            try {
                val contextDto = context?.let { ctx ->
                    ContextDto(
                        currentApp = ctx.currentApp,
                        currentPackage = ctx.currentPackage,
                        screenSummary = ctx.currentScreenSummary,
                        currentTarget = ctx.currentTarget,
                        lastAction = ctx.lastAction,
                        recentEntities = ctx.recentEntities
                    )
                }

                val response = chatApiService.createTaskPlan(
                    PlanRequest(
                        prompt = trimmed,
                        memoryContext = memoryContext.ifBlank { null },
                        currentPackage = currentPackage.ifBlank { null },
                        visibleScreenText = visibleScreenText.ifBlank { null },
                        context = contextDto
                    )
                )

                val domainPlan = response.toDomain()
                val validation = validator.validate(domainPlan)
                if (validation is PlanValidationResult.Valid) {
                    Log.i(TAG, "Successfully generated and validated remote TaskPlan with ${domainPlan.steps.size} steps.")
                    return validation
                } else {
                    Log.w(TAG, "Remote TaskPlan failed validation: ${(validation as PlanValidationResult.Invalid).reason}. Falling back to local planner.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Remote planner failed (${e.message}), using local deterministic fallback.")
            }
        }

        // 3. Local deterministic fallback planner with context
        val localPlan = generateLocalPlan(trimmed, currentPackage, context)
        return validator.validate(localPlan)
    }

    private fun generateLocalPlan(prompt: String, currentPackage: String, context: ConversationContext? = null): TaskPlan {
        val lower = prompt.lowercase()
        val steps = mutableListOf<TaskStep>()

        // Check reference resolution first
        if (context != null) {
            val resolved = referenceResolver.resolve(prompt, context)
            if (resolved is ReferenceResolutionResult.Resolved) {
                val targetLabel = resolved.target.label
                val targetViewId = resolved.target.viewId
                val action = if (targetViewId != null) "click_view" else "click_text"
                val args = if (targetViewId != null) mapOf("viewId" to targetViewId) else mapOf("text" to targetLabel)

                steps.add(
                    TaskStep(
                        stepId = 1,
                        action = action,
                        arguments = args,
                        expectedResult = ExpectedResult(expectedText = targetLabel),
                        riskLevel = RiskLevel.LOW,
                        requiresConfirmation = false,
                        timeoutMs = 2000L,
                        retryCount = 1
                    )
                )

                return TaskPlan(
                    taskId = "local_plan_ref_${System.currentTimeMillis()}",
                    userRequest = prompt,
                    steps = steps,
                    estimatedRisk = RiskLevel.LOW,
                    requiresConfirmation = false
                )
            }
        }

        // Check contextual search intent (e.g. "search for Spring Boot" while in YouTube)
        if (lower.startsWith("search for ") || lower.startsWith("search ")) {
            val queryPart = if (lower.startsWith("search for ")) {
                prompt.substring(11).trim()
            } else {
                prompt.substring(7).trim()
            }

            val effectiveApp = context?.currentApp?.lowercase() ?: when {
                currentPackage.contains("youtube") -> "youtube"
                currentPackage.contains("chrome") -> "chrome"
                else -> null
            }

            if (effectiveApp == "youtube") {
                steps.add(
                    TaskStep(
                        stepId = 1,
                        action = "click_text",
                        arguments = mapOf("text" to "Search"),
                        expectedResult = ExpectedResult(expectedText = "Search"),
                        riskLevel = RiskLevel.LOW,
                        requiresConfirmation = false,
                        timeoutMs = 2000L,
                        retryCount = 1
                    )
                )
                steps.add(
                    TaskStep(
                        stepId = 2,
                        action = "type_text",
                        arguments = mapOf("text" to queryPart),
                        expectedResult = ExpectedResult(expectedText = queryPart),
                        riskLevel = RiskLevel.MEDIUM,
                        requiresConfirmation = true,
                        timeoutMs = 3000L,
                        retryCount = 1
                    )
                )
                steps.add(
                    TaskStep(
                        stepId = 3,
                        action = "wait_for_screen",
                        arguments = mapOf("expectedPackage" to "com.google.android.youtube", "timeoutMs" to 3000L),
                        expectedResult = ExpectedResult(expectedScreenState = "YouTube search results active"),
                        riskLevel = RiskLevel.LOW,
                        requiresConfirmation = false,
                        timeoutMs = 3000L,
                        retryCount = 1
                    )
                )

                return TaskPlan(
                    taskId = "local_plan_search_${System.currentTimeMillis()}",
                    userRequest = prompt,
                    steps = steps,
                    estimatedRisk = RiskLevel.MEDIUM,
                    requiresConfirmation = true
                )
            }
        }

        if (lower.startsWith("open ") && lower.contains(" and search for ")) {
            val openPart = lower.substring(5, lower.indexOf(" and search for ")).trim()
            val queryPart = prompt.substring(lower.indexOf(" and search for ") + 16).trim()

            val appName = when (openPart) {
                "youtube" -> "YouTube"
                "chrome" -> "Chrome"
                "settings" -> "Settings"
                else -> openPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            val pkgName = when (openPart) {
                "youtube" -> "com.google.android.youtube"
                "chrome" -> "com.android.chrome"
                "settings" -> "com.android.settings"
                else -> null
            }

            steps.add(
                TaskStep(
                    stepId = 1,
                    action = "open_app",
                    arguments = mapOf("appName" to appName),
                    expectedResult = if (pkgName != null) ExpectedResult(expectedPackage = pkgName) else null,
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 3000L,
                    retryCount = 1
                )
            )

            steps.add(
                TaskStep(
                    stepId = 2,
                    action = "wait_for_screen",
                    arguments = mapOf("expectedPackage" to (pkgName ?: ""), "timeoutMs" to TaskPlanValidator.APP_LAUNCH_WAIT_MS),
                    expectedResult = ExpectedResult(expectedScreenState = "$appName screen active"),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = TaskPlanValidator.APP_LAUNCH_WAIT_MS,
                    retryCount = 1
                )
            )

            steps.add(
                TaskStep(
                    stepId = 3,
                    action = "click_text",
                    arguments = mapOf("text" to "Search"),
                    expectedResult = ExpectedResult(expectedText = "Search"),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 2000L,
                    retryCount = 1
                )
            )

            steps.add(
                TaskStep(
                    stepId = 4,
                    action = "type_text",
                    arguments = mapOf("text" to queryPart),
                    expectedResult = ExpectedResult(expectedText = queryPart),
                    riskLevel = RiskLevel.MEDIUM,
                    requiresConfirmation = true,
                    timeoutMs = 3000L,
                    retryCount = 1
                )
            )
        } else if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val appName = prompt.substring(prompt.indexOf(' ') + 1).trim()
            steps.add(
                TaskStep(
                    stepId = 1,
                    action = "open_app",
                    arguments = mapOf("appName" to appName),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 3000L,
                    retryCount = 1
                )
            )
        } else if (lower.contains("what time") || lower.contains("time is it")) {
            steps.add(
                TaskStep(
                    stepId = 1,
                    action = "get_time",
                    arguments = emptyMap(),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 1000L,
                    retryCount = 0
                )
            )
        } else if (lower.contains("what is on my screen") || lower.contains("analyze my screen")) {
            steps.add(
                TaskStep(
                    stepId = 1,
                    action = "analyze_current_screen",
                    arguments = emptyMap(),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 5000L,
                    retryCount = 1
                )
            )
        } else {
            steps.add(
                TaskStep(
                    stepId = 1,
                    action = "read_visible_screen",
                    arguments = emptyMap(),
                    riskLevel = RiskLevel.LOW,
                    requiresConfirmation = false,
                    timeoutMs = 2000L,
                    retryCount = 0
                )
            )
        }

        val hasMediumOrHigh = steps.any { it.riskLevel != RiskLevel.LOW || it.requiresConfirmation }

        return TaskPlan(
            taskId = "local_plan_${System.currentTimeMillis()}",
            userRequest = prompt,
            steps = steps,
            estimatedRisk = if (hasMediumOrHigh) RiskLevel.MEDIUM else RiskLevel.LOW,
            requiresConfirmation = hasMediumOrHigh
        )
    }
}
