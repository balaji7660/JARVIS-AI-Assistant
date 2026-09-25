package com.jarvis.assistant.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CalendarEventModel(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String? = null
)

/**
 * On-device local Calendar access manager via Android CalendarContract.
 * Never leaks or dumps the full calendar database.
 */
class CalendarManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "CalendarManager"
    }

    private fun hasReadPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    fun getEventsForRange(startRangeMs: Long, endRangeMs: Long, limit: Int = 10): List<CalendarEventModel> {
        if (!hasReadPermission()) {
            Log.w(TAG, "READ_CALENDAR permission not granted.")
            return emptyList()
        }

        val events = mutableListOf<CalendarEventModel>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )

        val selection = "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTSTART} <= ?) AND (${CalendarContract.Events.DELETED} = 0)"
        val selectionArgs = arrayOf(startRangeMs.toString(), endRangeMs.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) it.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                    val start = if (startIdx >= 0) it.getLong(startIdx) else 0L
                    val end = if (endIdx >= 0) it.getLong(endIdx) else start
                    val loc = if (locIdx >= 0) it.getString(locIdx) else null

                    events.add(CalendarEventModel(id, title, start, end, loc))
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query calendar events", e)
        }

        return events
    }

    fun getUpcomingEvents(limit: Int = 5): List<CalendarEventModel> {
        val now = System.currentTimeMillis()
        val end = now + (7 * 24 * 3600 * 1000L) // Next 7 days
        return getEventsForRange(now, end, limit)
    }

    fun createEvent(
        title: String,
        startMillis: Long,
        durationMinutes: Int = 60,
        location: String = ""
    ): Long? {
        if (!hasWritePermission()) {
            Log.w(TAG, "WRITE_CALENDAR permission not granted.")
            return null
        }

        val primaryCalendarId = getPrimaryCalendarId() ?: 1L
        val endMillis = startMillis + (durationMinutes * 60 * 1000L)

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, primaryCalendarId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
        }

        return try {
            val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert calendar event", e)
            null
        }
    }

    fun deleteEvent(eventId: Long?, title: String?): Boolean {
        if (!hasWritePermission()) {
            Log.w(TAG, "WRITE_CALENDAR permission not granted.")
            return false
        }

        return try {
            if (eventId != null && eventId > 0) {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                val rows = context.contentResolver.delete(deleteUri, null, null)
                rows > 0
            } else if (!title.isNullOrBlank()) {
                val selection = "${CalendarContract.Events.TITLE} LIKE ?"
                val selectionArgs = arrayOf("%${title.trim()}%")
                val rows = context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
                rows > 0
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete calendar event", e)
            false
        }
    }

    private fun getPrimaryCalendarId(): Long? {
        if (!hasReadPermission()) return null
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(CalendarContract.Calendars._ID)
                if (it.moveToFirst() && idIdx >= 0) {
                    return it.getLong(idIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve primary calendar id", e)
        }
        return null
    }

    /**
     * Resolves natural date string ("today", "tomorrow", "tonight") to start and end millisecond timestamps.
     */
    fun resolveDateRange(rangeStr: String?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val q = rangeStr?.lowercase()?.trim() ?: "today"

        when {
            q.contains("tomorrow") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            q.contains("tonight") || q.contains("evening") -> {
                cal.set(Calendar.HOUR_OF_DAY, 18)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            q.contains("week") -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 7)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            else -> { // "today" or default
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
        }
    }
}
