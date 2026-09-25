package com.jarvis.assistant

import com.jarvis.assistant.calendar.CalendarEventModel
import com.jarvis.assistant.calendar.CalendarManager
import com.jarvis.assistant.tools.impl.CreateCalendarEventTool
import com.jarvis.assistant.tools.impl.DeleteCalendarEventTool
import com.jarvis.assistant.tools.impl.ListCalendarEventsTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarToolsTest {

    @Test
    fun testResolveDateRange_today() {
        val manager = CalendarManager(android.app.Application())
        val (start, end) = manager.resolveDateRange("today")

        assertTrue(start < end)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 0)
    }

    @Test
    fun testResolveDateRange_tomorrow() {
        val manager = CalendarManager(android.app.Application())
        val (start, end) = manager.resolveDateRange("tomorrow")

        assertTrue(start < end)
        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance().apply { timeInMillis = start }
        assertTrue(tomorrow.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1 || (today.get(Calendar.DAY_OF_YEAR) > 360 && tomorrow.get(Calendar.DAY_OF_YEAR) == 1))
    }

    @Test
    fun testCreateCalendarEvent_requiresTitle() = runBlocking {
        val manager = CalendarManager(android.app.Application())
        val tool = CreateCalendarEventTool(manager)

        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.message.contains("Please provide an event title"))
    }

    @Test
    fun testDeleteCalendarEvent_requiresTarget() = runBlocking {
        val manager = CalendarManager(android.app.Application())
        val tool = DeleteCalendarEventTool(manager)

        val result = tool.execute(emptyMap())
        assertFalse(result.success)
        assertTrue(result.message.contains("Please specify the calendar event to delete"))
    }
}
