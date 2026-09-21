package com.jarvis.assistant

import com.jarvis.assistant.tools.impl.GetDateTool
import com.jarvis.assistant.tools.impl.GetTimeTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DateTimeToolsTest {

    @Test
    fun getTimeTool_returnsFormattedTime() = runTest {
        // Fixed date: 15:30:00 (3:30 PM)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }
        val tool = GetTimeTool(timeProvider = { cal.time })
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertEquals("3:30 pm", result.message.lowercase())
    }

    @Test
    fun getDateTool_returnsFormattedDate() = runTest {
        // Fixed date: 2026-09-21 (Monday, September 21, 2026)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 21)
        }
        val tool = GetDateTool(dateProvider = { cal.time })
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("2026"))
        assertTrue(result.message.contains("September") || result.message.contains("Sep"))
    }
}
