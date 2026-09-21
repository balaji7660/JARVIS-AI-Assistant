package com.jarvis.assistant.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object AccessibilityUtils {

    private const val TAG = "AccessibilityUtils"

    /**
     * Checks if JarvisAccessibilityService is currently enabled in Android Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // Fast path: if service instance is already running
        if (JarvisAccessibilityService.isRunning) {
            return true
        }

        val expectedComponent = ComponentName(context, JarvisAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        while (splitter.hasNext()) {
            val componentStr = splitter.next()
            val component = ComponentName.unflattenFromString(componentStr)
            if (component != null && component == expectedComponent) {
                return true
            }
        }

        return false
    }

    /**
     * Launches Android's native Accessibility Settings screen so the user can manually enable the service.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
        }
    }
}
