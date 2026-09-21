package com.jarvis.assistant.context

/**
 * Constants governing short-term conversational context limits, bounded memory retention, and expiration.
 * Enforces strict limits to prevent unbounded context growth and memory leaks.
 */
object ContextConstants {
    /** Maximum number of recent conversational turns retained in short-term context. */
    const val MAX_RECENT_TURNS: Int = 10

    /** Maximum number of detected or mentioned entities retained in context. */
    const val MAX_RECENT_ENTITIES: Int = 20

    /** Maximum character length of accumulated context text provided to planning/AI components. */
    const val MAX_CONTEXT_TEXT_LENGTH: Int = 4000

    /** Maximum age of short-term context before automatic lazy expiration (5 minutes = 300,000 ms). */
    const val CONTEXT_EXPIRY_MS: Long = 300_000L

    /** Maximum number of screen elements tracked in sanitized screen summary. */
    const val MAX_TRACKED_SCREEN_ELEMENTS: Int = 15
}
