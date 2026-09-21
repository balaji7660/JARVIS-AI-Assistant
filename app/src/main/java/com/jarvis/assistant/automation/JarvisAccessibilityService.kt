package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisAccessService"

        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun getInstance(): JarvisAccessibilityService? = instance

        val isRunning: Boolean get() = instance != null
    }

    @Volatile
    var currentPackageName: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "JarvisAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank()) {
                currentPackageName = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "JarvisAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "JarvisAccessibilityService destroyed.")
    }

    fun getRootInActiveWindowSafe(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve rootInActiveWindow", e)
            null
        }
    }

    fun performGlobalActionSafe(action: Int): Boolean {
        return try {
            performGlobalAction(action)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform global action $action", e)
            false
        }
    }
}
