package com.example.viewmodels

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ConversationEntity
import com.example.data.EvaRepository
import com.example.data.PreferencesManager
import com.example.models.AgentAction
import com.example.models.ChatMessage
import com.example.models.MessageRole
import com.example.models.SavedSkill
import com.example.models.TaskRecord
import com.example.services.ActionHandler
import com.example.services.AiService
import com.example.services.AppLauncherService
import com.example.services.EvaAccessibilityService
import com.example.services.EvaOverlayService
import com.example.services.ScreenAutomationService
import com.example.services.SkillMemoryService
import com.example.services.TaskExecutor
import com.example.services.TelegramService
import com.example.services.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class ScreenDestination {
    CHAT,
    TASK_HISTORY,
    SAVED_SKILLS,
    SETTINGS,
    ONBOARDING,
    SCREEN_INSPECTOR // Dedicated tablet/Infinix Xpad 20 tool
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = PreferencesManager(application)
    private val database = AppDatabase.getInstance(application)
    val repository = EvaRepository(database)

    val aiService = AiService(preferences)
    val screenAutomation = ScreenAutomationService(application)
    val appLauncher = AppLauncherService(application)
    val actionHandler = ActionHandler(application, screenAutomation, appLauncher)
    val taskExecutor = TaskExecutor(aiService, screenAutomation, appLauncher, repository, preferences)
    val skillMemory = SkillMemoryService(repository)
    val telegramService = TelegramService(preferences, aiService, actionHandler, taskExecutor)
    val voiceService = VoiceService(application)

    private val _currentScreen = MutableStateFlow(
        if (preferences.hasCompletedOnboarding.value) ScreenDestination.CHAT else ScreenDestination.ONBOARDING
    )
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _currentConvId = MutableStateFlow("conv_${System.currentTimeMillis()}")
    val currentConvId: StateFlow<String> = _currentConvId.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val messages: StateFlow<List<ChatMessage>> = _currentConvId.flatMapLatest { id ->
        repository.getMessagesForConversation(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<TaskRecord>> = repository.allTasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val allSkills: StateFlow<List<SavedSkill>> = repository.allSkills.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val isGenerating = MutableStateFlow(false)
    val isTaskRunning: StateFlow<Boolean> = taskExecutor.isTaskRunning
    val currentGoal: StateFlow<String?> = taskExecutor.currentGoal
    val currentStepNumber: StateFlow<Int> = taskExecutor.currentStep
    val accessibilityActive: StateFlow<Boolean> = EvaAccessibilityService.isServiceRunning

    val isListening: StateFlow<Boolean> = voiceService.isListening
    val speechTranscript: StateFlow<String> = voiceService.speechText

    init {
        // Wire voice completion
        voiceService.onSpeechComplete = { text ->
            if (text.isNotBlank()) {
                sendMessage(text)
            }
        }

        // Initialize Telegram bot if enabled
        if (preferences.telegramEnabled.value && preferences.telegramBotToken.value.isNotBlank()) {
            telegramService.start()
        }

        // Ensure default conversation exists
        viewModelScope.launch {
            repository.saveConversation(_currentConvId.value, "New EVA Session")
        }
    }

    fun navigateTo(destination: ScreenDestination) {
        _currentScreen.value = destination
    }

    fun createNewChat() {
        val newId = "conv_${System.currentTimeMillis()}"
        _currentConvId.value = newId
        viewModelScope.launch {
            repository.saveConversation(newId, "New Conversation")
        }
        _currentScreen.value = ScreenDestination.CHAT
    }

    fun selectConversation(id: String) {
        _currentConvId.value = id
        _currentScreen.value = ScreenDestination.CHAT
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConvId.value == id) {
                createNewChat()
            }
        }
    }

    fun cancelActiveTask() {
        taskExecutor.cancel()
    }

    fun sendMessage(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || isGenerating.value || isTaskRunning.value) return

        val convId = _currentConvId.value

        viewModelScope.launch {
            // Update conversation title if first message
            val history = repository.getRecentMessagesList(convId)
            if (history.isEmpty()) {
                val title = if (trimmed.length > 25) trimmed.take(25) + "..." else trimmed
                repository.saveConversation(convId, title)
            }

            // 1. Save user message
            val userMsg = ChatMessage(
                conversationId = convId,
                role = MessageRole.USER,
                content = trimmed
            )
            repository.saveMessage(userMsg)

            isGenerating.value = true

            val isAgent = preferences.isAgentMode.value

            if (!isAgent) {
                // Pure Chat Mode: stream or send natural response
                handleChatMode(trimmed, convId, history)
            } else {
                // Agent Mode: check skills or invoke AI decision
                handleAgentMode(trimmed, convId, history)
            }

            isGenerating.value = false
        }
    }

    private suspend fun handleChatMode(userText: String, convId: String, history: List<ChatMessage>) {
        val result = aiService.sendMessage(userText, history = history, isAgentMode = false)
        if (result.isSuccess) {
            val responseText = result.getOrNull() ?: ""
            val assistantMsg = ChatMessage(
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = responseText
            )
            repository.saveMessage(assistantMsg)
            if (preferences.ttsEnabled.value) {
                voiceService.speak(responseText)
            }
        } else {
            val errorMsg = ChatMessage(
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = "Error: ${result.exceptionOrNull()?.message}\n(Check your API Key and Base URL in Settings)",
                isSuccess = false
            )
            repository.saveMessage(errorMsg)
        }
    }

    private suspend fun handleAgentMode(userText: String, convId: String, history: List<ChatMessage>) {
        // Check if there is a matching Saved Skill
        val savedSkill = skillMemory.findMatchingSkill(userText)
        if (savedSkill != null) {
            val skillNotice = ChatMessage(
                conversationId = convId,
                role = MessageRole.STEP,
                content = "Replaying saved skill: \"${savedSkill.name}\"",
                stepIcon = "⚡",
                isTaskStep = true
            )
            repository.saveMessage(skillNotice)
        }

        // Call AI for agent decision
        val result = aiService.sendMessage(userText, history = history, isAgentMode = true)
        if (result.isFailure) {
            val errorMsg = ChatMessage(
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = "AI Error: ${result.exceptionOrNull()?.message}\n(Please verify your API key in Settings)",
                isSuccess = false
            )
            repository.saveMessage(errorMsg)
            return
        }

        val aiText = result.getOrNull() ?: ""
        val parsedAction = aiService.parseAction(aiText)

        if (parsedAction == null) {
            // Conversational response in Agent mode
            val assistantMsg = ChatMessage(
                conversationId = convId,
                role = MessageRole.ASSISTANT,
                content = aiText
            )
            repository.saveMessage(assistantMsg)
            if (preferences.ttsEnabled.value) {
                voiceService.speak(aiText)
            }
        } else {
            // Action to execute
            if (parsedAction.action.equals("execute_task", ignoreCase = true)) {
                val goal = parsedAction.getStringParam("goal", userText)

                val startingMsg = ChatMessage(
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = parsedAction.response.ifBlank { "Starting screen task: \"$goal\"" },
                    stepIcon = "🤖"
                )
                repository.saveMessage(startingMsg)

                // Execute autonomous multi-step loop
                taskExecutor.execute(
                    goal = goal,
                    conversationId = convId,
                    onStepProgress = { /* TaskExecutor saves messages directly to repo */ }
                )
            } else {
                // One-shot simple action
                val actionNotice = ChatMessage(
                    conversationId = convId,
                    role = MessageRole.ASSISTANT,
                    content = parsedAction.response.ifBlank { "Executing ${parsedAction.action}..." },
                    reasoning = parsedAction.reasoning
                )
                repository.saveMessage(actionNotice)

                val execResult = actionHandler.execute(parsedAction)

                val resultMsg = ChatMessage(
                    conversationId = convId,
                    role = MessageRole.STEP,
                    content = if (execResult.success) "Action Success: ${execResult.message}" else "Action Failed: ${execResult.message}",
                    stepIcon = if (execResult.success) "✓" else "✕",
                    isTaskStep = true,
                    isSuccess = execResult.success
                )
                repository.saveMessage(resultMsg)

                if (preferences.ttsEnabled.value && execResult.message.isNotBlank()) {
                    voiceService.speak(execResult.message)
                }
            }
        }
    }

    fun saveSkill(name: String, goal: String, actionsJson: String) {
        viewModelScope.launch {
            val skill = SavedSkill(
                name = name,
                goal = goal,
                stepsJson = actionsJson
            )
            repository.saveSkill(skill)
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            repository.deleteSkill(id)
        }
    }

    fun clearTaskHistory() {
        viewModelScope.launch {
            repository.clearAllTasks()
        }
    }

    fun toggleOverlayOrb(enable: Boolean) {
        preferences.setOverlayOrbEnabled(enable)
        val context = getApplication<Application>()
        if (enable) {
            if (Settings.canDrawOverlays(context)) {
                EvaOverlayService.start(context)
            }
        } else {
            EvaOverlayService.stop(context)
        }
    }

    fun toggleTelegram(enable: Boolean) {
        preferences.setTelegramEnabled(enable)
        if (enable) {
            telegramService.start()
        } else {
            telegramService.stop()
        }
    }

    fun startVoiceInput() {
        voiceService.startListening()
    }

    fun stopVoiceInput() {
        voiceService.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.destroy()
        telegramService.stop()
    }
}
