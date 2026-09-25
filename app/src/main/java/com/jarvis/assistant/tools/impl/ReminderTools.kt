package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.reminders.TimerReminderManager
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Native Reminders and Timers tools.
 */

class CreateTimerTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "create_timer"
    override val description: String = "Sets a countdown timer for a specified duration in seconds or minutes (e.g. 'Set a timer for 10 minutes', 'Timer for 30 seconds')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawDuration = arguments["durationSeconds"] ?: arguments["seconds"] ?: arguments["duration"]
        var seconds: Long = when (rawDuration) {
            is Number -> rawDuration.toLong()
            is String -> rawDuration.toLongOrNull() ?: 0L
            else -> 0L
        }

        val rawMinutes = arguments["minutes"]
        if (rawMinutes != null) {
            val mins = when (rawMinutes) {
                is Number -> rawMinutes.toLong()
                is String -> rawMinutes.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (mins > 0) seconds += (mins * 60)
        }

        val rawHours = arguments["hours"]
        if (rawHours != null) {
            val hrs = when (rawHours) {
                is Number -> rawHours.toLong()
                is String -> rawHours.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (hrs > 0) seconds += (hrs * 3600)
        }

        if (seconds <= 0) {
            return@withContext ToolResult(
                success = false,
                message = "Please specify how long you would like the timer to run for, boss."
            )
        }

        val label = (arguments["label"] as? String)?.trim() ?: "Timer"
        val id = timerReminderManager.setTimer(seconds, label)

        val readable = formatSeconds(seconds)
        ToolResult(
            success = true,
            message = "Timer set for $readable.",
            data = mapOf("timerId" to id, "seconds" to seconds, "label" to label)
        )
    }

    private fun formatSeconds(sec: Long): String {
        return when {
            sec >= 3600 -> "${sec / 3600} hours and ${(sec % 3600) / 60} minutes"
            sec >= 60 -> "${sec / 60} minute${if (sec / 60 > 1) "s" else ""}${if (sec % 60 > 0) " ${sec % 60} seconds" else ""}"
            else -> "$sec seconds"
        }
    }
}

class CancelTimerTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "cancel_timer"
    override val description: String = "Cancels an active timer."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawId = arguments["timerId"]
        val id = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        val label = (arguments["label"] as? String)?.trim()

        val success = timerReminderManager.cancelTimer(id, label)
        if (success) {
            ToolResult(
                success = true,
                message = "Timer has been cancelled, boss."
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't find an active timer to cancel."
            )
        }
    }
}

class ListTimersTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "list_timers"
    override val description: String = "Lists currently active countdown timers."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val timers = timerReminderManager.getActiveTimers()
        if (timers.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = "There are no active timers right now, boss."
            )
        }

        val now = System.currentTimeMillis()
        val summary = timers.joinToString("\n") { t ->
            val remainingSec = ((t.endTimeMs - now) / 1000L).coerceAtLeast(0L)
            "• ${t.label}: $remainingSec seconds remaining"
        }

        ToolResult(
            success = true,
            message = "Active timers:\n$summary",
            data = mapOf("count" to timers.size)
        )
    }
}

class CreateReminderTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "create_reminder"
    override val description: String = "Creates a scheduled reminder (e.g. 'Remind me in 30 minutes to call Daddy', 'Remind me tomorrow at 9 AM about interview')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val message = (arguments["message"] as? String ?: arguments["text"] as? String ?: arguments["title"] as? String)?.trim()
        if (message.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "What would you like to be reminded about, boss?"
            )
        }

        val now = System.currentTimeMillis()
        var triggerMs: Long? = null

        val rawMinutes = arguments["relativeMinutes"] ?: arguments["minutes"]
        if (rawMinutes != null) {
            val mins = when (rawMinutes) {
                is Number -> rawMinutes.toLong()
                is String -> rawMinutes.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (mins > 0) triggerMs = now + (mins * 60 * 1000L)
        }

        val rawHours = arguments["relativeHours"] ?: arguments["hours"]
        if (rawHours != null && triggerMs == null) {
            val hrs = when (rawHours) {
                is Number -> rawHours.toLong()
                is String -> rawHours.toLongOrNull() ?: 0L
                else -> 0L
            }
            if (hrs > 0) triggerMs = now + (hrs * 3600 * 1000L)
        }

        val explicitMs = arguments["triggerTimeMs"]
        if (explicitMs != null && triggerMs == null) {
            triggerMs = when (explicitMs) {
                is Number -> explicitMs.toLong()
                is String -> explicitMs.toLongOrNull()
                else -> null
            }
        }

        // Fallback: 10 minutes if no specific time provided
        val finalTriggerMs = triggerMs ?: (now + (10 * 60 * 1000L))
        val id = timerReminderManager.setReminder(message, finalTriggerMs)

        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = formatter.format(finalTriggerMs)

        ToolResult(
            success = true,
            message = "I've set a reminder for $formattedTime: \"$message\".",
            data = mapOf("reminderId" to id, "triggerTimeMs" to finalTriggerMs, "message" to message)
        )
    }
}

class CancelReminderTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "cancel_reminder"
    override val description: String = "Cancels a scheduled reminder by message query or ID."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawId = arguments["reminderId"]
        val id = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        val query = (arguments["query"] as? String ?: arguments["message"] as? String)?.trim()

        val success = timerReminderManager.cancelReminder(id, query)
        if (success) {
            ToolResult(
                success = true,
                message = "Reminder has been cancelled, boss."
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't find a matching reminder to cancel."
            )
        }
    }
}

class ListRemindersTool(
    private val timerReminderManager: TimerReminderManager
) : JarvisTool {

    override val name: String = "list_reminders"
    override val description: String = "Lists upcoming scheduled reminders."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val reminders = timerReminderManager.getActiveReminders()
        if (reminders.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = "You don't have any upcoming reminders, boss."
            )
        }

        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val summary = reminders.joinToString("\n") { r ->
            "• \"${r.message}\" at ${formatter.format(r.triggerTimeMs)}"
        }

        ToolResult(
            success = true,
            message = "Your upcoming reminders:\n$summary",
            data = mapOf("count" to reminders.size)
        )
    }
}
