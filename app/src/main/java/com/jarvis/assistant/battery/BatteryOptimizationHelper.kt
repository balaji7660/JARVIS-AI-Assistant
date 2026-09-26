package com.jarvis.assistant.battery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper to inspect and request battery optimization exemptions using official Android intents only.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptHelper"

    /**
     * Checks if the app is currently exempt from Android Doze and battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            Log.d(TAG, "isIgnoringBatteryOptimizations: $isIgnoring")
            return isIgnoring
        }
        return true
    }

    /**
     * Creates an official system intent to prompt the user to allow unrestricted background execution.
     */
    fun createRequestIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS failed, falling back to settings", e)
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }
}
