package com.jarvis.assistant.ui.home

/**
 * Authoritative assistant state machine for JARVIS 2.0.
 * Controls voice pipeline, UI overlay, and conversational task flow.
 */
enum class AssistantState(val displayName: String) {
    IDLE("IDLE"),
    PASSIVE_WAKE("PASSIVE WAKE"),
    WAKE_LISTENING("WAKE LISTENING"),
    ACTIVE_LISTENING("ACTIVE LISTENING"),
    LISTENING("LISTENING"),
    PROCESSING("PROCESSING"),
    THINKING("THINKING"),
    EXECUTING("EXECUTING"),
    SPEAKING("SPEAKING"),
    WAITING_FOR_CONFIRMATION("WAITING FOR CONFIRMATION"),
    WAITING_FOR_CLARIFICATION("WAITING FOR CLARIFICATION"),
    ERROR("ERROR");

    val isListening: Boolean
        get() = this == ACTIVE_LISTENING || this == LISTENING || this == WAKE_LISTENING

    val isExecuting: Boolean
        get() = this == EXECUTING || this == PROCESSING || this == THINKING
}
