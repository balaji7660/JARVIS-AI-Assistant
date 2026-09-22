package com.jarvis.assistant.ai

import com.jarvis.assistant.tools.ToolCall
import com.jarvis.assistant.tools.ToolRouter

interface ResponseEngine {
    suspend fun generateResponse(prompt: String): String
}

class LocalResponseEngine(
    private val toolRouter: ToolRouter? = null
) : ResponseEngine {

    override suspend fun generateResponse(prompt: String): String {
        val normalized = normalizeInput(prompt)

        // 1. Tool intent matching if ToolRouter is available
        if (toolRouter != null) {
            when {
                isTimeQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("get_time"))
                    return "The current time is ${res.message}, boss."
                }
                isDateQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("get_date"))
                    return "Today is ${res.message}, boss."
                }
                isHomeQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("go_home"))
                    return if (res.success) "Returning to home screen, boss." else res.message
                }
                isBackQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("press_back"))
                    return if (res.success) "Navigating back, boss." else res.message
                }
                isOpenUrlQuery(normalized) -> {
                    val url = extractUrl(prompt)
                    val res = toolRouter.dispatch(ToolCall("open_url", mapOf("url" to url)))
                    return if (res.success) "Opening $url, boss." else res.message
                }
                isOpenAppQuery(normalized) -> {
                    val appName = extractAppName(normalized)
                    val res = toolRouter.dispatch(ToolCall("open_app", mapOf("appName" to appName)))
                    return res.message
                }
                isScreenAnalysisQuery(normalized) -> {
                    val focus = extractScreenFocus(prompt)
                    val args = if (focus != null) mapOf("focus" to focus) else emptyMap()
                    val res = toolRouter.dispatch(ToolCall("analyze_current_screen", args))
                    return res.message
                }
            }
        }

        // 2. Standard rule-based fallback responses
        return when {
            normalized.isEmpty() -> {
                "I didn't catch that, boss. How can I assist you?"
            }
            isGreeting(normalized) -> {
                "Hello boss. I'm ready."
            }
            isIdentityQuery(normalized) -> {
                "I am JARVIS, your personal AI assistant."
            }
            isCapabilitiesQuery(normalized) -> {
                "I am your JARVIS AI assistant, boss. I can automate apps, open settings, navigate your screen, answer questions, and manage tasks."
            }
            else -> {
                "I heard you, boss. I'm currently operating in on-device mode. To enable full GPT cloud AI responses, ensure your OPENAI_API_KEY is configured on your Render server."
            }
        }
    }

    private fun normalizeInput(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\s:/.]"), "")
            .replace(Regex("\\s+"), " ")
    }

    private fun isTimeQuery(input: String): Boolean {
        return input in setOf(
            "what time is it",
            "what is the time",
            "tell me the time",
            "time",
            "what time is it jarvis"
        )
    }

    private fun isDateQuery(input: String): Boolean {
        return input in setOf(
            "what is todays date",
            "what is today date",
            "what date is it",
            "tell me the date",
            "date",
            "what is the date today"
        )
    }

    private fun isHomeQuery(input: String): Boolean {
        return input in setOf(
            "go home",
            "go to home screen",
            "home screen",
            "return home"
        )
    }

    private fun isBackQuery(input: String): Boolean {
        return input in setOf(
            "press back",
            "go back",
            "back"
        )
    }

    private fun isOpenUrlQuery(input: String): Boolean {
        return input.startsWith("open url") || input.startsWith("open http") || input.contains("https://") || input.contains("http://")
    }

    private fun extractUrl(input: String): String {
        val parts = input.trim().split(Regex("\\s+"))
        for (p in parts) {
            if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) {
                return p
            }
        }
        return parts.lastOrNull() ?: ""
    }

    private fun isOpenAppQuery(input: String): Boolean {
        return input.startsWith("open app ") || (input.startsWith("open ") && !input.contains("http"))
    }

    private fun extractAppName(input: String): String {
        return if (input.startsWith("open app ")) {
            input.removePrefix("open app ").trim()
        } else if (input.startsWith("open ")) {
            input.removePrefix("open ").trim()
        } else {
            input
        }
    }

    private fun isGreeting(input: String): Boolean {
        return input in setOf(
            "hello",
            "hello jarvis",
            "hey",
            "hey jarvis",
            "hi",
            "hi jarvis",
            "good morning",
            "good morning jarvis",
            "good evening",
            "good evening jarvis"
        )
    }

    private fun isIdentityQuery(input: String): Boolean {
        return input in setOf(
            "who are you",
            "who are you jarvis",
            "what is your name",
            "tell me who you are"
        )
    }

    private fun isCapabilitiesQuery(input: String): Boolean {
        return input in setOf(
            "what can you do",
            "what can you do jarvis",
            "what are your features",
            "help"
        )
    }

    private fun isScreenAnalysisQuery(input: String): Boolean {
        return input.contains("what is on my screen") ||
                input.contains("whats on my screen") ||
                input.contains("look at the screen") ||
                input.contains("read this screen") ||
                input.contains("read the screen") ||
                input.contains("analyze my screen") ||
                input.startsWith("find the ") ||
                input.startsWith("where is the ")
    }

    private fun extractScreenFocus(prompt: String): String? {
        val lower = prompt.lowercase().trim()
        return when {
            lower.contains("find the ") -> prompt.substring(lower.indexOf("find the ") + 9).trim().removeSuffix(".")
            lower.contains("where is the ") -> prompt.substring(lower.indexOf("where is the ") + 13).trim().removeSuffix("?").removeSuffix(".")
            else -> null
        }
    }
}
