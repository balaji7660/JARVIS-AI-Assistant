package com.jarvis.assistant.memory

import android.util.Log

/**
 * Result of note creation/update with security safety validation.
 */
sealed class NoteResult {
    data class Success(val note: NoteEntity, val message: String) : NoteResult()
    data class SecurityRejection(val reason: String) : NoteResult()
    data class Failure(val error: String) : NoteResult()
}

/**
 * Safe local repository for notes with built-in credential protection.
 */
class NoteRepository(
    private val noteDao: NoteDao
) {

    companion object {
        private const val TAG = "NoteRepository"

        private val FORBIDDEN_CREDENTIAL_PATTERNS = listOf(
            "password", "passwd", "pin code", "otp", "one-time password",
            "credit card", "debit card", "cvv", "security code",
            "api_key", "secret_key", "auth_token", "private_key"
        )
    }

    private fun containsForbiddenCredentials(text: String): String? {
        val lower = text.lowercase()
        for (pattern in FORBIDDEN_CREDENTIAL_PATTERNS) {
            if (lower.contains(pattern)) {
                return pattern
            }
        }
        return null
    }

    suspend fun createNote(title: String, content: String, tags: String = ""): NoteResult {
        val trimmedTitle = title.trim().ifBlank { "Untitled Note" }
        val trimmedContent = content.trim()

        val credentialInTitle = containsForbiddenCredentials(trimmedTitle)
        val credentialInContent = containsForbiddenCredentials(trimmedContent)

        if (credentialInTitle != null || credentialInContent != null) {
            val pattern = credentialInTitle ?: credentialInContent
            Log.w(TAG, "Refusing to store sensitive credential ('$pattern') in local notes.")
            return NoteResult.SecurityRejection(
                "For your security, JARVIS cannot store passwords, PINs, OTPs, or financial credentials in notes."
            )
        }

        return try {
            val entity = NoteEntity(
                title = trimmedTitle,
                content = trimmedContent,
                tags = tags.trim(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val id = noteDao.insert(entity)
            NoteResult.Success(entity.copy(id = id), "Note \"$trimmedTitle\" saved successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert note", e)
            NoteResult.Failure("Failed to save note: ${e.message}")
        }
    }

    suspend fun searchNotes(query: String): List<NoteEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return noteDao.getAllNotes()
        return noteDao.searchNotes(trimmed)
    }

    suspend fun getAllNotes(): List<NoteEntity> {
        return noteDao.getAllNotes()
    }

    suspend fun getNoteById(id: Long): NoteEntity? {
        return noteDao.getNoteById(id)
    }

    suspend fun findByTitle(title: String): NoteEntity? {
        return noteDao.findByTitle(title.trim())
    }

    suspend fun updateNote(id: Long, newTitle: String?, newContent: String?, append: Boolean = false): NoteResult {
        val existing = noteDao.getNoteById(id)
            ?: return NoteResult.Failure("Note not found.")

        val finalTitle = newTitle?.trim() ?: existing.title
        val finalContent = if (newContent != null) {
            if (append) {
                "${existing.content}\n${newContent.trim()}".trim()
            } else {
                newContent.trim()
            }
        } else {
            existing.content
        }

        val credentialInTitle = containsForbiddenCredentials(finalTitle)
        val credentialInContent = containsForbiddenCredentials(finalContent)
        if (credentialInTitle != null || credentialInContent != null) {
            return NoteResult.SecurityRejection(
                "For your security, JARVIS cannot store sensitive credentials in notes."
            )
        }

        return try {
            val updated = existing.copy(
                title = finalTitle,
                content = finalContent,
                updatedAt = System.currentTimeMillis()
            )
            noteDao.update(updated)
            NoteResult.Success(updated, "Note \"$finalTitle\" updated successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update note", e)
            NoteResult.Failure("Failed to update note: ${e.message}")
        }
    }

    suspend fun deleteNote(id: Long): Boolean {
        return try {
            noteDao.deleteById(id) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete note by id $id", e)
            false
        }
    }
}
