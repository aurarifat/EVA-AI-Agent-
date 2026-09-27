package com.example.data

import com.example.models.ChatMessage
import com.example.models.SavedSkill
import com.example.models.TaskRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EvaRepository(private val database: AppDatabase) {
    private val chatDao = database.chatDao()
    private val taskDao = database.taskDao()
    private val skillDao = database.skillDao()

    val allConversations: Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessagesForConversation(convId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForConversation(convId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getRecentMessagesList(convId: String, limit: Int = 20): List<ChatMessage> {
        val all = chatDao.getMessagesListForConversation(convId).map { it.toDomain() }
        return if (all.size > limit) all.takeLast(limit) else all
    }

    suspend fun saveConversation(id: String, title: String) {
        chatDao.insertConversation(
            ConversationEntity(id = id, title = title, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun deleteConversation(id: String) {
        chatDao.deleteMessagesForConversation(id)
        chatDao.deleteConversation(id)
    }

    suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(ChatMessageEntity.fromDomain(message))
    }

    val allTasks: Flow<List<TaskRecord>> = taskDao.getAllTasks().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getTaskById(id: String): TaskRecord? {
        return taskDao.getTaskById(id)?.toDomain()
    }

    suspend fun saveTask(task: TaskRecord) {
        taskDao.insertTask(TaskRecordEntity.fromDomain(task))
    }

    suspend fun updateTask(task: TaskRecord) {
        taskDao.updateTask(TaskRecordEntity.fromDomain(task))
    }

    suspend fun deleteTask(id: String) {
        taskDao.deleteTask(id)
    }

    suspend fun clearAllTasks() {
        taskDao.clearAllTasks()
    }

    val allSkills: Flow<List<SavedSkill>> = skillDao.getAllSkills().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun findMatchingSkill(query: String): SavedSkill? {
        return skillDao.findMatchingSkill(query)?.toDomain()
    }

    suspend fun saveSkill(skill: SavedSkill) {
        skillDao.insertSkill(SavedSkillEntity.fromDomain(skill))
    }

    suspend fun deleteSkill(id: String) {
        skillDao.deleteSkill(id)
    }
}
