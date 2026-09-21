package com.jarvis.assistant.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of MemoryRepository executing database operations on Dispatchers.IO.
 */
class RoomMemoryRepository(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override suspend fun saveMemory(memory: MemoryEntity): Long = withContext(Dispatchers.IO) {
        memoryDao.insert(memory)
    }

    override suspend fun searchMemories(query: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            memoryDao.getAllActive(limit = 20)
        } else {
            memoryDao.search(query.trim(), limit = 20)
        }
    }

    override suspend fun getRelevantMemories(query: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            memoryDao.getAllActive(limit = 10)
        } else {
            // Find keyword matches or return recent active memories
            val results = memoryDao.search(query.trim(), limit = 10)
            if (results.isNotEmpty()) {
                results
            } else {
                memoryDao.getAllActive(limit = 5)
            }
        }
    }

    override suspend fun getAllMemories(): List<MemoryEntity> = withContext(Dispatchers.IO) {
        memoryDao.getAllActive(limit = 500)
    }

    override suspend fun deleteMemory(id: Long) = withContext(Dispatchers.IO) {
        memoryDao.deleteById(id)
    }

    override suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        memoryDao.deleteAll()
    }

    override suspend fun getMemoryCount(): Int = withContext(Dispatchers.IO) {
        memoryDao.getActiveCount()
    }
}
