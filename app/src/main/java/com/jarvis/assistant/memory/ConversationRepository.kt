package com.jarvis.assistant.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface ConversationRepository {
    suspend fun getOrCreateActiveConversation(): ConversationEntity
    suspend fun saveUserMessage(text: String, conversationId: String? = null): MessageEntity
    suspend fun saveAssistantMessage(
        text: String,
        conversationId: String? = null,
        toolCallsJson: String? = null,
        toolResultsJson: String? = null
    ): MessageEntity
    suspend fun getRecentMessages(conversationId: String? = null, limit: Int = 30): List<MessageEntity>
    suspend fun restoreLastSessionContext(): List<MessageEntity>
    suspend fun clearCurrentConversation(conversationId: String? = null)
}

class RoomConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ConversationRepository {

    @Volatile
    private var activeConversationId: String? = null

    override suspend fun getOrCreateActiveConversation(): ConversationEntity = withContext(Dispatchers.IO) {
        val currentId = activeConversationId
        if (currentId != null) {
            val existing = conversationDao.getConversationById(currentId)
            if (existing != null && existing.isActive) {
                return@withContext existing
            }
        }

        val latest = conversationDao.getLatestActiveConversation()
        if (latest != null) {
            activeConversationId = latest.id
            return@withContext latest
        }

        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "Conversation ${System.currentTimeMillis()}",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        conversationDao.insertConversation(newConv)
        activeConversationId = newConv.id
        newConv
    }

    override suspend fun saveUserMessage(text: String, conversationId: String?): MessageEntity = withContext(Dispatchers.IO) {
        val targetConvId = conversationId ?: getOrCreateActiveConversation().id
        val message = MessageEntity(
            conversationId = targetConvId,
            role = "user",
            content = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        val id = messageDao.insertMessage(message)
        // Update conversation timestamp
        conversationDao.getConversationById(targetConvId)?.let {
            conversationDao.updateConversation(it.copy(updatedAt = System.currentTimeMillis()))
        }
        message.copy(id = id)
    }

    override suspend fun saveAssistantMessage(
        text: String,
        conversationId: String?,
        toolCallsJson: String?,
        toolResultsJson: String?
    ): MessageEntity = withContext(Dispatchers.IO) {
        val targetConvId = conversationId ?: getOrCreateActiveConversation().id
        val message = MessageEntity(
            conversationId = targetConvId,
            role = "assistant",
            content = text.trim(),
            sanitizedToolCallsJson = toolCallsJson,
            sanitizedToolResultsJson = toolResultsJson,
            timestamp = System.currentTimeMillis()
        )
        val id = messageDao.insertMessage(message)
        conversationDao.getConversationById(targetConvId)?.let {
            conversationDao.updateConversation(it.copy(updatedAt = System.currentTimeMillis()))
        }
        message.copy(id = id)
    }

    override suspend fun getRecentMessages(conversationId: String?, limit: Int): List<MessageEntity> = withContext(Dispatchers.IO) {
        val targetConvId = conversationId ?: getOrCreateActiveConversation().id
        // Dao returns in DESC order, reverse for chronological order
        messageDao.getRecentMessages(targetConvId, limit).reversed()
    }

    override suspend fun restoreLastSessionContext(): List<MessageEntity> = withContext(Dispatchers.IO) {
        val active = getOrCreateActiveConversation()
        messageDao.getRecentMessages(active.id, limit = 20).reversed()
    }

    override suspend fun clearCurrentConversation(conversationId: String?) = withContext(Dispatchers.IO) {
        val targetConvId = conversationId ?: activeConversationId
        if (targetConvId != null) {
            messageDao.deleteMessagesForConversation(targetConvId)
            conversationDao.deleteConversation(targetConvId)
        }
        activeConversationId = null
    }
}

class InMemoryConversationRepository : ConversationRepository {
    private val conversations = mutableListOf<ConversationEntity>()
    private val messages = mutableListOf<MessageEntity>()
    private var activeId: String? = null
    private var messageIdCounter = 1L

    override suspend fun getOrCreateActiveConversation(): ConversationEntity {
        val currentId = activeId
        if (currentId != null) {
            val existing = conversations.firstOrNull { it.id == currentId && it.isActive }
            if (existing != null) return existing
        }
        val latest = conversations.filter { it.isActive }.maxByOrNull { it.updatedAt }
        if (latest != null) {
            activeId = latest.id
            return latest
        }
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "Conversation ${System.currentTimeMillis()}",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true
        )
        conversations.add(newConv)
        activeId = newConv.id
        return newConv
    }

    override suspend fun saveUserMessage(text: String, conversationId: String?): MessageEntity {
        val targetId = conversationId ?: getOrCreateActiveConversation().id
        val msg = MessageEntity(
            id = messageIdCounter++,
            conversationId = targetId,
            role = "user",
            content = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        messages.add(msg)
        return msg
    }

    override suspend fun saveAssistantMessage(
        text: String,
        conversationId: String?,
        toolCallsJson: String?,
        toolResultsJson: String?
    ): MessageEntity {
        val targetId = conversationId ?: getOrCreateActiveConversation().id
        val msg = MessageEntity(
            id = messageIdCounter++,
            conversationId = targetId,
            role = "assistant",
            content = text.trim(),
            sanitizedToolCallsJson = toolCallsJson,
            sanitizedToolResultsJson = toolResultsJson,
            timestamp = System.currentTimeMillis()
        )
        messages.add(msg)
        return msg
    }

    override suspend fun getRecentMessages(conversationId: String?, limit: Int): List<MessageEntity> {
        val targetId = conversationId ?: getOrCreateActiveConversation().id
        return messages.filter { it.conversationId == targetId }.takeLast(limit)
    }

    override suspend fun restoreLastSessionContext(): List<MessageEntity> {
        val active = getOrCreateActiveConversation()
        return messages.filter { it.conversationId == active.id }.takeLast(20)
    }

    override suspend fun clearCurrentConversation(conversationId: String?) {
        val targetId = conversationId ?: activeId
        if (targetId != null) {
            messages.removeAll { it.conversationId == targetId }
            conversations.removeAll { it.id == targetId }
        }
        activeId = null
    }
}
