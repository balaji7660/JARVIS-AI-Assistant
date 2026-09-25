package com.jarvis.assistant

import com.jarvis.assistant.memory.NoteDao
import com.jarvis.assistant.memory.NoteEntity
import com.jarvis.assistant.memory.NoteRepository
import com.jarvis.assistant.tools.impl.CreateNoteTool
import com.jarvis.assistant.tools.impl.DeleteNoteTool
import com.jarvis.assistant.tools.impl.ListNotesTool
import com.jarvis.assistant.tools.impl.SearchNotesTool
import com.jarvis.assistant.tools.impl.UpdateNoteTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotesToolsTest {

    private class FakeNoteDao : NoteDao {
        val notes = mutableListOf<NoteEntity>()
        private var idCounter = 1L

        override suspend fun insert(note: NoteEntity): Long {
            val id = if (note.id == 0L) idCounter++ else note.id
            val entity = note.copy(id = id)
            notes.removeAll { it.id == id }
            notes.add(entity)
            return id
        }

        override suspend fun update(note: NoteEntity) {
            notes.removeAll { it.id == note.id }
            notes.add(note)
        }

        override suspend fun delete(note: NoteEntity) {
            notes.removeAll { it.id == note.id }
        }

        override suspend fun deleteById(id: Long): Int {
            return if (notes.removeAll { it.id == id }) 1 else 0
        }

        override suspend fun getAllNotes(): List<NoteEntity> = notes.filter { !it.isArchived }

        override suspend fun getNoteById(id: Long): NoteEntity? = notes.firstOrNull { it.id == id }

        override suspend fun searchNotes(query: String): List<NoteEntity> =
            notes.filter { !it.isArchived && (it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)) }

        override suspend fun findByTitle(title: String): NoteEntity? =
            notes.firstOrNull { !it.isArchived && it.title.contains(title, ignoreCase = true) }
    }

    private lateinit var dao: FakeNoteDao
    private lateinit var repo: NoteRepository

    @Before
    fun setup() {
        dao = FakeNoteDao()
        repo = NoteRepository(dao)
    }

    @Test
    fun testCreateNote_success() = runBlocking {
        val tool = CreateNoteTool(repo)
        val result = tool.execute(mapOf("title" to "Java Interview", "content" to "Spring Boot, JVM tuning"))

        assertTrue(result.success)
        assertTrue(result.message.contains("Java Interview"))
        assertEquals(1, dao.notes.size)
    }

    @Test
    fun testCreateNote_blocksPasswordsAndCredentials() = runBlocking {
        val tool = CreateNoteTool(repo)
        val result = tool.execute(mapOf("title" to "Secret Bank Account", "content" to "My password is Secret123 and pin code 4433"))

        assertFalse(result.success)
        assertTrue(result.message.contains("cannot store passwords") || result.message.contains("security"))
        assertEquals(0, dao.notes.size)
    }

    @Test
    fun testSearchNotes_findsMatching() = runBlocking {
        repo.createNote("Java Topics", "Study JVM and garbage collection")
        repo.createNote("Shopping List", "Milk, bread, eggs")

        val tool = SearchNotesTool(repo)
        val result = tool.execute(mapOf("query" to "Java"))

        assertTrue(result.success)
        assertTrue(result.message.contains("Java Topics"))
    }

    @Test
    fun testListNotes_returnsAll() = runBlocking {
        repo.createNote("Note 1", "Content 1")
        repo.createNote("Note 2", "Content 2")

        val tool = ListNotesTool(repo)
        val result = tool.execute(emptyMap())

        assertTrue(result.success)
        assertTrue(result.message.contains("Note 1"))
        assertTrue(result.message.contains("Note 2"))
    }

    @Test
    fun testUpdateNote_appendsContent() = runBlocking {
        val createRes = repo.createNote("Java Note", "Spring Boot")
        val tool = UpdateNoteTool(repo)

        val result = tool.execute(mapOf("title" to "Java Note", "content" to "and Hibernate", "append" to true))
        assertTrue(result.success)

        val updated = repo.findByTitle("Java Note")
        assertTrue(updated?.content?.contains("Hibernate") == true)
    }

    @Test
    fun testDeleteNote_removesFromStorage() = runBlocking {
        repo.createNote("Temporary Note", "To delete")
        val tool = DeleteNoteTool(repo)

        val result = tool.execute(mapOf("title" to "Temporary Note"))
        assertTrue(result.success)
        assertEquals(0, dao.notes.size)
    }
}
