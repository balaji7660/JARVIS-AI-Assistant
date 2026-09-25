package com.jarvis.assistant.context

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Manages continuous conversation sessions for JARVIS 2.0.
 * Allows the user to issue multiple back-to-back commands ("Search for jobs", "Open the first one")
 * without having to repeat "Hey Jarvis" on every single turn.
 *
 * Configurable inactivity timeout: default 45 seconds (within 30-60s range).
 * Automatically closes session after inactivity timeout and transitions to passive wake-word mode.
 */
class ContinuousConversationManager(
    val sessionTimeoutMs: Long = DEFAULT_SESSION_TIMEOUT_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val onSessionExpired: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "ContinuousConvManager"
        const val DEFAULT_SESSION_TIMEOUT_MS = 45_000L // 45 seconds
        const val MIN_SESSION_TIMEOUT_MS = 15_000L
        const val MAX_SESSION_TIMEOUT_MS = 60_000L
    }

    private var lastActivityTimeMs: Long = 0L
    @Volatile
    private var isSessionActive: Boolean = false

    private val handler: Handler? = try {
        val looper = Looper.getMainLooper()
        if (looper != null) Handler(looper) else null
    } catch (_: Throwable) {
        null
    }

    private val timeoutRunnable = Runnable {
        if (isSessionActive && timeProvider() - lastActivityTimeMs >= sessionTimeoutMs) {
            Log.i(TAG, "Continuous conversation session timed out after ${sessionTimeoutMs}ms")
            isSessionActive = false
            onSessionExpired?.invoke()
        }
    }

    fun startOrExtendSession() {
        lastActivityTimeMs = timeProvider()
        isSessionActive = true
        handler?.removeCallbacks(timeoutRunnable)
        handler?.postDelayed(timeoutRunnable, sessionTimeoutMs)
        Log.i(TAG, "Continuous conversation session active for ${sessionTimeoutMs}ms")
    }

    fun isSessionActive(): Boolean {
        if (!isSessionActive) return false
        val elapsed = timeProvider() - lastActivityTimeMs
        val stillActive = elapsed < sessionTimeoutMs
        if (!stillActive) {
            isSessionActive = false
        }
        return stillActive
    }

    fun endSession() {
        isSessionActive = false
        handler?.removeCallbacks(timeoutRunnable)
        Log.i(TAG, "Continuous conversation session ended.")
    }

    fun getRemainingSessionTimeMs(): Long {
        if (!isSessionActive) return 0L
        val remaining = sessionTimeoutMs - (timeProvider() - lastActivityTimeMs)
        return remaining.coerceAtLeast(0L)
    }

    /**
     * For unit testing: directly trigger timeout evaluation.
     */
    fun evaluateTimeout() {
        timeoutRunnable.run()
    }
}
