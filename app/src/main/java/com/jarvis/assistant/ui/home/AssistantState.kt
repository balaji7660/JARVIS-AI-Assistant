package com.jarvis.assistant.ui.home

enum class AssistantState(val displayName: String) {
    IDLE("IDLE"),
    WAKE_LISTENING("WAKE LISTENING"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    EXECUTING("EXECUTING"),
    SPEAKING("SPEAKING"),
    ERROR("ERROR")
}
