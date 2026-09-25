package com.jarvis.assistant.memory

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing an individual conversational turn (user message, assistant reply, or tool execution).
 * Permanently stored in Room database.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("conversationId"),
        Index("timestamp")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val role: String, // "user", "assistant", "system", "tool"
    val content: String,
    val sanitizedToolCallsJson: String? = null,
    val sanitizedToolResultsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
