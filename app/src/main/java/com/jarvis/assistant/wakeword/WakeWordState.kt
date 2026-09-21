package com.jarvis.assistant.wakeword

/**
 * State machine representing the operational states of the local wake-word detector.
 */
enum class WakeWordState(val displayName: String) {
    DISABLED("WAKE WORD OFF"),
    STARTING("WAKE WORD STARTING"),
    LISTENING("LISTENING LOCALLY"),
    WAKE_DETECTED("WAKE WORD DETECTED"),
    TRANSITIONING("TRANSITIONING"),
    COMMAND_LISTENING("LISTENING FOR COMMAND"),
    ERROR("WAKE WORD ERROR")
}
