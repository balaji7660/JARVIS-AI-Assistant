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
                isCallQuery(normalized) -> {
                    val contactName = extractContactName(normalized)
                    val res = toolRouter.dispatch(ToolCall("call_contact", mapOf("contactName" to contactName)))
                    return res.message
                }
                isScreenAnalysisQuery(normalized) -> {
                    val focus = extractScreenFocus(prompt)
                    val args = if (focus != null) mapOf("focus" to focus) else emptyMap()
                    val res = toolRouter.dispatch(ToolCall("analyze_current_screen", args))
                    return res.message
                }
                isBatteryQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("get_battery_status"))
                    return res.message
                }
                isFlashlightQuery(normalized) -> {
                    val action = if (normalized.contains("off")) "off" else if (normalized.contains("on")) "on" else "toggle"
                    val res = toolRouter.dispatch(ToolCall("toggle_flashlight", mapOf("action" to action)))
                    return res.message
                }
                isVolumeQuery(normalized) -> {
                    val args = extractVolumeArgs(normalized)
                    val res = toolRouter.dispatch(ToolCall("set_volume", args))
                    return res.message
                }
                isBrightnessQuery(normalized) -> {
                    val args = extractBrightnessArgs(normalized)
                    val res = toolRouter.dispatch(ToolCall("set_brightness", args))
                    return res.message
                }
                isCameraQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("open_camera"))
                    return res.message
                }
                isMediaQuery(normalized) -> {
                    val toolName = when {
                        normalized.contains("pause") || normalized.contains("stop music") -> "pause_media"
                        normalized.contains("resume") -> "resume_media"
                        normalized.contains("next") -> "next_track"
                        normalized.contains("previous") || normalized.contains("prev") -> "previous_track"
                        else -> "play_media"
                    }
                    val res = toolRouter.dispatch(ToolCall(toolName))
                    return res.message
                }
                isWifiSettingsQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("open_wifi_settings"))
                    return res.message
                }
                isBluetoothSettingsQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("open_bluetooth_settings"))
                    return res.message
                }
                isLockScreenQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("lock_screen"))
                    return res.message
                }
                isDeviceInfoQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("get_device_info"))
                    return res.message
                }
                isNetworkQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("get_network_status"))
                    return res.message
                }
                isSmsQuery(normalized) -> {
                    val args = extractSmsArgs(prompt)
                    val res = toolRouter.dispatch(ToolCall("send_sms", args))
                    return res.message
                }
                isReadScreenQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("read_current_screen"))
                    return res.message
                }
                isDiagnoseErrorQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("diagnose_screen_error"))
                    return res.message
                }
                isSearchYouTubeQuery(normalized) -> {
                    val query = extractYouTubeQuery(normalized)
                    val res = toolRouter.dispatch(ToolCall("search_youtube", mapOf("query" to query)))
                    return res.message
                }
                isSearchWebQuery(normalized) -> {
                    val query = extractWebQuery(normalized)
                    val res = toolRouter.dispatch(ToolCall("search_web", mapOf("query" to query)))
                    return res.message
                }
                isTimerQuery(normalized) -> {
                    val args = extractTimerArgs(normalized)
                    val res = toolRouter.dispatch(ToolCall("create_timer", args))
                    return res.message
                }
                isCancelTimerQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("cancel_timer"))
                    return res.message
                }
                isListTimersQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("list_timers"))
                    return res.message
                }
                isCreateReminderQuery(normalized) -> {
                    val args = extractReminderArgs(prompt)
                    val res = toolRouter.dispatch(ToolCall("create_reminder", args))
                    return res.message
                }
                isListRemindersQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("list_reminders"))
                    return res.message
                }
                isCancelReminderQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("cancel_reminder"))
                    return res.message
                }
                isCreateNoteQuery(normalized) -> {
                    val args = extractNoteArgs(prompt)
                    val res = toolRouter.dispatch(ToolCall("create_note", args))
                    return res.message
                }
                isSearchNoteQuery(normalized) -> {
                    val query = extractNoteSearchQuery(normalized)
                    val res = toolRouter.dispatch(ToolCall("search_notes", mapOf("query" to query)))
                    return res.message
                }
                isListNotesQuery(normalized) -> {
                    val res = toolRouter.dispatch(ToolCall("list_notes"))
                    return res.message
                }
                isListCalendarQuery(normalized) -> {
                    val range = if (normalized.contains("tomorrow")) "tomorrow" else "today"
                    val res = toolRouter.dispatch(ToolCall("list_calendar_events", mapOf("range" to range)))
                    return res.message
                }
                isMorningBriefingQuery(normalized) -> {
                    val batteryRes = toolRouter.dispatch(ToolCall("get_battery_status"))
                    val reminderRes = toolRouter.dispatch(ToolCall("list_reminders"))
                    val calRes = toolRouter.dispatch(ToolCall("list_calendar_events", mapOf("range" to "today")))

                    val remCount = (reminderRes.data["count"] as? Number)?.toInt() ?: 0
                    val calCount = (calRes.data["count"] as? Number)?.toInt() ?: 0
                    val calList = if (calCount > 0) listOf("$calCount events scheduled") else emptyList<String>()
                    val batteryPct = (batteryRes.data["percentage"] as? Number)?.toInt() ?: 80

                    return com.jarvis.assistant.personality.JarvisPersonalityEngine.formatMorningBriefing(
                        remindersCount = remCount,
                        calendarEvents = calList,
                        batteryPercent = batteryPct
                    )
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
                "JARVIS cloud intelligence is unavailable."
            }
        }
    }

    fun canHandleLocally(prompt: String): Boolean {
        val normalized = normalizeInput(prompt)
        return isTimeQuery(normalized) ||
               isDateQuery(normalized) ||
               isHomeQuery(normalized) ||
               isBackQuery(normalized) ||
               isOpenUrlQuery(normalized) ||
               isOpenAppQuery(normalized) ||
               isCallQuery(normalized) ||
               isScreenAnalysisQuery(normalized) ||
               isBatteryQuery(normalized) ||
               isFlashlightQuery(normalized) ||
               isVolumeQuery(normalized) ||
               isBrightnessQuery(normalized) ||
               isCameraQuery(normalized) ||
               isMediaQuery(normalized) ||
               isWifiSettingsQuery(normalized) ||
               isBluetoothSettingsQuery(normalized) ||
               isLockScreenQuery(normalized) ||
               isDeviceInfoQuery(normalized) ||
               isNetworkQuery(normalized) ||
               isSmsQuery(normalized) ||
               isReadScreenQuery(normalized) ||
               isDiagnoseErrorQuery(normalized) ||
               isSearchYouTubeQuery(normalized) ||
               isSearchWebQuery(normalized) ||
               isTimerQuery(normalized) ||
               isCancelTimerQuery(normalized) ||
               isListTimersQuery(normalized) ||
               isCreateReminderQuery(normalized) ||
               isListRemindersQuery(normalized) ||
               isCancelReminderQuery(normalized) ||
               isCreateNoteQuery(normalized) ||
               isSearchNoteQuery(normalized) ||
               isListNotesQuery(normalized) ||
               isListCalendarQuery(normalized) ||
               isMorningBriefingQuery(normalized) ||
               isGreeting(normalized) ||
               isIdentityQuery(normalized) ||
               isCapabilitiesQuery(normalized)
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

    private fun isCallQuery(input: String): Boolean {
        return input.startsWith("call ") ||
                input.startsWith("phone ") ||
                input.startsWith("dial ") ||
                input.startsWith("make a call to ")
    }

    private fun extractContactName(input: String): String {
        return when {
            input.startsWith("make a call to ") -> input.removePrefix("make a call to ").trim()
            input.startsWith("call ") -> input.removePrefix("call ").trim()
            input.startsWith("phone ") -> input.removePrefix("phone ").trim()
            input.startsWith("dial ") -> input.removePrefix("dial ").trim()
            else -> input.trim()
        }
    }

    private fun isBatteryQuery(input: String): Boolean =
        input.contains("battery") || input.contains("charge level")

    private fun isFlashlightQuery(input: String): Boolean =
        input.contains("flashlight") || input.contains("torch")

    private fun isVolumeQuery(input: String): Boolean =
        input.contains("volume") || input == "mute" || input == "unmute"

    private fun extractVolumeArgs(input: String): Map<String, Any?> {
        val numberRegex = Regex("(\\d+)")
        val match = numberRegex.find(input)
        return when {
            input == "mute" || input.contains("mute") -> mapOf("direction" to "mute")
            input == "unmute" || input.contains("unmute") -> mapOf("direction" to "unmute")
            input.contains("up") || input.contains("raise") || input.contains("increase") -> mapOf("direction" to "up")
            input.contains("down") || input.contains("lower") || input.contains("decrease") -> mapOf("direction" to "down")
            match != null -> mapOf("volumePercent" to (match.groupValues[1].toIntOrNull() ?: 50))
            else -> mapOf("direction" to "up")
        }
    }

    private fun isBrightnessQuery(input: String): Boolean =
        input.contains("brightness")

    private fun extractBrightnessArgs(input: String): Map<String, Any?> {
        val numberRegex = Regex("(\\d+)")
        val match = numberRegex.find(input)
        val percent = match?.groupValues?.get(1)?.toIntOrNull() ?: 50
        return mapOf("brightnessPercent" to percent)
    }

    private fun isCameraQuery(input: String): Boolean =
        input == "open camera" || input == "launch camera" || input == "take a picture" || input == "take a photo"

    private fun isMediaQuery(input: String): Boolean =
        input.contains("music") || input.contains("media") || input.contains("song") ||
        input == "play" || input == "pause" || input == "next" || input == "previous"

    private fun isWifiSettingsQuery(input: String): Boolean =
        input.contains("wifi") && (input.contains("setting") || input.contains("open"))

    private fun isBluetoothSettingsQuery(input: String): Boolean =
        input.contains("bluetooth") && (input.contains("setting") || input.contains("open"))

    private fun isLockScreenQuery(input: String): Boolean =
        input.contains("lock screen") || input.contains("lock the screen") || input == "lock phone"

    private fun isDeviceInfoQuery(input: String): Boolean =
        input.contains("device info") || input.contains("what device is this") || input.contains("phone info")

    private fun isNetworkQuery(input: String): Boolean =
        input.contains("network status") || input.contains("internet status") || input.contains("am i online") || input.contains("am i connected")

    private fun isSmsQuery(input: String): Boolean =
        input.startsWith("send a message") || input.startsWith("send message") || input.startsWith("send sms") || input.startsWith("text ")

    private fun extractSmsArgs(prompt: String): Map<String, Any?> {
        val lower = prompt.lowercase()
        var contact = ""
        var message = ""
        if (lower.contains(" to ") && (lower.contains(" saying ") || lower.contains(" that "))) {
            val toIdx = lower.indexOf(" to ") + 4
            val sayIdx = if (lower.contains(" saying ")) lower.indexOf(" saying ") else lower.indexOf(" that ")
            if (sayIdx > toIdx) {
                contact = prompt.substring(toIdx, sayIdx).trim()
                message = prompt.substring(sayIdx + (if (lower.contains(" saying ")) 8 else 6)).trim()
            }
        }
        if (contact.isBlank()) {
            val words = prompt.split(" ")
            contact = words.getOrNull(2) ?: "Contact"
            message = prompt
        }
        return mapOf("contactName" to contact, "message" to message)
    }

    private fun isReadScreenQuery(input: String): Boolean =
        input.contains("what is on my screen") || input.contains("read this page") || input.contains("read screen") || input.contains("describe screen") || input == "whats on my screen"

    private fun isDiagnoseErrorQuery(input: String): Boolean =
        input.contains("what does this error mean") || input.contains("is there an error") || input.contains("why did this fail") || input.contains("diagnose error")

    private fun isSearchYouTubeQuery(input: String): Boolean =
        input.contains("youtube") && (input.contains("search") || input.contains("find") || input.contains("play"))

    private fun extractYouTubeQuery(input: String): String {
        return input.replace("search youtube for", "")
            .replace("search on youtube for", "")
            .replace("search for", "")
            .replace("youtube", "")
            .trim()
    }

    private fun isSearchWebQuery(input: String): Boolean =
        input.startsWith("search google for") || input.startsWith("search web for") || input.startsWith("google ") || (input.startsWith("search for ") && !input.contains("note"))

    private fun extractWebQuery(input: String): String {
        return input.replace("search google for", "")
            .replace("search web for", "")
            .replace("google ", "")
            .replace("search for ", "")
            .trim()
    }

    private fun isTimerQuery(input: String): Boolean =
        input.contains("timer for") || input.startsWith("set a timer") || input.startsWith("timer ")

    private fun extractTimerArgs(input: String): Map<String, Any?> {
        val numberRegex = Regex("(\\d+)")
        val match = numberRegex.find(input)
        val value = match?.groupValues?.get(1)?.toLongOrNull() ?: 60L
        return if (input.contains("second")) {
            mapOf("durationSeconds" to value)
        } else if (input.contains("hour")) {
            mapOf("durationSeconds" to (value * 3600))
        } else {
            mapOf("durationSeconds" to (value * 60))
        }
    }

    private fun isCancelTimerQuery(input: String): Boolean =
        input.contains("cancel") && input.contains("timer")

    private fun isListTimersQuery(input: String): Boolean =
        (input.contains("show") || input.contains("list") || input.contains("what")) && input.contains("timer")

    private fun isCreateReminderQuery(input: String): Boolean =
        input.startsWith("remind me") || input.startsWith("set a reminder") || input.startsWith("create a reminder")

    private fun extractReminderArgs(prompt: String): Map<String, Any?> {
        val lower = prompt.lowercase()
        var msg = prompt.replace(Regex("(?i)^(remind me to|remind me|set a reminder to|create a reminder to)"), "").trim()
        var mins = 10L
        if (lower.contains(" in ") && lower.contains(" min")) {
            val num = Regex("(\\d+)").find(lower.substring(lower.indexOf(" in ")))?.groupValues?.get(1)?.toLongOrNull()
            if (num != null) mins = num
        }
        return mapOf("message" to msg, "relativeMinutes" to mins)
    }

    private fun isListRemindersQuery(input: String): Boolean =
        (input.contains("show") || input.contains("list") || input.contains("what")) && input.contains("reminder")

    private fun isCancelReminderQuery(input: String): Boolean =
        input.contains("cancel") && input.contains("reminder")

    private fun isCreateNoteQuery(input: String): Boolean =
        input.startsWith("create a note") || input.startsWith("take a note") || input.startsWith("new note") || input.startsWith("remember this in my notes")

    private fun extractNoteArgs(prompt: String): Map<String, Any?> {
        var title = "Quick Note"
        var content = prompt
        val lower = prompt.lowercase()
        if (lower.contains(" called ")) {
            val idx = lower.indexOf(" called ") + 8
            val withIdx = if (lower.contains(" with ")) lower.indexOf(" with ") else prompt.length
            title = prompt.substring(idx, withIdx).trim()
            if (withIdx < prompt.length) {
                content = prompt.substring(withIdx + 6).trim()
            }
        }
        return mapOf("title" to title, "content" to content)
    }

    private fun isSearchNoteQuery(input: String): Boolean =
        input.contains("note") && (input.contains("search") || input.contains("find") || input.contains("what did i write") || input.contains("show note"))

    private fun extractNoteSearchQuery(input: String): String {
        return input.replace("search my notes for", "")
            .replace("search notes for", "")
            .replace("find my note about", "")
            .replace("find my notes for", "")
            .replace("notes for", "")
            .replace("notes", "")
            .trim()
    }

    private fun isListNotesQuery(input: String): Boolean =
        input.contains("show my notes") || input.contains("list my notes") || input == "my notes" || input == "show notes"

    private fun isListCalendarQuery(input: String): Boolean =
        input.contains("calendar") || input.contains("what do i have tomorrow") || input.contains("what is on my schedule") || input.contains("show my events")

    private fun isMorningBriefingQuery(input: String): Boolean =
        input == "good morning" || input == "good morning jarvis" || input == "morning briefing" || input == "morning summary" || input == "morning jarvis"
}
