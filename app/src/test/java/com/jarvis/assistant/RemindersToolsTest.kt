package com.jarvis.assistant

import com.jarvis.assistant.memory.ReminderDao
import com.jarvis.assistant.memory.ReminderEntity
import com.jarvis.assistant.memory.TimerEntity
import com.jarvis.assistant.reminders.TimerReminderManager
import com.jarvis.assistant.tools.impl.CancelReminderTool
import com.jarvis.assistant.tools.impl.CancelTimerTool
import com.jarvis.assistant.tools.impl.CreateReminderTool
import com.jarvis.assistant.tools.impl.CreateTimerTool
import com.jarvis.assistant.tools.impl.ListRemindersTool
import com.jarvis.assistant.tools.impl.ListTimersTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class RemindersToolsTest {

    private class FakeReminderDao : ReminderDao {
        val reminders = mutableListOf<ReminderEntity>()
        val timers = mutableListOf<TimerEntity>()
        private var remId = 1L
        private var timerId = 1L

        override suspend fun insertReminder(reminder: ReminderEntity): Long {
            val id = if (reminder.id == 0L) remId++ else reminder.id
            val saved = reminder.copy(id = id)
            reminders.removeAll { it.id == id }
            reminders.add(saved)
            return id
        }

        override suspend fun getActiveReminders(): List<ReminderEntity> =
            reminders.filter { !it.isCompleted }

        override suspend fun getReminderById(id: Long): ReminderEntity? =
            reminders.firstOrNull { it.id == id }

        override suspend fun searchReminders(query: String): List<ReminderEntity> =
            reminders.filter { !it.isCompleted && it.message.contains(query, ignoreCase = true) }

        override suspend fun updateReminder(reminder: ReminderEntity) {
            reminders.removeAll { it.id == reminder.id }
            reminders.add(reminder)
        }

        override suspend fun deleteReminderById(id: Long): Int =
            if (reminders.removeAll { it.id == id }) 1 else 0

        override suspend fun insertTimer(timer: TimerEntity): Long {
            val id = if (timer.id == 0L) timerId++ else timer.id
            val saved = timer.copy(id = id)
            timers.removeAll { it.id == id }
            timers.add(saved)
            return id
        }

        override suspend fun getActiveTimers(now: Long): List<TimerEntity> =
            timers.filter { !it.isCompleted && it.endTimeMs > now }

        override suspend fun getTimerById(id: Long): TimerEntity? =
            timers.firstOrNull { it.id == id }

        override suspend fun updateTimer(timer: TimerEntity) {
            timers.removeAll { it.id == timer.id }
            timers.add(timer)
        }

        override suspend fun deleteTimerById(id: Long): Int =
            if (timers.removeAll { it.id == id }) 1 else 0
    }

    private lateinit var dao: FakeReminderDao
    private lateinit var manager: TimerReminderManager

    @Before
    fun setup() {
        dao = FakeReminderDao()
        val dummyContext = android.app.Application()
        manager = TimerReminderManager(dummyContext, dao)
    }

    @Test
    fun testCreateTimer_secondsAndMinutes() = runBlocking {
        val tool = CreateTimerTool(manager)

        val resSec = tool.execute(mapOf("seconds" to 30))
        assertTrue(resSec.success)
        assertTrue(resSec.message.contains("30 seconds"))

        val resMin = tool.execute(mapOf("minutes" to 5))
        assertTrue(resMin.success)
        assertTrue(resMin.message.contains("5 minutes"))
    }

    @Test
    fun testListTimers_returnsActive() = runBlocking {
        dao.insertTimer(TimerEntity(label = "Pasta Timer", durationSeconds = 600, endTimeMs = System.currentTimeMillis() + 600000))
        val tool = ListTimersTool(manager)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("Pasta Timer"))
    }

    @Test
    fun testCancelTimer_removesActive() = runBlocking {
        val id = dao.insertTimer(TimerEntity(label = "Study Timer", durationSeconds = 1200, endTimeMs = System.currentTimeMillis() + 1200000))
        val tool = CancelTimerTool(manager)

        val result = tool.execute(mapOf("timerId" to id))
        assertTrue(result.success)
        assertEquals(0, dao.timers.size)
    }

    @Test
    fun testCreateReminder_persistsAndFormats() = runBlocking {
        val tool = CreateReminderTool(manager)
        val result = tool.execute(mapOf("message" to "Call Daddy", "minutes" to 30))

        assertTrue(result.success)
        assertTrue(result.message.contains("Call Daddy"))
        assertEquals(1, dao.reminders.size)
    }

    @Test
    fun testListReminders_showsAll() = runBlocking {
        dao.insertReminder(ReminderEntity(message = "Interview Prep", triggerTimeMs = System.currentTimeMillis() + 3600000))
        val tool = ListRemindersTool(manager)

        val result = tool.execute(emptyMap())
        assertTrue(result.success)
        assertTrue(result.message.contains("Interview Prep"))
    }

    @Test
    fun testCancelReminder_deletesFromStorage() = runBlocking {
        val id = dao.insertReminder(ReminderEntity(message = "Buy Milk", triggerTimeMs = System.currentTimeMillis() + 100000))
        val tool = CancelReminderTool(manager)

        val result = tool.execute(mapOf("reminderId" to id))
        assertTrue(result.success)
        assertEquals(0, dao.reminders.size)
    }
}
