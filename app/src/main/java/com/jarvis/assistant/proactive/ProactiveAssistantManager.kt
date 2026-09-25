package com.jarvis.assistant.proactive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R

/**
 * Event types for proactive notifications in JARVIS 2.0.
 */
enum class ProactiveEventType {
    LOW_BATTERY,
    REMINDER,
    CALENDAR_EVENT,
    TASK_FAILURE,
    TASK_COMPLETION
}

data class ProactiveAlert(
    val type: ProactiveEventType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ProactiveAssistantManager coordinates optional, privacy-safe notifications.
 *
 * Privacy Guarantees:
 * - Strictly opt-in by the user (default: disabled).
 * - NEVER activates the microphone secretly.
 * - NEVER captures the screen continuously or in the background.
 * - NEVER sends unsolicited private data to remote servers.
 */
class ProactiveAssistantManager(
    private val context: Context? = null,
    private val prefs: SharedPreferences? = null
) {

    companion object {
        private const val TAG = "ProactiveAssistant"
        const val PREFS_KEY_PROACTIVE_ENABLED = "proactive_mode_enabled"
        const val CHANNEL_ID = "jarvis_proactive"
        const val CHANNEL_NAME = "JARVIS Proactive Assistant"
        private const val BATTERY_ALERT_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes
    }

    private var lastBatteryAlertTime = 0L
    private var inMemoryEnabled: Boolean = false

    fun isProactiveEnabled(): Boolean {
        return prefs?.getBoolean(PREFS_KEY_PROACTIVE_ENABLED, inMemoryEnabled) ?: inMemoryEnabled
    }

    fun setProactiveEnabled(enabled: Boolean) {
        inMemoryEnabled = enabled
        prefs?.edit()?.putBoolean(PREFS_KEY_PROACTIVE_ENABLED, enabled)?.apply()
        Log.i(TAG, "Proactive Assistant mode updated: enabled=$enabled")
    }

    /**
     * Checks battery level and returns an alert if battery is critically low (<= 15%) and not charging.
     */
    fun evaluateBattery(
        batteryPercent: Int,
        isCharging: Boolean,
        currentTime: Long = System.currentTimeMillis()
    ): ProactiveAlert? {
        if (!isProactiveEnabled()) return null
        if (isCharging) return null

        if (batteryPercent <= 15 && (currentTime - lastBatteryAlertTime > BATTERY_ALERT_COOLDOWN_MS)) {
            lastBatteryAlertTime = currentTime
            return ProactiveAlert(
                type = ProactiveEventType.LOW_BATTERY,
                title = "JARVIS Battery Advisory",
                message = "Boss, your battery is at $batteryPercent%. You may want to connect the charger."
            )
        }
        return null
    }

    /**
     * Alerts for due reminders.
     */
    fun evaluateReminder(reminderTitle: String): ProactiveAlert? {
        if (!isProactiveEnabled()) return null
        return ProactiveAlert(
            type = ProactiveEventType.REMINDER,
            title = "JARVIS Reminder",
            message = "Boss, your $reminderTitle reminder is due."
        )
    }

    /**
     * Alerts for upcoming calendar events (e.g. 15 minutes before).
     */
    fun evaluateCalendarEvent(eventTitle: String, minutesUntil: Int): ProactiveAlert? {
        if (!isProactiveEnabled()) return null
        return ProactiveAlert(
            type = ProactiveEventType.CALENDAR_EVENT,
            title = "JARVIS Calendar Alert",
            message = "You have a calendar event '$eventTitle' in $minutesUntil minutes, boss."
        )
    }

    /**
     * Alerts when an automation task encounters a recoverable failure.
     */
    fun evaluateTaskFailure(taskDescription: String, failureReason: String): ProactiveAlert? {
        if (!isProactiveEnabled()) return null
        val clean = failureReason.removePrefix("Error:").removePrefix("Failed:").trim()
        return ProactiveAlert(
            type = ProactiveEventType.TASK_FAILURE,
            title = "JARVIS Task Notice",
            message = "$clean Want me to retry, boss?"
        )
    }

    /**
     * Displays a system notification if context is available.
     */
    fun postNotification(alert: ProactiveAlert) {
        val ctx = context ?: return
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Proactive suggestions and reminders from JARVIS"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx,
            alert.type.ordinal,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(alert.type.ordinal + 100, notification)
        Log.i(TAG, "Posted proactive notification: ${alert.title} - ${alert.message}")
    }
}
