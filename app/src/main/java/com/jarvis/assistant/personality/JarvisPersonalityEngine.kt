package com.jarvis.assistant.personality

/**
 * JARVIS 2.0 Personality Engine.
 * Enforces a consistent, confident, concise, futuristic, and respectful tone.
 * Eliminates repetitive filler words ("Sure!", "Of course!", "Certainly!") on every turn.
 * Avoids excessive jokes, robotic repetitions, or verbose monologues.
 */
object JarvisPersonalityEngine {

    private val GREETINGS = listOf(
        "Yes, boss?",
        "I'm here, boss.",
        "Listening, boss.",
        "Ready, boss."
    )

    private val TASK_ACKS = listOf(
        "On it, boss.",
        "Working on that.",
        "Right away, boss.",
        "Processing now."
    )

    private val SEARCHING_ACKS = listOf(
        "Searching now.",
        "Searching now, boss.",
        "Looking into that."
    )

    private val COMPLETION_PHRASES = listOf(
        "Done, boss.",
        "Completed, boss.",
        "All set, boss."
    )

    private val INTERRUPTION_ACKS = listOf(
        "Stopped, boss.",
        "Task cancelled, boss.",
        "Standing by, boss."
    )

    fun getWakeGreeting(): String {
        return GREETINGS.random()
    }

    fun getTaskAck(): String {
        return TASK_ACKS.random()
    }

    fun getSearchingAck(): String {
        return SEARCHING_ACKS.random()
    }

    fun getInterruptionAck(): String {
        return INTERRUPTION_ACKS.random()
    }

    fun getTaskCompletion(toolMessage: String? = null): String {
        if (!toolMessage.isNullOrBlank()) {
            return deduplicate(toolMessage)
        }
        return COMPLETION_PHRASES.random()
    }

    fun getTaskFailure(reason: String): String {
        val clean = reason.removePrefix("Error:").removePrefix("Failed:").trim()
        return "I couldn't complete that. $clean"
    }

    fun formatConfirmation(actionDescription: String): String {
        val clean = actionDescription.trim().removeSuffix(".")
        return "Do you want me to $clean?"
    }

    fun formatCallConfirmation(contactName: String): String {
        val clean = contactName.trim().removeSuffix(".")
        return "Call $clean?"
    }

    fun formatRevisionConfirmation(newTarget: String): String {
        val clean = newTarget.trim().removeSuffix(".")
        return "Okay. Call $clean?"
    }

    fun formatClarification(prompt: String, options: List<String> = emptyList()): String {
        if (options.isEmpty()) {
            return "$prompt, boss?"
        }
        val optionsSummary = options.joinToString(" or ") { "\"$it\"" }
        return "$prompt Which one: $optionsSummary?"
    }

    fun formatMorningBriefing(
        remindersCount: Int,
        calendarEvents: List<String>,
        batteryPercent: Int
    ): String {
        val reminderText = when (remindersCount) {
            0 -> "No pending reminders."
            1 -> "1 reminder."
            else -> "$remindersCount reminders."
        }

        val eventText = when {
            calendarEvents.isEmpty() -> "No events on your calendar today."
            calendarEvents.size == 1 -> "1 calendar event: ${calendarEvents[0]}."
            else -> "${calendarEvents.size} calendar events scheduled today."
        }

        return "Good morning, boss.\n\nYou have:\n• $reminderText\n• $eventText\n• Battery at $batteryPercent%.\n\nYou're ready to go."
    }

    /**
     * Deduplicates phrases and strips unnecessary robotic fluff.
     */
    fun deduplicate(rawMessage: String): String {
        var clean = rawMessage.trim()

        // Remove duplicate opening clichés
        val prefixesToRemove = listOf(
            "Sure! ", "Sure, ", "Certainly! ", "Certainly, ",
            "Of course! ", "Of course, ", "I'd be happy to help! ",
            "I have successfully ", "Successfully ",
            "Operation completed successfully. ",
            "Action completed successfully. "
        )

        for (prefix in prefixesToRemove) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
            }
        }

        // Deduplicate repeated sentences (e.g. "Chrome opened. Chrome opened successfully.")
        val sentences = clean.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size > 1) {
            val uniqueSentences = mutableListOf<String>()
            val seenSignatures = mutableSetOf<String>()
            for (s in sentences) {
                val signature = s.lowercase().replace(Regex("[^a-z0-9]"), "")
                // Check if this sentence essentially repeats an earlier one
                val isRedundant = seenSignatures.any { existing ->
                    existing.contains(signature) || signature.contains(existing)
                }
                if (!isRedundant) {
                    uniqueSentences.add(s)
                    seenSignatures.add(signature)
                }
            }
            clean = uniqueSentences.joinToString(" ")
        }

        // Capitalize first letter
        return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
