package com.example.services

import android.util.Log
import com.example.data.PreferencesManager
import com.example.models.ChatMessage
import com.example.models.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramService(
    private val prefs: PreferencesManager,
    private val aiService: AiService,
    private val actionHandler: ActionHandler,
    private val taskExecutor: TaskExecutor
) {
    companion object {
        private const val TAG = "TelegramService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling.asStateFlow()

    private val _lastLog = MutableStateFlow("Telegram bot idle")
    val lastLog: StateFlow<String> = _lastLog.asStateFlow()

    fun start() {
        val token = prefs.telegramBotToken.value.trim()
        if (token.isBlank()) {
            _lastLog.value = "Bot token is empty. Enter token in Settings."
            return
        }

        stop()
        pollingJob = scope.launch {
            _isPolling.value = true
            _lastLog.value = "Starting Telegram polling..."
            var lastUpdateId = 0L

            while (isActive && prefs.telegramEnabled.value) {
                try {
                    val url = "https://api.telegram.org/bot$token/getUpdates?offset=$lastUpdateId&timeout=20"
                    val request = Request.Builder().url(url).get().build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        _lastLog.value = "Telegram HTTP error: ${response.code}"
                        delay(5000)
                        continue
                    }

                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optBoolean("ok", false)) {
                        val result = json.optJSONArray("result")
                        if (result != null) {
                            for (i in 0 until result.length()) {
                                val item = result.getJSONObject(i)
                                val updateId = item.getLong("update_id")
                                lastUpdateId = updateId + 1

                                val message = item.optJSONObject("message") ?: continue
                                val chat = message.optJSONObject("chat") ?: continue
                                val chatId = chat.getLong("id").toString()
                                val chatTitle = chat.optString("first_name", chat.optString("title", "User"))
                                val text = message.optString("text", "").trim()

                                if (text.isNotBlank()) {
                                    handleIncomingMessage(token, chatId, chatTitle, text)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        _lastLog.value = "Polling error: ${e.message}"
                        delay(4000)
                    }
                }
            }
            _isPolling.value = false
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        _isPolling.value = false
        _lastLog.value = "Telegram bot listener stopped"
    }

    private suspend fun handleIncomingMessage(
        token: String,
        chatId: String,
        chatName: String,
        text: String
    ) {
        val pairedId = prefs.telegramPairedChatId.value

        // Security check: Pairing lock-down
        if (pairedId.isBlank()) {
            // First contact locks the bot
            prefs.setTelegramPairedChatId(chatId)
            sendTelegramMessage(
                token,
                chatId,
                "EVA AI PAIRED SUCCESSFULLY!\nDevice locked to chat: $chatName (ID: $chatId).\nYou can now chat or send device commands."
            )
            _lastLog.value = "Paired with chat: $chatId ($chatName)"
            return
        } else if (pairedId != chatId) {
            // Reject unauthorized sender
            Log.w(TAG, "Rejected unauthorized message from chat $chatId")
            sendTelegramMessage(
                token,
                chatId,
                "Unauthorized. EVA AI is securely paired to a different device chat."
            )
            return
        }

        _lastLog.value = "Received: \"$text\" from paired chat"
        sendTelegramMessage(token, chatId, "🤖 Processing: \"$text\"...")

        val isAgent = prefs.isAgentMode.value
        val aiResult = aiService.sendMessage(text, isAgentMode = isAgent)

        if (aiResult.isFailure) {
            sendTelegramMessage(token, chatId, "⚠️ Error: ${aiResult.exceptionOrNull()?.message}")
            return
        }

        val response = aiResult.getOrNull() ?: ""

        if (!isAgent) {
            sendTelegramMessage(token, chatId, response)
            return
        }

        val action = aiService.parseAction(response)
        if (action == null) {
            sendTelegramMessage(token, chatId, response)
            return
        }

        if (action.action.equals("execute_task", ignoreCase = true)) {
            val goal = action.getStringParam("goal", text)
            sendTelegramMessage(token, chatId, "🚀 Starting Screen Automation: \"$goal\"")

            val record = taskExecutor.execute(
                goal = goal,
                conversationId = "telegram_$chatId",
                onStepProgress = { step ->
                    if (step.role == MessageRole.STEP) {
                        sendTelegramMessage(token, chatId, "⏳ ${step.content}")
                    }
                }
            )

            sendTelegramMessage(
                token,
                chatId,
                "🏁 Task Finished [${record.status}]:\n${record.stepsSummary}"
            )
        } else {
            val result = actionHandler.execute(action)
            val reply = "${action.response}\n[Result: ${if (result.success) "SUCCESS" else "FAILED"}] ${result.message}"
            sendTelegramMessage(token, chatId, reply)
        }
    }

    private suspend fun sendTelegramMessage(token: String, chatId: String, text: String) = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val body = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
            }
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send telegram message", e)
        }
    }
}
