package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesListForConversation(conversationId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_records ORDER BY startTime DESC")
    fun getAllTasks(): Flow<List<TaskRecordEntity>>

    @Query("SELECT * FROM task_records WHERE id = :id")
    suspend fun getTaskById(id: String): TaskRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskRecordEntity)

    @Update
    suspend fun updateTask(task: TaskRecordEntity)

    @Query("DELETE FROM task_records WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM task_records")
    suspend fun clearAllTasks()
}

@Dao
interface SkillDao {
    @Query("SELECT * FROM saved_skills ORDER BY usageCount DESC, createdAt DESC")
    fun getAllSkills(): Flow<List<SavedSkillEntity>>

    @Query("SELECT * FROM saved_skills WHERE goal LIKE '%' || :query || '%' LIMIT 1")
    suspend fun findMatchingSkill(query: String): SavedSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SavedSkillEntity)

    @Query("UPDATE saved_skills SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementSkillUsage(id: String)

    @Query("DELETE FROM saved_skills WHERE id = :id")
    suspend fun deleteSkill(id: String)
}
