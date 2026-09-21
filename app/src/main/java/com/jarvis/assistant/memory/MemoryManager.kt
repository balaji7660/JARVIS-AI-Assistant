package com.jarvis.assistant.memory

import android.util.Log

sealed class MemoryValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    class Valid(val normalizedContent: String, val category: MemoryCategory) :
        MemoryValidationResult(true, null)
    class Rejected(val reason: String) :
        MemoryValidationResult(false, reason)
}

/**
 * MemoryManager coordinates persistent memory validation, storage limits, duplicate prevention,
 * sensitive content filtering, and prompt context synthesis.
 */
class MemoryManager(
    private val repository: MemoryRepository
) {

    companion object {
        private const val TAG = "MemoryManager"
        const val MAX_MEMORIES_LIMIT = 500
        const val MAX_CONTENT_LENGTH = 1000
        const val MAX_CONTEXT_INJECTED = 10
        const val SENSITIVE_REJECTION_MESSAGE = "I can't securely store credentials or authentication secrets."

        private val SENSITIVE_KEYWORDS = listOf(
            "password", "passwd", "api key", "api_key", "apikey", "secret key", "auth token",
            "bearer token", "jwt", "otp", "pin number", "debit pin", "pin is", "cvv", "credit card",
            "debit card", "card number", "account number", "bank account", "social security", "ssn"
        )
    }

    /**
     * Validates whether raw input text is eligible to be saved as memory.
     */
    fun validateContent(content: String): MemoryValidationResult {
        val trimmed = content.trim()
        if (trimmed.isBlank()) {
            return MemoryValidationResult.Rejected("Memory content cannot be empty.")
        }

        if (trimmed.length > MAX_CONTENT_LENGTH) {
            return MemoryValidationResult.Rejected(
                "Memory content exceeds maximum limit of $MAX_CONTENT_LENGTH characters."
            )
        }

        val lower = trimmed.lowercase()

        // 1. Sensitive credential check
        for (keyword in SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                Log.w(TAG, "Rejected sensitive memory containing keyword '$keyword'")
                return MemoryValidationResult.Rejected(SENSITIVE_REJECTION_MESSAGE)
            }
        }

        // 2. Category inference
        val category = inferCategory(lower)

        return MemoryValidationResult.Valid(trimmed, category)
    }

    fun validateMemoryContent(content: String): MemoryValidationResult = validateContent(content)

    fun inferCategory(lower: String): MemoryCategory {
        return when {
            lower.contains("prefer") || lower.contains("favorite") || lower.contains("favourite") || lower.contains("like") || lower.contains("dislike") -> {
                MemoryCategory.PREFERENCE
            }
            lower.contains("project") || lower.contains("repo") || lower.contains("app") || lower.contains("code") -> {
                MemoryCategory.PROJECT
            }
            lower.contains("always") || lower.contains("never") || lower.contains("instruction") || lower.contains("format") || lower.contains("concise") -> {
                MemoryCategory.INSTRUCTION
            }
            lower.contains("name") || lower.contains("live") || lower.contains("work") || lower.contains("birthday") || lower.contains("email") -> {
                MemoryCategory.PERSONAL_CONTEXT
            }
            else -> MemoryCategory.OTHER
        }
    }

    fun categorize(text: String): MemoryCategory = inferCategory(text.lowercase())

    /**
     * Saves an explicit memory after validation and capacity check.
     */
    suspend fun saveMemory(
        content: String,
        categoryOverride: MemoryCategory? = null,
        source: String = "user_explicit"
    ): Result<MemoryEntity> {
        val validation = validateContent(content)
        if (validation is MemoryValidationResult.Rejected) {
            return Result.failure(IllegalArgumentException(validation.reason))
        }

        val valid = validation as MemoryValidationResult.Valid
        val currentCount = repository.getMemoryCount()
        if (currentCount >= MAX_MEMORIES_LIMIT) {
            return Result.failure(IllegalStateException("Memory storage is full (maximum $MAX_MEMORIES_LIMIT memories). Please remove older entries."))
        }

        val existing = repository.searchMemories(valid.normalizedContent)
        val duplicate = existing.find { it.content.equals(valid.normalizedContent, ignoreCase = true) }
        if (duplicate != null) {
            return Result.failure(IllegalArgumentException("This memory already exists."))
        }

        val categoryToUse = categoryOverride ?: valid.category
        val entity = MemoryEntity(
            category = categoryToUse.name,
            content = valid.normalizedContent,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            source = source,
            isActive = true
        )

        val id = repository.saveMemory(entity)
        return Result.success(entity.copy(id = id))
    }

    /**
     * Builds bounded context string for injection into OpenAI prompts.
     */
    suspend fun buildMemoryContextForPrompt(query: String): String {
        val memories = repository.getRelevantMemories(query)
            .take(MAX_CONTEXT_INJECTED)

        if (memories.isEmpty()) {
            return ""
        }

        val sb = StringBuilder("[SAVED USER CONTEXT & MEMORY]\n")
        for (memory in memories) {
            sb.append("- (").append(memory.category).append(") ").append(memory.content).append("\n")
        }
        return sb.toString().trim()
    }

    /**
     * Formats memories for speech query "What do you remember about me?".
     */
    fun formatMemoriesForSpeech(memories: List<MemoryEntity>): String {
        if (memories.isEmpty()) {
            return "I don't have any memories stored yet, boss."
        }
        val prefix = "You asked me to remember:\n"
        val items = memories.take(10).mapIndexed { idx, m ->
            "${idx + 1}. ${m.content}"
        }.joinToString("\n")
        return prefix + items
    }

    suspend fun getFormattedMemoriesSummary(): String {
        val memories = repository.getAllMemories()
        return formatMemoriesForSpeech(memories)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return repository.searchMemories(query)
    }

    suspend fun getAllMemoriesOnce(): List<MemoryEntity> {
        return repository.getAllMemories()
    }

    suspend fun deleteMemory(id: Long) {
        repository.deleteMemory(id)
    }

    suspend fun clearAllMemories() {
        repository.clearAllMemories()
    }

    suspend fun getAllMemories(): List<MemoryEntity> {
        return repository.getAllMemories()
    }

    suspend fun getMemoryCount(): Int {
        return repository.getMemoryCount()
    }
}
