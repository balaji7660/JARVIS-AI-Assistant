package com.jarvis.assistant.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for JARVIS persistent memories.
 * All queries are strictly bounded to prevent memory bloat.
 */
@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryEntity?

    @Query("SELECT * FROM memories WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getAllActive(limit: Int = 500): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE isActive = 1 AND (content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE isActive = 1")
    suspend fun getActiveCount(): Int

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
