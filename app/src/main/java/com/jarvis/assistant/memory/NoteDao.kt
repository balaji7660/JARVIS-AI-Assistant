package com.jarvis.assistant.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND title LIKE '%' || :title || '%' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByTitle(title: String): NoteEntity?
}
