package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.models.ChatMessage
import com.example.models.MessageRole
import com.example.models.SavedSkill
import com.example.models.TaskRecord
import com.example.models.TaskStatus

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isTaskStep: Boolean = false,
    val stepIcon: String? = null,
    val reasoning: String? = null,
    val actionJson: String? = null,
    val stepNumber: Int = 0,
    val isSuccess: Boolean = true
) {
    fun toDomain(): ChatMessage {
        val parsedRole = try {
            MessageRole.valueOf(role)
        } catch (_: Exception) {
            MessageRole.ASSISTANT
        }
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            role = parsedRole,
            content = content,
            timestamp = timestamp,
            isTaskStep = isTaskStep,
            stepIcon = stepIcon,
            reasoning = reasoning,
            actionJson = actionJson,
            stepNumber = stepNumber,
            isSuccess = isSuccess
        )
    }

    companion object {
        fun fromDomain(m: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = m.id,
                conversationId = m.conversationId,
                role = m.role.name,
                content = m.content,
                timestamp = m.timestamp,
                isTaskStep = m.isTaskStep,
                stepIcon = m.stepIcon,
                reasoning = m.reasoning,
                actionJson = m.actionJson,
                stepNumber = m.stepNumber,
                isSuccess = m.isSuccess
            )
        }
    }
}

@Entity(tableName = "task_records")
data class TaskRecordEntity(
    @PrimaryKey val id: String,
    val goal: String,
    val startTime: Long,
    val endTime: Long,
    val status: String,
    val stepCount: Int,
    val stepsSummary: String,
    val traceLog: String
) {
    fun toDomain(): TaskRecord {
        val parsedStatus = try {
            TaskStatus.valueOf(status)
        } catch (_: Exception) {
            TaskStatus.FAILED
        }
        return TaskRecord(
            id = id,
            goal = goal,
            startTime = startTime,
            endTime = endTime,
            status = parsedStatus,
            stepCount = stepCount,
            stepsSummary = stepsSummary,
            traceLog = traceLog
        )
    }

    companion object {
        fun fromDomain(t: TaskRecord): TaskRecordEntity {
            return TaskRecordEntity(
                id = t.id,
                goal = t.goal,
                startTime = t.startTime,
                endTime = t.endTime,
                status = t.status.name,
                stepCount = t.stepCount,
                stepsSummary = t.stepsSummary,
                traceLog = t.traceLog
            )
        }
    }
}

@Entity(tableName = "saved_skills")
data class SavedSkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val goal: String,
    val stepsJson: String,
    val createdAt: Long,
    val usageCount: Int
) {
    fun toDomain(): SavedSkill = SavedSkill(
        id = id,
        name = name,
        goal = goal,
        stepsJson = stepsJson,
        createdAt = createdAt,
        usageCount = usageCount
    )

    companion object {
        fun fromDomain(s: SavedSkill): SavedSkillEntity = SavedSkillEntity(
            id = s.id,
            name = s.name,
            goal = s.goal,
            stepsJson = s.stepsJson,
            createdAt = s.createdAt,
            usageCount = s.usageCount
        )
    }
}
