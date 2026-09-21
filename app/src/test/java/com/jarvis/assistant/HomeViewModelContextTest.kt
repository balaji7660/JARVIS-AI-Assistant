package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.ai.LocalResponseEngine
import com.jarvis.assistant.context.ConversationContextManager
import com.jarvis.assistant.context.ReferenceResolver
import com.jarvis.assistant.context.ScreenElementContext
import com.jarvis.assistant.memory.MemoryCategory
import com.jarvis.assistant.memory.MemoryEntity
import com.jarvis.assistant.memory.MemoryManager
import com.jarvis.assistant.memory.MemoryRepository
import com.jarvis.assistant.planner.TaskExecutionState
import com.jarvis.assistant.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelContextTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeMemoryRepo : MemoryRepository {
        val list = mutableListOf<MemoryEntity>()
        private var idGen = 1L

        override suspend fun saveMemory(memory: MemoryEntity): Long {
            val id = idGen++
            list.add(memory.copy(id = id))
            return id
        }
        override suspend fun searchMemories(query: String): List<MemoryEntity> = list.filter { it.content.contains(query, true) }
        override suspend fun getRelevantMemories(query: String): List<MemoryEntity> = list
        override suspend fun getAllMemories(): List<MemoryEntity> = list.toList()
        override suspend fun deleteMemory(id: Long) { list.removeAll { it.id == id } }
        override suspend fun clearAllMemories() { list.clear() }
        override suspend fun getMemoryCount(): Int = list.size
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun handleSpeechRecognized_clearContextCommand_resetsShortTermContextOnly() = runTest(testDispatcher) {
        val memoryRepo = FakeMemoryRepo()
        val memoryManager = MemoryManager(memoryRepo)
        // Store a permanent memory
        memoryManager.saveMemory("User prefers Kotlin", MemoryCategory.PREFERENCE)

        val contextManager = ConversationContextManager()
        contextManager.recordUserTurn("Open YouTube")
        contextManager.updateCurrentApp("YouTube", "com.google.android.youtube")

        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            memoryRepository = memoryRepo,
            memoryManager = memoryManager,
            conversationContextManager = contextManager
        )

        assertEquals("YouTube", contextManager.getConversationContext().currentApp)
        assertEquals(1, memoryManager.getMemoryCount())

        viewModel.handleSpeechRecognized("Clear current context")

        // Short-term context is cleared
        assertEquals(null, contextManager.getConversationContext().currentApp)
        assertTrue(contextManager.getConversationContext().recentTurns.isEmpty())

        // Long-term Room memory is completely preserved!
        assertEquals(1, memoryManager.getMemoryCount())
        assertEquals("User prefers Kotlin", memoryManager.getAllMemories().first().content)
        assertTrue(viewModel.uiState.value.responseText.contains("context cleared", ignoreCase = true))
    }

    @Test
    fun handleSpeechRecognized_stopCommand_cancelsTaskAndUpdatesContext() = runTest(testDispatcher) {
        val contextManager = ConversationContextManager()
        contextManager.updateTaskProgress(
            taskId = "task_test",
            description = "Download file",
            currentStep = 1,
            totalSteps = 3,
            lastSuccessfulStep = 0,
            state = TaskExecutionState.EXECUTING
        )

        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            conversationContextManager = contextManager
        )

        viewModel.handleSpeechRecognized("Stop.")

        assertEquals(TaskExecutionState.CANCELLED, viewModel.uiState.value.taskState)
        assertEquals(TaskExecutionState.CANCELLED, contextManager.getActiveTaskContext().taskState)
        assertEquals("Task cancelled, boss.", viewModel.uiState.value.responseText)
    }

    @Test
    fun handleSpeechRecognized_ambiguousReference_promptsUserForClarification() = runTest(testDispatcher) {
        val contextManager = ConversationContextManager()
        contextManager.updateScreenSummary(
            summary = "Screen with buttons",
            elements = listOf(
                ScreenElementContext(label = "Download 720p", type = "button"),
                ScreenElementContext(label = "Download 1080p", type = "button")
            )
        )

        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            conversationContextManager = contextManager
        )

        viewModel.handleSpeechRecognized("Click the button")

        assertTrue(viewModel.uiState.value.responseText.contains("multiple matching items", ignoreCase = true))
        assertTrue(viewModel.uiState.value.responseText.contains("Download 720p", ignoreCase = true))
        assertTrue(viewModel.uiState.value.responseText.contains("Download 1080p", ignoreCase = true))
    }

    @Test
    fun clearCurrentContextUIAction_resetsContextWithoutAlteringRoomMemory() = runTest(testDispatcher) {
        val memoryRepo = FakeMemoryRepo()
        val memoryManager = MemoryManager(memoryRepo)
        memoryManager.saveMemory("User prefers Dark Mode", MemoryCategory.PREFERENCE)

        val contextManager = ConversationContextManager()
        contextManager.recordUserTurn("Where is the store?")
        contextManager.updateCurrentApp("Maps", "com.google.android.apps.maps")

        val viewModel = HomeViewModel(
            application = Application(),
            responseEngine = LocalResponseEngine(),
            memoryRepository = memoryRepo,
            memoryManager = memoryManager,
            conversationContextManager = contextManager
        )

        viewModel.clearCurrentContext()

        assertEquals(null, contextManager.getConversationContext().currentApp)
        assertEquals(1, memoryManager.getMemoryCount())
    }
}
