package com.jarvis.assistant.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Representation of a structured tool call returned by the backend.
 */
data class RemoteToolCall(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("arguments")
    val arguments: Map<String, Any?> = emptyMap()
)

/**
 * Payload received from the backend /api/chat endpoint.
 *
 * @property reply The AI-generated conversational response (if any).
 * @property sessionId The session identifier associated with this conversation turn.
 * @property toolCall Optional tool execution request dispatched by the AI model.
 */
data class ChatResponse(
    @SerializedName("reply")
    val reply: String? = null,

    @SerializedName("sessionId")
    val sessionId: String = "",

    @SerializedName("toolCall")
    val toolCall: RemoteToolCall? = null,

    @SerializedName("timing")
    val timing: Map<String, Any?>? = null
)
