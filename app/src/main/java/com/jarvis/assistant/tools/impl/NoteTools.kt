package com.jarvis.assistant.tools.impl

import com.jarvis.assistant.memory.NoteRepository
import com.jarvis.assistant.memory.NoteResult
import com.jarvis.assistant.tools.JarvisTool
import com.jarvis.assistant.tools.RiskLevel
import com.jarvis.assistant.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Note management tools running 100% on-device with Room database persistence.
 */

class CreateNoteTool(
    private val noteRepository: NoteRepository
) : JarvisTool {

    override val name: String = "create_note"
    override val description: String = "Creates and saves a new personal note on device (e.g. 'Create a note called Java Interview with Spring Boot questions')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val title = (arguments["title"] as? String)?.trim() ?: "Untitled Note"
        val content = (arguments["content"] as? String ?: arguments["text"] as? String ?: arguments["body"] as? String)?.trim() ?: ""
        val tags = (arguments["tags"] as? String)?.trim() ?: ""

        if (title.isBlank() && content.isBlank()) {
            return@withContext ToolResult(
                success = false,
                message = "Note title or content cannot be completely empty, boss."
            )
        }

        when (val result = noteRepository.createNote(title, content, tags)) {
            is NoteResult.Success -> {
                ToolResult(
                    success = true,
                    message = "I have saved your note: \"${result.note.title}\".",
                    data = mapOf(
                        "noteId" to result.note.id,
                        "title" to result.note.title,
                        "content" to result.note.content
                    )
                )
            }
            is NoteResult.SecurityRejection -> {
                ToolResult(
                    success = false,
                    message = result.reason
                )
            }
            is NoteResult.Failure -> {
                ToolResult(
                    success = false,
                    message = "I couldn't save the note, boss: ${result.error}"
                )
            }
        }
    }
}

class SearchNotesTool(
    private val noteRepository: NoteRepository
) : JarvisTool {

    override val name: String = "search_notes"
    override val description: String = "Searches local notes for keywords, topics, or titles (e.g. 'Search notes for Spring Boot')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val query = (arguments["query"] as? String ?: arguments["keyword"] as? String ?: arguments["title"] as? String)?.trim() ?: ""

        val results = noteRepository.searchNotes(query)
        if (results.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = if (query.isNotBlank()) "I couldn't find any notes matching \"$query\", boss." else "You don't have any notes saved yet, boss."
            )
        }

        val count = results.size
        val noteListSummary = results.take(5).joinToString("\n") { n ->
            "• \"${n.title}\": ${n.content.take(80)}"
        }

        val message = if (count == 1) {
            val single = results[0]
            "I found your note \"${single.title}\":\n${single.content}"
        } else {
            "Found $count notes:\n$noteListSummary"
        }

        ToolResult(
            success = true,
            message = message,
            data = mapOf(
                "count" to count,
                "notes" to results.map { mapOf("id" to it.id, "title" to it.title, "content" to it.content) }
            )
        )
    }
}

class ListNotesTool(
    private val noteRepository: NoteRepository
) : JarvisTool {

    override val name: String = "list_notes"
    override val description: String = "Lists all saved personal notes."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val notes = noteRepository.getAllNotes()
        if (notes.isEmpty()) {
            return@withContext ToolResult(
                success = true,
                message = "You don't have any saved notes right now, boss."
            )
        }

        val summary = notes.take(8).joinToString("\n") { n ->
            "• ${n.title}"
        }
        ToolResult(
            success = true,
            message = "You have ${notes.size} notes:\n$summary",
            data = mapOf("count" to notes.size)
        )
    }
}

class UpdateNoteTool(
    private val noteRepository: NoteRepository
) : JarvisTool {

    override val name: String = "update_note"
    override val description: String = "Appends to or updates an existing note by title or ID (e.g. 'Add Spring Boot to Java note')."
    override val riskLevel: RiskLevel = RiskLevel.LOW

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawId = arguments["noteId"]
        val id = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        val title = (arguments["title"] as? String)?.trim()
        val newContent = (arguments["content"] as? String ?: arguments["text"] as? String)?.trim()
        val append = (arguments["append"] as? Boolean) ?: true

        val targetNote = if (id != null) {
            noteRepository.getNoteById(id)
        } else if (!title.isNullOrBlank()) {
            noteRepository.findByTitle(title)
        } else null

        if (targetNote == null) {
            return@withContext ToolResult(
                success = false,
                message = "I couldn't find the note to update, boss."
            )
        }

        when (val result = noteRepository.updateNote(targetNote.id, null, newContent, append)) {
            is NoteResult.Success -> {
                ToolResult(
                    success = true,
                    message = "Updated your note \"${targetNote.title}\" successfully.",
                    data = mapOf("id" to targetNote.id, "title" to targetNote.title)
                )
            }
            is NoteResult.SecurityRejection -> {
                ToolResult(
                    success = false,
                    message = result.reason
                )
            }
            is NoteResult.Failure -> {
                ToolResult(
                    success = false,
                    message = "Failed to update note: ${result.error}"
                )
            }
        }
    }
}

class DeleteNoteTool(
    private val noteRepository: NoteRepository
) : JarvisTool {

    override val name: String = "delete_note"
    override val description: String = "Deletes a saved note by title or ID (requires confirmation)."
    override val riskLevel: RiskLevel = RiskLevel.HIGH

    override suspend fun execute(arguments: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val rawId = arguments["noteId"]
        val id = when (rawId) {
            is Number -> rawId.toLong()
            is String -> rawId.toLongOrNull()
            else -> null
        }
        val title = (arguments["title"] as? String)?.trim()

        val targetNote = if (id != null) {
            noteRepository.getNoteById(id)
        } else if (!title.isNullOrBlank()) {
            noteRepository.findByTitle(title)
        } else null

        if (targetNote == null) {
            return@withContext ToolResult(
                success = false,
                message = "I couldn't find a note matching \"${title ?: id}\" to delete, boss."
            )
        }

        val deleted = noteRepository.deleteNote(targetNote.id)
        if (deleted) {
            ToolResult(
                success = true,
                message = "Note \"${targetNote.title}\" has been deleted.",
                data = mapOf("deletedId" to targetNote.id)
            )
        } else {
            ToolResult(
                success = false,
                message = "Failed to delete note \"${targetNote.title}\"."
            )
        }
    }
}
