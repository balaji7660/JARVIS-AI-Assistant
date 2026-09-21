package com.jarvis.assistant.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryManagerTest {

    private class FakeMemoryRepository : MemoryRepository {
        val list = mutableListOf<MemoryEntity>()
        private var nextId = 1L

        override suspend fun saveMemory(memory: MemoryEntity): Long {
            val id = if (memory.id == 0L) nextId++ else memory.id
            val saved = memory.copy(id = id)
            list.removeAll { it.id == id }
            list.add(saved)
            return id
        }

        override suspend fun searchMemories(query: String): List<MemoryEntity> =
            list.filter { it.content.contains(query, ignoreCase = true) }

        override suspend fun getRelevantMemories(query: String): List<MemoryEntity> {
            val matching = list.filter { it.content.contains(query, ignoreCase = true) }
            return if (matching.isNotEmpty()) matching.take(10) else list.take(10)
        }

        override suspend fun getAllMemories(): List<MemoryEntity> = list.toList()

        override suspend fun deleteMemory(id: Long) {
            list.removeAll { it.id == id }
        }

        override suspend fun clearAllMemories() {
            list.clear()
        }

        override suspend fun getMemoryCount(): Int = list.size
    }

    private lateinit var repository: FakeMemoryRepository
    private lateinit var memoryManager: MemoryManager

    @Before
    fun setUp() {
        repository = FakeMemoryRepository()
        memoryManager = MemoryManager(repository)
    }

    @Test
    fun validateContent_rejectsSensitiveKeywords() {
        val sensitiveInputs = listOf(
            "My password is Secret123",
            "Remember my api_key sk-12345",
            "Store my bank account token",
            "My card number is 4111 2222 3333 4444",
            "Remember my debit pin is 9988",
            "Here is the otp code 4521"
        )

        sensitiveInputs.forEach { input ->
            val result = memoryManager.validateContent(input)
            assertFalse("Expected '$input' to be rejected", result.isValid)
            assertEquals("I can't securely store credentials or authentication secrets.", result.errorMessage)
        }
    }

    @Test
    fun validateContent_rejectsBlankOrTooLong() {
        val blankResult = memoryManager.validateContent("   ")
        assertFalse(blankResult.isValid)
        assertEquals("Memory content cannot be empty.", blankResult.errorMessage)

        val longResult = memoryManager.validateContent("A".repeat(1001))
        assertFalse(longResult.isValid)
        assertEquals("Memory content exceeds maximum limit of 1000 characters.", longResult.errorMessage)
    }

    @Test
    fun validateContent_acceptsSafeContent() {
        val safeResult = memoryManager.validateContent("My favourite drink is Earl Grey tea.")
        assertTrue(safeResult.isValid)
    }

    @Test
    fun saveMemory_successfulCommit() = runTest {
        val result = memoryManager.saveMemory("I work on Project Apollo", MemoryCategory.PROJECT)
        assertTrue(result.isSuccess)
        val memories = memoryManager.getAllMemoriesOnce()
        assertEquals(1, memories.size)
        assertEquals("I work on Project Apollo", memories[0].content)
        assertEquals(MemoryCategory.PROJECT.name, memories[0].category)
    }

    @Test
    fun saveMemory_duplicatePrevention() = runTest {
        val firstResult = memoryManager.saveMemory("I like green tea", MemoryCategory.PREFERENCE)
        assertTrue(firstResult.isSuccess)

        val duplicateResult = memoryManager.saveMemory("I like green tea", MemoryCategory.PREFERENCE)
        assertTrue(duplicateResult.isFailure)
        assertEquals("This memory already exists.", duplicateResult.exceptionOrNull()?.message)
    }

    @Test
    fun saveMemory_enforces500ItemCap() = runTest {
        // Fill up to 500 items
        for (i in 1..500) {
            repository.saveMemory(MemoryEntity(id = i.toLong(), content = "Memory #$i", category = MemoryCategory.OTHER.name))
        }

        val overflowResult = memoryManager.saveMemory("Memory #501", MemoryCategory.OTHER)
        assertTrue(overflowResult.isFailure)
        assertEquals("Memory storage is full (maximum 500 memories). Please remove older entries.", overflowResult.exceptionOrNull()?.message)
    }

    @Test
    fun categorize_identifiesCategoriesCorrectly() {
        assertEquals(MemoryCategory.PREFERENCE, memoryManager.categorize("I prefer dark mode"))
        assertEquals(MemoryCategory.PROJECT, memoryManager.categorize("Working on the JARVIS client app repository"))
        assertEquals(MemoryCategory.INSTRUCTION, memoryManager.categorize("Always keep responses concise"))
        assertEquals(MemoryCategory.PERSONAL_CONTEXT, memoryManager.categorize("My name is Balaji and I live in Bangalore"))
        assertEquals(MemoryCategory.OTHER, memoryManager.categorize("The sky is blue today"))
    }

    @Test
    fun buildMemoryContextForPrompt_injectsTop10Items() = runTest {
        for (i in 1..15) {
            memoryManager.saveMemory("User preference rule $i", MemoryCategory.PREFERENCE)
        }

        val promptContext = memoryManager.buildMemoryContextForPrompt("What do I like?")
        assertTrue(promptContext.contains("[SAVED USER CONTEXT & MEMORY]"))
        val lines = promptContext.lines().filter { it.startsWith("- (") }
        assertTrue("Expected between 1 and 10 items, but got ${lines.size}", lines.size in 1..10)
    }

    @Test
    fun formatMemoriesForSpeech_emptyAndPopulated() {
        val emptySpeech = memoryManager.formatMemoriesForSpeech(emptyList())
        assertEquals("I don't have any memories stored yet, boss.", emptySpeech)

        val populated = listOf(
            MemoryEntity(content = "User prefers iced latte", category = MemoryCategory.PREFERENCE.name),
            MemoryEntity(content = "User leads Android team", category = MemoryCategory.PERSONAL_CONTEXT.name)
        )
        val speech = memoryManager.formatMemoriesForSpeech(populated)
        assertTrue(speech.contains("You asked me to remember:"))
        assertTrue(speech.contains("1. User prefers iced latte"))
        assertTrue(speech.contains("2. User leads Android team"))
    }
}
