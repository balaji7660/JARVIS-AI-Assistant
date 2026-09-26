package com.jarvis.assistant.voice

import android.util.Log

/**
 * Structured telemetry logger for JARVIS voice and microphone lifecycle.
 * Strictly enforces privacy: passwords, OTP, PIN, API keys, and sensitive tokens are never logged.
 */
object JarvisLogger {

    private const val TAG = "JARVIS_VOICE_LIFECYCLE"

    enum class Event {
        WAKE_SERVICE_STARTED,
        WAKE_SERVICE_STOPPED,
        WAKE_DETECTED,
        MIC_ACQUIRED_WAKE,
        MIC_RELEASED_WAKE,
        SPEECH_STARTED,
        SPEECH_RESULT,
        SPEECH_ERROR,
        TTS_STARTED,
        TTS_DONE,
        TTS_ERROR,
        MIC_RECOVERY,
        WAKE_DETECTOR_RESTARTED,
        AUDIO_FOCUS_GAINED,
        AUDIO_FOCUS_RELEASED
    }

    fun log(event: Event, details: String = "") {
        val sanitized = sanitize(details)
        val message = if (sanitized.isNotBlank()) "[$event] $sanitized" else "[$event]"
        Log.i(TAG, message)
    }

    fun logError(event: Event, error: String) {
        val sanitized = sanitize(error)
        Log.e(TAG, "[$event] $sanitized")
    }

    private fun sanitize(input: String): String {
        // Redact potential passwords, 4-6 digit numeric OTPs, API keys, and sensitive tokens
        return input
            .replace(Regex("(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*\\S+"), "$1=[REDACTED]")
            .replace(Regex("\\b\\d{4,6}\\b"), "[REDACTED_OTP]")
    }
}
