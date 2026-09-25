package com.jarvis.assistant.planner

import com.jarvis.assistant.tools.RiskLevel

/**
 * Result of plan validation.
 */
sealed class PlanValidationResult {
    data class Valid(val sanitizedPlan: TaskPlan) : PlanValidationResult()
    data class Invalid(val reason: String, val violatingStepId: Int? = null) : PlanValidationResult()
}

/**
 * Validates AI-proposed TaskPlans against strict system bounds, safety rules, and security policies.
 */
class TaskPlanValidator {

    companion object {
        const val MAX_PLAN_STEPS = 12
        const val MAX_STEP_TIMEOUT_MS = 8000L
        const val MIN_STEP_TIMEOUT_MS = 500L
        const val APP_LAUNCH_WAIT_MS = 7000L
        const val SCREEN_SYNC_WAIT_MS = 7000L
        const val ACTION_EXECUTION_TIMEOUT_MS = 5000L
        const val MAX_TOTAL_TASK_TIMEOUT_MS = 60000L
        const val MAX_RETRIES_PER_STEP = 2

        val ALLOWED_TOOLS = setOf(
            "get_time",
            "get_date",
            "go_home",
            "press_back",
            "open_url",
            "open_app",
            "read_visible_screen",
            "click_text",
            "click_view",
            "type_text",
            "scroll",
            "analyze_current_screen",
            "wait_for_screen",
            "call_contact",
            "get_battery_status",
            "set_volume",
            "get_volume",
            "set_brightness",
            "get_brightness",
            "toggle_flashlight",
            "open_camera",
            "get_device_info",
            "get_network_status",
            "open_wifi_settings",
            "open_bluetooth_settings",
            "lock_screen",
            "play_media",
            "pause_media",
            "resume_media",
            "next_track",
            "previous_track",
            "get_media_state",
            "send_sms",
            // Phase 6: Screen Assistant
            "find_screen_element",
            "read_current_screen",
            "diagnose_screen_error",
            "click_screen_element",
            "scroll_screen",
            // Phase 7: Web Assistant
            "search_web",
            "search_youtube",
            "read_current_webpage",
            "summarize_webpage",
            // Phase 8: Local Notes
            "create_note",
            "search_notes",
            "list_notes",
            "update_note",
            "delete_note",
            // Phase 9: Reminders and Timers
            "create_timer",
            "cancel_timer",
            "list_timers",
            "create_reminder",
            "cancel_reminder",
            "list_reminders",
            // Phase 10: Controlled Calendar
            "create_calendar_event",
            "list_calendar_events",
            "delete_calendar_event"
        )

        private val DANGEROUS_COMMAND_PATTERNS = listOf(
            "rm -rf", "su ", "sudo", "reboot", "format",
            "system/bin", "chmod", "setprop", "sh ", "bash "
        )

        private val CREDENTIAL_PATTERNS = listOf(
            "password", "passwd", "pin code", "otp", "one-time", "secret",
            "credit card", "cvv", "security code", "passcode"
        )
    }

    /**
     * Validates the provided plan and returns a sanitized valid plan or an invalid rejection reason.
     */
    fun validate(plan: TaskPlan): PlanValidationResult {
        // 1. Step Count Bounds
        if (plan.steps.isEmpty()) {
            return PlanValidationResult.Invalid("TaskPlan contains 0 steps.")
        }
        if (plan.steps.size > MAX_PLAN_STEPS) {
            return PlanValidationResult.Invalid("TaskPlan exceeds maximum step limit ($MAX_PLAN_STEPS steps).")
        }

        // 2. Validate overall task estimated duration
        val totalEstimatedTimeout = plan.steps.sumOf { it.timeoutMs * (it.retryCount + 1) }
        if (totalEstimatedTimeout > MAX_TOTAL_TASK_TIMEOUT_MS) {
            return PlanValidationResult.Invalid("Total task timeout exceeds safe execution limit ($MAX_TOTAL_TASK_TIMEOUT_MS ms).")
        }

        val sanitizedSteps = mutableListOf<TaskStep>()
        var requiresConfirmationOverall = false
        var maxRisk = RiskLevel.LOW

        for (step in plan.steps) {
            // 3. Tool Allowed List
            if (!ALLOWED_TOOLS.contains(step.action)) {
                return PlanValidationResult.Invalid(
                    reason = "Tool '${step.action}' is not in approved tool registry.",
                    violatingStepId = step.stepId
                )
            }

            // 4. Argument Security & Sanitization
            val argValidation = validateArguments(step.action, step.arguments)
            if (!argValidation.isValid) {
                return PlanValidationResult.Invalid(
                    reason = argValidation.reason ?: "Invalid arguments for action ${step.action}",
                    violatingStepId = step.stepId
                )
            }

            // 5. Enforce risk level and confirmation rules
            val isTyping = step.action == "type_text"
            val effectiveRisk = when {
                isTyping -> RiskLevel.MEDIUM
                step.riskLevel == RiskLevel.HIGH -> RiskLevel.HIGH
                step.riskLevel == RiskLevel.MEDIUM -> RiskLevel.MEDIUM
                else -> RiskLevel.LOW
            }

            val effectiveRequiresConfirmation = if (effectiveRisk != RiskLevel.LOW) {
                true
            } else {
                step.requiresConfirmation
            }

            if (effectiveRequiresConfirmation) {
                requiresConfirmationOverall = true
            }

            if (effectiveRisk > maxRisk) {
                maxRisk = effectiveRisk
            }

            val sanitizedTimeout = step.timeoutMs.coerceIn(MIN_STEP_TIMEOUT_MS, MAX_STEP_TIMEOUT_MS)
            val sanitizedRetries = step.retryCount.coerceIn(0, MAX_RETRIES_PER_STEP)

            sanitizedSteps.add(
                step.copy(
                    riskLevel = effectiveRisk,
                    requiresConfirmation = effectiveRequiresConfirmation,
                    timeoutMs = sanitizedTimeout,
                    retryCount = sanitizedRetries
                )
            )
        }

        val sanitizedPlan = plan.copy(
            steps = sanitizedSteps,
            estimatedRisk = maxRisk,
            requiresConfirmation = requiresConfirmationOverall
        )

        return PlanValidationResult.Valid(sanitizedPlan)
    }

    private data class ArgValidation(val isValid: Boolean, val reason: String? = null)

    private fun validateArguments(action: String, args: Map<String, Any?>): ArgValidation {
        // Inspect all argument strings for dangerous system or credential patterns
        for ((key, value) in args) {
            if (value is String) {
                val lower = value.lowercase()

                // Check dangerous shell injection patterns
                for (pattern in DANGEROUS_COMMAND_PATTERNS) {
                    if (lower.contains(pattern)) {
                        return ArgValidation(false, "Dangerous pattern '$pattern' detected in argument '$key'.")
                    }
                }

                // Check sensitive credential patterns in text/input
                if (action == "type_text" || key == "text") {
                    for (cred in CREDENTIAL_PATTERNS) {
                        if (lower.contains(cred)) {
                            return ArgValidation(false, "Automating sensitive credentials ('$cred') is strictly forbidden.")
                        }
                    }
                }
            }
        }

        // Action-specific argument checks
        when (action) {
            "open_url" -> {
                val url = args["url"] as? String
                if (url.isNullOrBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    return ArgValidation(false, "open_url requires valid http:// or https:// URL.")
                }
            }
            "open_app" -> {
                val appName = args["appName"] as? String
                if (appName.isNullOrBlank()) {
                    return ArgValidation(false, "open_app requires non-empty 'appName' argument.")
                }
            }
            "click_text" -> {
                val text = args["text"] as? String
                if (text.isNullOrBlank()) {
                    return ArgValidation(false, "click_text requires non-empty 'text' argument.")
                }
            }
            "click_view" -> {
                val viewId = args["viewId"] as? String
                if (viewId.isNullOrBlank()) {
                    return ArgValidation(false, "click_view requires non-empty 'viewId' argument.")
                }
            }
            "type_text" -> {
                val text = args["text"] as? String
                if (text == null) {
                    return ArgValidation(false, "type_text requires 'text' argument.")
                }
            }
            "scroll" -> {
                val direction = (args["direction"] as? String)?.lowercase()
                if (direction != "forward" && direction != "backward") {
                    return ArgValidation(false, "scroll requires direction 'forward' or 'backward'.")
                }
            }
            "wait_for_screen" -> {
                val timeout = args["timeoutMs"]
                if (timeout != null && timeout !is Number) {
                    return ArgValidation(false, "wait_for_screen 'timeoutMs' must be a numeric value.")
                }
            }
        }

        return ArgValidation(true)
    }
}
