package com.jarvis.assistant.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

/**
 * Room entity representing a user reminder.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,
    val triggerTimeMs: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room entity representing a persistent countdown timer.
 */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "Timer",
    val durationSeconds: Long,
    val endTimeMs: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTimeMs ASC")
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND message LIKE '%' || :query || '%' ORDER BY triggerTimeMs ASC")
    suspend fun searchReminders(query: String): List<ReminderEntity>

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerEntity): Long

    @Query("SELECT * FROM timers WHERE isCompleted = 0 AND endTimeMs > :now ORDER BY endTimeMs ASC")
    suspend fun getActiveTimers(now: Long = System.currentTimeMillis()): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id LIMIT 1")
    suspend fun getTimerById(id: Long): TimerEntity?

    @Update
    suspend fun updateTimer(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimerById(id: Long): Int
}
