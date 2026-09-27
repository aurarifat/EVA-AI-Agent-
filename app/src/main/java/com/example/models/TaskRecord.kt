package com.example.models

data class SavedSkill(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val goal: String,
    val stepsJson: String, // serialized list of actions
    val createdAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 1
)

enum class TaskStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class TaskRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val goal: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val status: TaskStatus = TaskStatus.RUNNING,
    val stepCount: Int = 0,
    val stepsSummary: String = "",
    val traceLog: String = ""
)
