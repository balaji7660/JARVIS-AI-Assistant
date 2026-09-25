package com.jarvis.assistant.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int = 30): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}
