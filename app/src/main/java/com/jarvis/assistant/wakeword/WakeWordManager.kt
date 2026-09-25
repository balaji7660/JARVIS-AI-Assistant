package com.jarvis.assistant.wakeword

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authoritative singleton managing the local wake-word detector lifecycle.
 * Prevents multiple AudioRecord allocations and coordinates foreground service execution.
 */
object WakeWordManager {

    private const val TAG = "WakeWordManager"

    @Volatile
    private var detectorInstance: LocalWakeWordDetector? = null

    @Volatile
    var isActivityVisible: Boolean = false

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    @Synchronized
    fun getInstance(context: Context): LocalWakeWordDetector {
        return detectorInstance ?: synchronized(this) {
            detectorInstance ?: LocalWakeWordDetector(context.applicationContext).also {
                detectorInstance = it
            }
        }
    }

    @Synchronized
    fun setCustomDetector(detector: LocalWakeWordDetector?) {
        detectorInstance = detector
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
        Log.d(TAG, "WakeWord service active state changed to: $active")
    }

    fun suppressDuringSpeech(speaking: Boolean) {
        detectorInstance?.suppressDuringSpeech(speaking)
    }

    fun notifyCommandListeningStarted() {
        detectorInstance?.notifyCommandListeningStarted()
    }

    fun notifyCommandFinished() {
        detectorInstance?.notifyCommandFinished()
    }

    fun addListener(listener: WakeWordListener) {
        detectorInstance?.addListener(listener)
    }

    fun removeListener(listener: WakeWordListener) {
        detectorInstance?.removeListener(listener)
    }

    fun stop() {
        detectorInstance?.stop()
    }
}
