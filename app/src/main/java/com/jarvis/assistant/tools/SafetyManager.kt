package com.jarvis.assistant.tools

import android.util.Log

/**
 * Safety decision outcome for a requested tool call.
 */
sealed class SafetyDecision {
    data class Allowed(val riskLevel: RiskLevel) : SafetyDecision()
    data class RequiresConfirmation(val riskLevel: RiskLevel, val reason: String) : SafetyDecision()
    data class Denied(val reason: String) : SafetyDecision()
}

/**
 * Safety Manager responsible for evaluating every tool call before execution.
 * Enforces risk policies:
 * - LOW: Automatically approved for execution.
 * - MEDIUM: Triggers confirmation architecture requirement unless explicitly confirmed by user.
 * - HIGH: Strictly requires explicit user confirmation and execution constraints.
 */
class SafetyManager {

    companion object {
        private const val TAG = "SafetyManager"
    }

    /**
     * Evaluates a tool and its arguments against safety policies.
     * @param tool The target tool
     * @param arguments Arguments map
     * @param isUserConfirmed Whether the user explicitly tapped Confirm in the UI confirmation dialog
     */
    fun evaluate(
        tool: JarvisTool,
        arguments: Map<String, Any?>,
        isUserConfirmed: Boolean = false
    ): SafetyDecision {
        Log.i(TAG, "[SAFETY_CHECK] Evaluating tool '${tool.name}' with risk level ${tool.riskLevel} (UserConfirmed: $isUserConfirmed)")

        // 1. Validate argument safety (no command injection or malicious shell tokens)
        for ((key, value) in arguments) {
            val strVal = value?.toString() ?: ""
            if (strVal.contains("rm -rf") || strVal.contains("; ") || strVal.contains("&&") || strVal.contains("||")) {
                val reason = "Argument '$key' contains potentially dangerous syntax."
                Log.w(TAG, "[SAFETY_CHECK] Denied: $reason")
                return SafetyDecision.Denied(reason)
            }
        }

        // 2. Specialized checks for TypeText
        if (tool.name == "type_text") {
            val text = arguments["text"]?.toString() ?: ""
            val lower = text.lowercase()
            if (lower.contains("password") && (lower.contains("is") || lower.contains("="))) {
                val reason = "Input contains potential password pattern."
                Log.w(TAG, "[SAFETY_CHECK] Denied: $reason")
                return SafetyDecision.Denied(reason)
            }
        }

        // 3. Risk-level policy enforcement
        return when (tool.riskLevel) {
            RiskLevel.LOW -> {
                SafetyDecision.Allowed(RiskLevel.LOW)
            }
            RiskLevel.MEDIUM -> {
                if (isUserConfirmed) {
                    Log.i(TAG, "[SAFETY_CHECK] MEDIUM risk tool approved via explicit user confirmation.")
                    SafetyDecision.Allowed(RiskLevel.MEDIUM)
                } else {
                    val desc = if (tool.name == "type_text") {
                        "Type \"${arguments["text"]}\" into the current field"
                    } else {
                        "Execute ${tool.name}"
                    }
                    SafetyDecision.RequiresConfirmation(
                        riskLevel = RiskLevel.MEDIUM,
                        reason = desc
                    )
                }
            }
            RiskLevel.HIGH -> {
                if (isUserConfirmed) {
                    SafetyDecision.Allowed(RiskLevel.HIGH)
                } else {
                    SafetyDecision.RequiresConfirmation(
                        riskLevel = RiskLevel.HIGH,
                        reason = "Tool '${tool.name}' has high risk and requires explicit user consent."
                    )
                }
            }
        }
    }

    /**
     * Validates AI-provided coordinate bounds against physical screen dimensions.
     * Prevents arbitrary or out-of-bounds execution.
     */
    fun validateCoordinates(bounds: List<Int>, screenWidth: Int, screenHeight: Int): Boolean {
        if (bounds.size != 4) return false
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        return left in 0..screenWidth &&
                right in 0..screenWidth &&
                top in 0..screenHeight &&
                bottom in 0..screenHeight &&
                left < right && top < bottom
    }
}
