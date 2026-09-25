package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.calendar.CalendarManager
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Android Calendar Tools (Controlled Calendar Operations).
 */

class CreateCalendarEventTool(
    private val calendarManager: CalendarManager
) : JarvisTool {

    override val name: String = "create_calendar_event"
    override val description: String = "Creates a calendar event (e.g. 'Schedule Java interview preparation tomorrow at 7 PM'). Requires confirmation."
    override val riskLevel: RiskLevel = RiskLevel.MEDIUM

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val title = (arguments["title"] as? String ?: arguments["event"] as? String ?: arguments["summary"] as? String)?.trim()
        if (title.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "Please provide an event title, boss."
            )
        }

        val rawStartMs = arguments["startMillis"] ?: arguments["startTimeMs"]
        var startMs = when (rawStartMs) {
            is Number -> rawStartMs.toLong()
            is String -> rawStartMs.toLongOrNull()
            else -> null
        }

        if (startMs == null) {
            // Parse relative date & time expressions
            val dateStr = (arguments["date"] as? String ?: "today").lowercase()
            val timeStr = (arguments["time"] as? String ?: "").lowercase()

            val cal = Calendar.getInstance()
            if (dateStr.contains("tomorrow")) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }

            if (timeStr.isNotBlank()) {
                val hour = extractHour(timeStr)
                val minute = extractMinute(timeStr)
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
            } else {
                // Default to next hour if no time specified
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
            }
            startMs = cal.timeInMillis
        }

        val rawDuration = arguments["durationMinutes"] ?: arguments["duration"] ?: 60
        val duration = when (rawDuration) {
            is Number -> rawDuration.toInt()
            is String -> rawDuration.toIntOrNull() ?: 60
            else -> 60
        }
        val location = (arguments["location"] as? String)?.trim() ?: ""

        val eventId = calendarManager.createEvent(title, startMs, duration, location)
        val formatter = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault())
        val formattedDate = formatter.format(startMs)

        if (eventId != null) {
            ToolResult(
                success = true,
                message = "Calendar event created:\n\"$title\"\n$formattedDate",
                data = mapOf("eventId" to eventId, "title" to title, "startMillis" to startMs)
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't create the calendar event. Please verify calendar permissions in Settings."
            )
        }
    }

    private fun extractHour(timeStr: String): Int {
        val isPm = timeStr.contains("pm")
        val digits = timeStr.filter { it.isDigit() || it == ':' }
        val parts = digits.split(":")
        var h = parts.firstOrNull()?.toIntOrNull() ?: 9
        if (isPm && h < 12) h += 12
        if (!isPm && timeStr.contains("am") && h == 12) h = 0
        return h
    }

    private fun extractMinute(timeStr: String): Int {
        val digits = timeStr.filter { it.isDigit() || it == ':' }
        val parts = digits.split(":")
        return if (parts.size > 1) parts[1].take(2).toIntOrNull() ?: 0 else 0
    }
}

class ListCalendarEventsTool(
    private val calendarManager: CalendarManager
) : JarvisTool {

    override val name: String = "list_calendar_events"
    override val description: String = "Lists calendar events for today, tomorrow, or an upcoming period (e.g. 'What is on my calendar today?')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rangeStr = (arguments["range"] as? String ?: arguments["date"] as? String ?: "today").trim()
        val (startMs, endMs) = calendarManager.resolveDateRange(rangeStr)

        val events = calendarManager.getEventsForRange(startMs, endMs, 10)
        if (events.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = "You have no events scheduled for $rangeStr, boss."
            )
        }

        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        val summary = events.joinToString("\n") { ev ->
            val time = formatter.format(ev.startMillis)
            "• \"${ev.title}\" at $time"
        }

        ToolResult(
            success = true,
            message = "Your events for $rangeStr:\n$summary",
            data = mapOf(
                "count" to events.size,
                "events" to events.map { mapOf("id" to it.id, "title" to it.title, "start" to it.startMillis) }
            )
        )
    }
}

class DeleteCalendarEventTool(
    private val calendarManager: CalendarManager
) : JarvisTool {

    override val name: String = "delete_calendar_event"
    override val description: String = "Deletes a scheduled calendar event by title or ID (requires confirmation)."
    override val riskLevel: RiskLevel = RiskLevel.HIGH

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawId = arguments["eventId"]
        val id = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        val title = (arguments["title"] as? String)?.trim()

        if (id == null && title.isNullOrBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "Please specify the calendar event to delete, boss."
            )
        }

        val deleted = calendarManager.deleteEvent(id, title)
        if (deleted) {
            ToolResult(
                success = true,
                message = "Calendar event \"${title ?: id}\" has been deleted, boss."
            )
        } else {
            ToolResult(
                success = false,
                message = "I couldn't delete the event \"${title ?: id}\". Please check calendar permissions or verify the event exists."
            )
        }
    }
}
