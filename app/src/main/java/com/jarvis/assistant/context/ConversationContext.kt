package com.jarvis.assistant.context

/**
 * Sanitized, lightweight representation of an interactive or visible screen element.
 * Never stores raw AccessibilityNodeInfo instances or sensitive credentials.
 */
data class ScreenElementContext(
    val label: String,
    val type: String = "element", // e.g. "button", "result", "input", "text", "link"
    val viewId: String? = null,
    val bounds: List<Int>? = null // [left, top, right, bottom]
)

/**
 * Represents a single turn in a conversational exchange.
 */
data class ConversationTurn(
    val role: String, // "user" or "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Bounded short-term conversational context.
 *
 * Tracks the current state of conversation, active foreground app, recent intents,
 * sanitized screen summary, and detected entities.
 *
 * Never stores raw AccessibilityNodeInfo or screenshots.
 * Exclusively maintained in-memory and lazily expires after [ContextConstants.CONTEXT_EXPIRY_MS].
 */
data class ConversationContext(
    val sessionId: String = "default_session",
    val currentApp: String? = null,
    val currentPackage: String? = null,
    val currentActivity: String? = null,
    val currentTaskId: String? = null,
    val currentTaskDescription: String? = null,
    val lastUserIntent: String? = null,
    val lastAssistantResponse: String? = null,
    val lastAction: String? = null,
    val lastActionResult: String? = null,
    val currentScreenSummary: String? = null,
    val detectedElements: List<ScreenElementContext> = emptyList(),
    val currentTarget: String? = null,
    val recentEntities: List<String> = emptyList(),
    val recentTurns: List<ConversationTurn> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Checks if this short-term context has expired based on [ContextConstants.CONTEXT_EXPIRY_MS].
     */
    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
        return (currentTime - timestamp) > ContextConstants.CONTEXT_EXPIRY_MS
    }

    /**
     * Produces a concise, bounded textual summary of the active context suitable for prompt injection.
     * Enforces the [ContextConstants.MAX_CONTEXT_TEXT_LENGTH] cap.
     */
    fun toBoundedSummary(): String {
        val builder = StringBuilder()

        currentApp?.let { builder.append("Current App: $it\n") }
        currentPackage?.let { builder.append("Current Package: $it\n") }
        currentTarget?.let { builder.append("Current Target: $it\n") }
        lastAction?.let { builder.append("Last Action: $it\n") }
        lastActionResult?.let { builder.append("Last Result: $it\n") }
        currentScreenSummary?.let { builder.append("Screen Summary: $it\n") }

        if (recentEntities.isNotEmpty()) {
            builder.append("Recent Entities: ${recentEntities.take(ContextConstants.MAX_RECENT_ENTITIES).joinToString(", ")}\n")
        }

        if (recentTurns.isNotEmpty()) {
            builder.append("Recent Conversation:\n")
            recentTurns.takeLast(ContextConstants.MAX_RECENT_TURNS).forEach { turn ->
                builder.append("- ${turn.role.uppercase()}: ${turn.text}\n")
            }
        }

        val fullText = builder.toString().trim()
        return if (fullText.length > ContextConstants.MAX_CONTEXT_TEXT_LENGTH) {
            fullText.take(ContextConstants.MAX_CONTEXT_TEXT_LENGTH) + "..."
        } else {
            fullText
        }
    }
}
