package com.jarvis.assistant.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a locally stored note.
 * Never synced remotely or uploaded to third-party APIs.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val content: String,

    val tags: String = "",

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis(),

    val isArchived: Boolean = false
)
