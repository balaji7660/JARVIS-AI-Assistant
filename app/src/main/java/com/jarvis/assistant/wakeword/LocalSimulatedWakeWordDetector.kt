package com.jarvis.assistant.wakeword

import android.util.Log

/**
 * Local implementation of WakeWordDetector.
 * Operates purely locally on the client without sending any microphone data to external cloud servers.
 * Implements the architecture abstraction and allows deterministic testing / simulation in emulator environments.
 */
class LocalSimulatedWakeWordDetector(
    private val context: Any? = null
) : WakeWordDetector {

    companion object {
        private const val TAG = "WakeWordDetector"
    }

    private var listener: WakeWordListener? = null
    private var _isListening: Boolean = false

    override val isListening: Boolean
        get() = _isListening

    override fun start() {
        if (_isListening) return
        _isListening = true
        Log.i(TAG, "Local wake-word detector started. Listening locally for \"Hey JARVIS\"...")
    }

    override fun stop() {
        if (!_isListening) return
        _isListening = false
        Log.i(TAG, "Local wake-word detector stopped.")
    }

    override fun setListener(listener: WakeWordListener?) {
        this.listener = listener
    }

    /**
     * Programmatically or locally triggers wake word detection.
     */
    fun simulateWakeWordDetected() {
        if (_isListening) {
            Log.i(TAG, "Wake word \"Hey JARVIS\" detected locally!")
            listener?.onWakeWordDetected()
        } else {
            Log.w(TAG, "Wake word trigger ignored because detector is not listening.")
        }
    }

    fun simulateDetection() {
        simulateWakeWordDetected()
    }

    fun simulateError(error: String) {
        listener?.onWakeWordError(error)
    }
}
