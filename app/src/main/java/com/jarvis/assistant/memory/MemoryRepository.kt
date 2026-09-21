package com.jarvis.assistant.memory

/**
 * Architectural interface for persistent memory storage.
 * Decoupled from Room and SQLite to allow unit testing with in-memory or fake repositories.
 */
interface MemoryRepository {

    suspend fun saveMemory(memory: MemoryEntity): Long

    suspend fun searchMemories(query: String): List<MemoryEntity>

    suspend fun getRelevantMemories(query: String): List<MemoryEntity>

    suspend fun getAllMemories(): List<MemoryEntity>

    suspend fun deleteMemory(id: Long)

    suspend fun clearAllMemories()

    suspend fun getMemoryCount(): Int
}
