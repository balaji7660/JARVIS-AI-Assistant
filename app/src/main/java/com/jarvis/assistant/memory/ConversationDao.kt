package com.jarvis.assistant.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveConversation(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAllConversations(): List<ConversationEntity>

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
}
