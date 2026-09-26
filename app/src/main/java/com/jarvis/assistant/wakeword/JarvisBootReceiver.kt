package com.jarvis.assistant.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered on BOOT_COMPLETED.
 * Restores JarvisWakeWordService as a foreground service IF AND ONLY IF
 * the user has explicitly enabled background wake word in preferences.
 */
class JarvisBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JarvisBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val isEnabled = WakeWordPreferences.isBackgroundWakeEnabled(context)
        Log.i(TAG, "Device boot completed. Background JARVIS enabled preference: $isEnabled")

        if (isEnabled) {
            try {
                JarvisWakeWordService.startService(context)
                Log.i(TAG, "Successfully restored JarvisWakeWordService on boot.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore JarvisWakeWordService on boot", t)
            }
        } else {
            Log.d(TAG, "Background JARVIS was not enabled by user; microphone remains off.")
        }
    }
}
