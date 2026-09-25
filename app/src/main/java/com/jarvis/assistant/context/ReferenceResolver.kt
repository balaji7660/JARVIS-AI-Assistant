package com.jarvis.assistant.context

/**
 * Result of attempting to resolve a conversational or deictic reference (e.g., "it", "the first result").
 */
sealed class ReferenceResolutionResult {
    /**
     * Unambiguously resolved to a single target screen element.
     */
    data class Resolved(
        val target: ScreenElementContext,
        val referenceText: String
    ) : ReferenceResolutionResult()

    /**
     * Multiple potential candidates match the reference.
     * The assistant MUST NOT guess; it must ask the user for clarification.
     */
    data class Ambiguous(
        val candidates: List<ScreenElementContext>,
        val query: String
    ) : ReferenceResolutionResult()

    /**
     * No matching target could be found in the current context or screen state.
     */
    object NotFound : ReferenceResolutionResult()
}

/**
 * Resolves conversational references ("it", "that", "the first result", "the button")
 * against the active [ConversationContext] and visible screen elements.
 *
 * Implements strict ambiguity safety: if multiple candidates match, returns [ReferenceResolutionResult.Ambiguous]
 * rather than guessing.
 */
class ReferenceResolver {

    /**
     * Evaluates a natural language user query against the conversational context to resolve references.
     */
    fun resolve(query: String, context: ConversationContext): ReferenceResolutionResult {
        val lower = query.lowercase().trim()
        val elements = context.detectedElements

        // 1. Ordinal Result references: "the first result", "the first one", "first item"
        val ordinalIndex = extractOrdinalIndex(lower)
        if (ordinalIndex != null) {
            val resultCandidates = elements.filter {
                it.type.equals("result", ignoreCase = true) ||
                it.type.equals("item", ignoreCase = true)
            }.ifEmpty { elements }

            val targetIdx = if (ordinalIndex == -1) resultCandidates.lastIndex else ordinalIndex
            return if (targetIdx in resultCandidates.indices) {
                ReferenceResolutionResult.Resolved(
                    target = resultCandidates[targetIdx],
                    referenceText = if (ordinalIndex == -1) "last result" else "result #${targetIdx + 1}"
                )
            } else {
                ReferenceResolutionResult.NotFound
            }
        }

        // 2. Specific component references: "the button", "that button"
        if (lower.contains("button")) {
            val buttonCandidates = elements.filter {
                it.type.equals("button", ignoreCase = true) ||
                it.label.contains("button", ignoreCase = true)
            }

            return when {
                buttonCandidates.size == 1 -> ReferenceResolutionResult.Resolved(
                    target = buttonCandidates.first(),
                    referenceText = "button"
                )
                buttonCandidates.size > 1 -> ReferenceResolutionResult.Ambiguous(
                    candidates = buttonCandidates,
                    query = query
                )
                else -> ReferenceResolutionResult.NotFound
            }
        }

        // 2b. Contact reference: "that contact", "the contact"
        if (lower.contains("that contact") || lower.contains("the contact") || lower == "contact") {
            val contactName = context.recentEntities.firstOrNull() ?: context.currentTarget
            return if (!contactName.isNullOrBlank()) {
                ReferenceResolutionResult.Resolved(
                    target = ScreenElementContext(label = contactName, type = "contact"),
                    referenceText = contactName
                )
            } else {
                ReferenceResolutionResult.NotFound
            }
        }

        // 2c. Message reference: "that message", "the message"
        if (lower.contains("that message") || lower.contains("the message") || lower == "message") {
            val messageText = context.recentEntities.firstOrNull() ?: context.currentTarget
            return if (!messageText.isNullOrBlank()) {
                ReferenceResolutionResult.Resolved(
                    target = ScreenElementContext(label = messageText, type = "message"),
                    referenceText = messageText
                )
            } else {
                ReferenceResolutionResult.NotFound
            }
        }

        // 3. App reference: "the app", "that app", "the current app"
        if (lower.contains("the app") || lower.contains("that app") || lower.contains("the current app") || lower == "app") {
            val app = context.currentApp ?: context.currentPackage
            return if (app != null) {
                ReferenceResolutionResult.Resolved(
                    target = ScreenElementContext(
                        label = app,
                        type = "app",
                        viewId = context.currentPackage
                    ),
                    referenceText = app
                )
            } else {
                ReferenceResolutionResult.NotFound
            }
        }

        // 3b. Page reference: "the page", "that page", "this page", "the current page"
        if (lower.contains("the page") || lower.contains("that page") || lower.contains("this page") || lower.contains("the current page") || lower == "page") {
            val page = context.currentScreenSummary ?: context.currentApp
            return if (page != null) {
                ReferenceResolutionResult.Resolved(
                    target = ScreenElementContext(
                        label = page,
                        type = "page",
                        viewId = context.currentPackage
                    ),
                    referenceText = page
                )
            } else {
                ReferenceResolutionResult.NotFound
            }
        }

        // 4. Pronouns & deictic references: "it", "that", "this", "there", "like that"
        if (isPronounReference(lower)) {
            // First check if an explicit currentTarget was already established
            if (!context.currentTarget.isNullOrBlank()) {
                val matched = elements.firstOrNull { it.label.equals(context.currentTarget, ignoreCase = true) }
                    ?: ScreenElementContext(label = context.currentTarget, type = "target")
                return ReferenceResolutionResult.Resolved(matched, context.currentTarget)
            }

            // Otherwise evaluate currently detected elements
            return when {
                elements.size == 1 -> ReferenceResolutionResult.Resolved(elements.first(), elements.first().label)
                elements.size > 1 -> ReferenceResolutionResult.Ambiguous(elements, query)
                else -> ReferenceResolutionResult.NotFound
            }
        }

        return ReferenceResolutionResult.NotFound
    }

    private fun extractOrdinalIndex(lower: String): Int? {
        return when {
            lower.contains("first result") || lower.contains("1st result") ||
            lower.contains("first one") || lower.contains("1st one") ||
            lower.contains("the first") || lower.contains("first item") -> 0

            lower.contains("second result") || lower.contains("2nd result") ||
            lower.contains("second one") || lower.contains("2nd one") ||
            lower.contains("the second") || lower.contains("second item") -> 1

            lower.contains("third result") || lower.contains("3rd result") ||
            lower.contains("third one") || lower.contains("3rd one") ||
            lower.contains("the third") || lower.contains("third item") -> 2

            lower.contains("fourth result") || lower.contains("4th result") ||
            lower.contains("fourth one") -> 3

            lower.contains("fifth result") || lower.contains("5th result") ||
            lower.contains("fifth one") -> 4

            lower.contains("last result") || lower.contains("the last result") ||
            lower.contains("last one") || lower.contains("the last one") ||
            lower.contains("previous result") || lower.contains("previous one") -> -1

            else -> null
        }
    }

    private fun isPronounReference(lower: String): Boolean {
        return lower == "it" || lower == "that" || lower == "this" || lower == "there" ||
                lower == "like that" || lower.startsWith("like that") ||
                lower.startsWith("open it") || lower.startsWith("open that") || lower.startsWith("open this") ||
                lower.startsWith("click it") || lower.startsWith("click that") || lower.startsWith("click this") ||
                lower.startsWith("tap it") || lower.startsWith("tap that") || lower.startsWith("tap this") ||
                lower.startsWith("select it") || lower.startsWith("select that") || lower.startsWith("select this") ||
                lower.contains(" open that") || lower.contains(" click that") || lower.contains(" click it")
    }

    fun isCancellationFollowUp(query: String): Boolean {
        val lower = query.lowercase().trim()
        return lower.startsWith("actually, don't") ||
                lower.startsWith("don't ") ||
                lower.startsWith("cancel ") ||
                lower == "cancel" ||
                lower == "never mind" ||
                lower == "forget it" ||
                lower == "stop" ||
                lower == "no" ||
                lower == "wait"
    }

    companion object {
        /**
         * Detects if the user is revising or correcting a previous command/confirmation (e.g. "No, Mom", "Actually open YouTube").
         */
        fun isTaskCorrection(query: String): Boolean {
            val lower = query.lowercase().trim()
            return lower.startsWith("no, ") ||
                    lower.startsWith("no ") ||
                    lower.startsWith("actually ") ||
                    lower.startsWith("instead ") ||
                    lower.startsWith("change to ") ||
                    lower.startsWith("i meant ")
        }

        /**
         * Extracts the new corrected command or argument from a revision query.
         */
        fun extractCorrectionTarget(query: String): String {
            val clean = query.trim()
            val lower = clean.lowercase()
            return when {
                lower.startsWith("no, ") -> clean.substring(4).trim()
                lower.startsWith("no ") -> clean.substring(3).trim()
                lower.startsWith("actually ") -> clean.substring(9).trim()
                lower.startsWith("instead ") -> clean.substring(8).trim()
                lower.startsWith("change to ") -> clean.substring(10).trim()
                lower.startsWith("i meant ") -> clean.substring(8).trim()
                else -> clean
            }
        }
    }
}
