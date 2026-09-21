package com.jarvis.assistant.wakeword

/**
 * Callback listener invoked when the wake phrase ("Hey JARVIS") is recognized locally.
 */
interface WakeWordListener {
    fun onWakeWordDetected()
    fun onWakeWordError(error: String) {}
}

/**
 * Modular architectural abstraction for on-device wake-word detection.
 * Decoupled from specific wake-word ML models or third-party engines.
 * Operates purely locally without streaming audio to cloud services.
 */
interface WakeWordDetector {

    val isListening: Boolean

    fun start()

    fun stop()

    fun setListener(listener: WakeWordListener?)
}
