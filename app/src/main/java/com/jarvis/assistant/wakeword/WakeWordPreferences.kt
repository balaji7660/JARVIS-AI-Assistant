package com.jarvis.assistant.wakeword

import android.content.Context
import android.content.SharedPreferences

/**
 * Authoritative local persistence for Background JARVIS settings and diagnostic metrics.
 * Ensures the user's background wake preference survives app closure and device reboot.
 * Provides resilient in-memory fallback for headless test environments.
 */
object WakeWordPreferences {

    private const val PREFS_NAME = "jarvis_wake_prefs"

    private const val KEY_BACKGROUND_WAKE_ENABLED = "key_background_wake_enabled"
    private const val KEY_LAST_WAKE_TIMESTAMP = "key_last_wake_timestamp"
    private const val KEY_LAST_SPEECH_RESULT_TIMESTAMP = "key_last_speech_result_timestamp"
    private const val KEY_LAST_SPEECH_ERROR = "key_last_speech_error"
    private const val KEY_LAST_RECOVERY_TIMESTAMP = "key_last_recovery_timestamp"
    private const val KEY_SERVICE_START_COUNT = "key_service_start_count"
    private const val KEY_SERVICE_RECOVERY_COUNT = "key_service_recovery_count"

    private val memoryFallback = mutableMapOf<String, Any>()

    fun resetForTesting() {
        memoryFallback.clear()
    }

    private fun getPrefs(context: Context?): SharedPreferences? {
        if (context == null) return null
        return try {
            val ctx = context.applicationContext ?: context
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    fun isBackgroundWakeEnabled(context: Context?): Boolean {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getBoolean(KEY_BACKGROUND_WAKE_ENABLED, false)
        } else {
            (memoryFallback[KEY_BACKGROUND_WAKE_ENABLED] as? Boolean) ?: false
        }
    }

    fun setBackgroundWakeEnabled(context: Context?, enabled: Boolean) {
        memoryFallback[KEY_BACKGROUND_WAKE_ENABLED] = enabled
        getPrefs(context)?.edit()?.putBoolean(KEY_BACKGROUND_WAKE_ENABLED, enabled)?.apply()
    }

    fun recordWakeDetected(context: Context?, timestamp: Long = System.currentTimeMillis()) {
        memoryFallback[KEY_LAST_WAKE_TIMESTAMP] = timestamp
        getPrefs(context)?.edit()?.putLong(KEY_LAST_WAKE_TIMESTAMP, timestamp)?.apply()
    }

    fun getLastWakeTimestamp(context: Context?): Long {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getLong(KEY_LAST_WAKE_TIMESTAMP, 0L)
        } else {
            (memoryFallback[KEY_LAST_WAKE_TIMESTAMP] as? Long) ?: 0L
        }
    }

    fun recordSpeechResult(context: Context?, timestamp: Long = System.currentTimeMillis()) {
        memoryFallback[KEY_LAST_SPEECH_RESULT_TIMESTAMP] = timestamp
        getPrefs(context)?.edit()?.putLong(KEY_LAST_SPEECH_RESULT_TIMESTAMP, timestamp)?.apply()
    }

    fun getLastSpeechResultTimestamp(context: Context?): Long {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getLong(KEY_LAST_SPEECH_RESULT_TIMESTAMP, 0L)
        } else {
            (memoryFallback[KEY_LAST_SPEECH_RESULT_TIMESTAMP] as? Long) ?: 0L
        }
    }

    fun recordSpeechError(context: Context?, error: String) {
        memoryFallback[KEY_LAST_SPEECH_ERROR] = error
        getPrefs(context)?.edit()?.putString(KEY_LAST_SPEECH_ERROR, error)?.apply()
    }

    fun getLastSpeechError(context: Context?): String? {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getString(KEY_LAST_SPEECH_ERROR, null)
        } else {
            memoryFallback[KEY_LAST_SPEECH_ERROR] as? String
        }
    }

    fun recordRecoveryAttempt(context: Context?, timestamp: Long = System.currentTimeMillis()) {
        val currentRecoveryCount = getServiceRecoveryCount(context)
        memoryFallback[KEY_LAST_RECOVERY_TIMESTAMP] = timestamp
        memoryFallback[KEY_SERVICE_RECOVERY_COUNT] = currentRecoveryCount + 1
        getPrefs(context)?.edit()
            ?.putLong(KEY_LAST_RECOVERY_TIMESTAMP, timestamp)
            ?.putInt(KEY_SERVICE_RECOVERY_COUNT, currentRecoveryCount + 1)
            ?.apply()
    }

    fun getLastRecoveryTimestamp(context: Context?): Long {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getLong(KEY_LAST_RECOVERY_TIMESTAMP, 0L)
        } else {
            (memoryFallback[KEY_LAST_RECOVERY_TIMESTAMP] as? Long) ?: 0L
        }
    }

    fun incrementServiceStartCount(context: Context?): Int {
        val count = getServiceStartCount(context) + 1
        memoryFallback[KEY_SERVICE_START_COUNT] = count
        getPrefs(context)?.edit()?.putInt(KEY_SERVICE_START_COUNT, count)?.apply()
        return count
    }

    fun getServiceStartCount(context: Context?): Int {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getInt(KEY_SERVICE_START_COUNT, 0)
        } else {
            (memoryFallback[KEY_SERVICE_START_COUNT] as? Int) ?: 0
        }
    }

    fun getServiceRecoveryCount(context: Context?): Int {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            prefs.getInt(KEY_SERVICE_RECOVERY_COUNT, 0)
        } else {
            (memoryFallback[KEY_SERVICE_RECOVERY_COUNT] as? Int) ?: 0
        }
    }
}
