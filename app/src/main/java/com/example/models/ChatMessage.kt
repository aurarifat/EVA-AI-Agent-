package com.example.models

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    STEP
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val conversationId: String = "default",
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isTaskStep: Boolean = false,
    val stepIcon: String? = null,
    val reasoning: String? = null,
    val actionJson: String? = null,
    val stepNumber: Int = 0,
    val isSuccess: Boolean = true
)
