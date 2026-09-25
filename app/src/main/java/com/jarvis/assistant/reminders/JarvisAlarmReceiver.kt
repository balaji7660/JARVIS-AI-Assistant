package com.jarvis.assistant.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R

/**
 * Handles AlarmManager triggers for Reminders and Timers.
 * Shows high-priority heads-up notifications that survive app closure.
 */
class JarvisAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "JarvisAlarmReceiver"
        const val ACTION_ALARM_TRIGGERED = "com.jarvis.assistant.ACTION_ALARM_TRIGGERED"
        const val CHANNEL_ID = "jarvis_reminders_and_timers"
        const val CHANNEL_NAME = "JARVIS Reminders & Timers"

        const val EXTRA_TYPE = "extra_type" // "TIMER" or "REMINDER"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_ID = "extra_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive: action=${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Device reboot completed; alarm re-scheduling handled by manager.")
            return
        }

        if (intent.action == ACTION_ALARM_TRIGGERED) {
            val type = intent.getStringExtra(EXTRA_TYPE) ?: "REMINDER"
            val title = intent.getStringExtra(EXTRA_TITLE) ?: if (type == "TIMER") "Timer Finished!" else "JARVIS Reminder"
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "Your scheduled alarm has triggered."
            val id = intent.getIntExtra(EXTRA_ID, System.currentTimeMillis().toInt())

            showAlarmNotification(context, id, title, message)
        }
    }

    private fun showAlarmNotification(context: Context, notificationId: Int, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for JARVIS timers and scheduled reminders."
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
        Log.i(TAG, "Notification posted for alarm: $title - $message")
    }
}
