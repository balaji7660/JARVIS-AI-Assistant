package com.jarvis.assistant.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jarvis.assistant.memory.ReminderDao
import com.jarvis.assistant.memory.ReminderEntity
import com.jarvis.assistant.memory.TimerEntity

/**
 * Native Android timer and reminder management with Room persistence and AlarmManager scheduling.
 */
class TimerReminderManager(
    private val context: Context,
    private val reminderDao: ReminderDao
) {

    companion object {
        private const val TAG = "TimerReminderManager"
    }

    private val alarmManager: AlarmManager? = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    suspend fun setTimer(durationSeconds: Long, label: String = "Timer"): Long {
        val safeDuration = durationSeconds.coerceAtLeast(1L)
        val now = System.currentTimeMillis()
        val endTimeMs = now + (safeDuration * 1000L)

        val entity = TimerEntity(
            label = label.trim().ifBlank { "Timer" },
            durationSeconds = safeDuration,
            endTimeMs = endTimeMs
        )
        val id = reminderDao.insertTimer(entity)

        scheduleAlarm(
            id = id.toInt(),
            triggerAtMs = endTimeMs,
            type = "TIMER",
            title = "Timer Finished!",
            message = "$label (${formatDuration(safeDuration)}) is done."
        )

        Log.i(TAG, "Scheduled timer id=$id for ${safeDuration}s")
        return id
    }

    suspend fun cancelTimer(timerId: Long?, label: String?): Boolean {
        val active = reminderDao.getActiveTimers()
        val target: TimerEntity? = if (timerId != null) {
            active.firstOrNull { it.id == timerId }
        } else if (!label.isNullOrBlank()) {
            active.firstOrNull { it.label.contains(label, ignoreCase = true) }
        } else {
            active.firstOrNull()
        }

        if (target == null) return false

        cancelAlarm(target.id.toInt(), "TIMER")
        reminderDao.deleteTimerById(target.id)
        return true
    }

    suspend fun getActiveTimers(): List<TimerEntity> {
        val now = System.currentTimeMillis()
        return reminderDao.getActiveTimers(now)
    }

    suspend fun setReminder(message: String, triggerTimeMs: Long): Long {
        val entity = ReminderEntity(
            message = message.trim(),
            triggerTimeMs = triggerTimeMs
        )
        val id = reminderDao.insertReminder(entity)

        scheduleAlarm(
            id = id.toInt() + 100000,
            triggerAtMs = triggerTimeMs,
            type = "REMINDER",
            title = "JARVIS Reminder",
            message = message.trim()
        )

        Log.i(TAG, "Scheduled reminder id=$id at $triggerTimeMs: $message")
        return id
    }

    suspend fun cancelReminder(reminderId: Long?, query: String?): Boolean {
        val active = reminderDao.getActiveReminders()
        val target: ReminderEntity? = if (reminderId != null) {
            active.firstOrNull { it.id == reminderId }
        } else if (!query.isNullOrBlank()) {
            active.firstOrNull { it.message.contains(query, ignoreCase = true) }
        } else {
            active.firstOrNull()
        }

        if (target == null) return false

        cancelAlarm(target.id.toInt() + 100000, "REMINDER")
        reminderDao.deleteReminderById(target.id)
        return true
    }

    suspend fun getActiveReminders(): List<ReminderEntity> {
        return reminderDao.getActiveReminders()
    }

    private fun scheduleAlarm(id: Int, triggerAtMs: Long, type: String, title: String, message: String) {
        val intent = Intent(context, JarvisAlarmReceiver::class.java).apply {
            action = JarvisAlarmReceiver.ACTION_ALARM_TRIGGERED
            putExtra(JarvisAlarmReceiver.EXTRA_TYPE, type)
            putExtra(JarvisAlarmReceiver.EXTRA_TITLE, title)
            putExtra(JarvisAlarmReceiver.EXTRA_MESSAGE, message)
            putExtra(JarvisAlarmReceiver.EXTRA_ID, id)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, flags)

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm permission not granted; falling back to inexact alarm", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }

    private fun cancelAlarm(id: Int, type: String) {
        val intent = Intent(context, JarvisAlarmReceiver::class.java).apply {
            action = JarvisAlarmReceiver.ACTION_ALARM_TRIGGERED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, flags)
        alarmManager?.cancel(pendingIntent)
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600} hr ${if ((seconds % 3600) / 60 > 0) "${(seconds % 3600) / 60} min" else ""}".trim()
            seconds >= 60 -> "${seconds / 60} min ${if (seconds % 60 > 0) "${seconds % 60} sec" else ""}".trim()
            else -> "$seconds seconds"
        }
    }
}
