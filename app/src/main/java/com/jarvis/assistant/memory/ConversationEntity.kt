package com.jarvis.assistant.memory

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing a persistent conversation session in JARVIS.
 * Survives activity destruction, app restart, and device reboot.
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Conversation",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
