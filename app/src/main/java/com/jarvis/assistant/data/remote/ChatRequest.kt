package com.jarvis.assistant.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Payload sent to the backend /api/chat endpoint.
 * Supports both standard prompt queries and follow-up tool result reports.
 *
 * @property message The user's query/prompt.
 * @property sessionId Unique or default session identifier for multi-turn conversational context.
 * @property toolResult Optional result of an executed tool action.
 * @property toolCallId ID of the tool call being answered.
 * @property toolName Name of the executed tool.
 */
data class ChatRequest(
    @SerializedName("message")
    val message: String? = null,

    @SerializedName("sessionId")
    val sessionId: String = "default",

    @SerializedName("toolResult")
    val toolResult: Any? = null,

    @SerializedName("toolCallId")
    val toolCallId: String? = null,

    @SerializedName("toolName")
    val toolName: String? = null,

    @SerializedName("clientTimestamp")
    val clientTimestamp: Long? = null
)
