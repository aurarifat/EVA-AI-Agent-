package com.example.services

import android.util.Log
import com.example.data.PreferencesManager
import com.example.models.AgentAction
import com.example.models.ChatMessage
import com.example.models.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AiService(private val prefs: PreferencesManager) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AiService"

        val AGENT_SYSTEM_PROMPT = """
            You are EVA AI, a personal on-device AI assistant that chats normally AND controls an Android phone or Infinix Xpad 20 tablet on the user's behalf.
            Always identify yourself as EVA AI if asked.
            You have two distinct ways to reply:
            1. CONVERSATION: If the user is chatting, asking questions, seeking advice, or when no physical device action is requested, reply naturally in clean, friendly text (markdown supported).
            2. DEVICE ACTION: If the user commands an action or task on their device, you MUST reply with ONLY a single raw JSON object (NO markdown backticks, NO prefix/suffix text).

            For a SINGLE SIMPLE ACTION:
            {
              "action": "<action_name>",
              "params": { ... },
              "response": "<friendly confirmation to speak/show user>",
              "reasoning": "<why this action was chosen>"
            }

            Available simple actions:
            - open_app: {"app_name": "Camera"}
            - launch_package: {"package_name": "com.android.settings"}
            - make_call: {"contact_name": "Alex"} OR {"phone_number": "5551234"}
            - send_sms: {"contact_name": "Alex", "message": "On my way"}
            - search_contact: {"query": "Doctor"}
            - set_alarm: {"hour": 8, "minute": 0, "label": "Morning Workout"}
            - set_volume: {"level": 60} (0 to 100)
            - set_brightness: {"level": 75} (0 to 100)
            - read_screen: {}
            - press_back: {}

            For MULTI-STEP COMPLEX TASKS (e.g., navigating across apps, booking, searching within third-party apps, multi-screen workflows):
            {
              "action": "execute_task",
              "params": {"goal": "<precise task description>"},
              "response": "Starting automation to accomplish this task on your screen.",
              "reasoning": "This requires multi-step interactive screen automation."
            }
        """.trimIndent()

        val CHAT_SYSTEM_PROMPT = """
            You are EVA AI, a helpful, intelligent on-device AI assistant.
            In this mode, you are in conversational Chat Mode and cannot perform device actions.
            Reply in clear, engaging, helpful text with rich markdown.
            Always identify yourself as EVA AI.
        """.trimIndent()

        val TASK_SYSTEM_PROMPT = """
            You are EVA AI Screen Automation Agent controlling an Android device / Infinix Xpad 20 tablet.
            You are given the user's ultimate GOAL and the current live SCREEN elements dump.
            Inspect the screen and decide what SINGLE next action to take to advance toward the goal.

            Available step actions:
            - click_text: {"text": "Search"} (Finds and clicks button/item matching text or description)
            - click_at: {"x": 540, "y": 960} (Dispatches tap gesture at coordinates)
            - type_text: {"text": "Hotels in Tokyo", "field_hint": "Search"} (Types into editable field)
            - press_enter: {} (Triggers keyboard search/enter action)
            - scroll: {"direction": "up" | "down"} (Scrolls the screen container)
            - swipe: {"startX": 800, "startY": 500, "endX": 200, "endY": 500}
            - press_back: {} (Android back button)
            - press_home: {} (Android home button)
            - open_app: {"app_name": "Chrome"}
            - wait: {} (Wait for UI to settle)
            - done: {} (Goal is completed)

            Return ONLY a raw JSON object:
            {
              "action": "<step_action>",
              "params": { ... },
              "reasoning": "<concise explanation of why this step is taken>",
              "is_complete": false
            }
            Set "is_complete": true or action to "done" when the user's goal has been accomplished.
        """.trimIndent()
    }

    /**
     * Sends a chat or agent message with rolling history.
     */
    suspend fun sendMessage(
        userMessage: String,
        history: List<ChatMessage> = emptyList(),
        isAgentMode: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = sanitizeBaseUrl(prefs.baseUrl.value)
            val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

            val jsonBody = JSONObject().apply {
                put("model", prefs.modelName.value)
                put("temperature", prefs.temperature.value.toDouble())
                put("max_tokens", prefs.maxTokens.value)

                val messagesArray = JSONArray()

                // System prompt if enabled
                if (prefs.sendSystemPrompt.value) {
                    val sysPrompt = if (isAgentMode) AGENT_SYSTEM_PROMPT else CHAT_SYSTEM_PROMPT
                    messagesArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", sysPrompt)
                    })
                }

                // Add recent history
                val recent = if (history.size > 20) history.takeLast(20) else history
                for (msg in recent) {
                    if (msg.role == MessageRole.USER) {
                        messagesArray.put(JSONObject().apply {
                            put("role", "user")
                            put("content", msg.content)
                        })
                    } else if (msg.role == MessageRole.ASSISTANT && !msg.isTaskStep) {
                        messagesArray.put(JSONObject().apply {
                            put("role", "assistant")
                            put("content", msg.content)
                        })
                    }
                }

                // Current message
                messagesArray.put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })

                put("messages", messagesArray)
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

            val apiKey = prefs.apiKey.value
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    return@withContext Result.failure(Exception("API Error (${response.code}): $err"))
                }

                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                val choices = json.getJSONArray("choices")
                if (choices.length() == 0) {
                    return@withContext Result.failure(Exception("Empty choices in AI response"))
                }
                val rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                val cleanContent = stripThinking(rawContent)
                Result.success(cleanContent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            Result.failure(e)
        }
    }

    /**
     * Streaming chat with Server-Sent Events (SSE).
     */
    fun sendMessageStream(
        userMessage: String,
        history: List<ChatMessage> = emptyList(),
        isAgentMode: Boolean = true
    ): Flow<String> = flow {
        val baseUrl = sanitizeBaseUrl(prefs.baseUrl.value)
        val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val jsonBody = JSONObject().apply {
            put("model", prefs.modelName.value)
            put("temperature", prefs.temperature.value.toDouble())
            put("max_tokens", prefs.maxTokens.value)
            put("stream", true)

            val messagesArray = JSONArray()
            if (prefs.sendSystemPrompt.value) {
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", if (isAgentMode) AGENT_SYSTEM_PROMPT else CHAT_SYSTEM_PROMPT)
                })
            }
            val recent = if (history.size > 20) history.takeLast(20) else history
            for (msg in recent) {
                if (msg.role == MessageRole.USER) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", msg.content)
                    })
                } else if (msg.role == MessageRole.ASSISTANT && !msg.isTaskStep) {
                    messagesArray.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", msg.content)
                    })
                }
            }
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
            put("messages", messagesArray)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

        val apiKey = prefs.apiKey.value
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "HTTP ${response.code}"
            throw Exception("API Error (${response.code}): $err")
        }

        val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
        var line: String?
        val rawBuffer = StringBuilder()

        try {
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("data: ")) {
                    val data = l.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        val chunkJson = JSONObject(data)
                        val choices = chunkJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val contentChunk = delta?.optString("content", "") ?: ""
                            if (contentChunk.isNotEmpty()) {
                                rawBuffer.append(contentChunk)
                                // Only emit if outside of <think>
                                val currentText = stripThinking(rawBuffer.toString())
                                emit(currentText)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Send task message with retry logic for TaskExecutor.
     */
    suspend fun sendTaskMessage(
        goal: String,
        screenDump: String,
        stepCount: Int,
        previousSteps: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val maxRetries = 3
        var lastException: Exception? = null

        val promptBuilder = StringBuilder()
        promptBuilder.append("USER GOAL: ").append(goal).append("\n\n")
        if (previousSteps.isNotEmpty()) {
            promptBuilder.append("ACTIONS TAKEN SO FAR:\n")
            previousSteps.forEachIndexed { i, s ->
                promptBuilder.append("Step ${i + 1}: ").append(s).append("\n")
            }
            promptBuilder.append("\n")
        }
        promptBuilder.append("CURRENT SCREEN:\n").append(screenDump).append("\n\n")
        promptBuilder.append("Current step index: ").append(stepCount + 1).append(".\n")
        promptBuilder.append("Decide the single next action. Reply ONLY with the requested JSON object.")

        for (attempt in 1..maxRetries) {
            try {
                val baseUrl = sanitizeBaseUrl(prefs.baseUrl.value)
                val endpoint = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

                val jsonBody = JSONObject().apply {
                    put("model", prefs.modelName.value)
                    put("temperature", 0.2) // Low temperature for deterministic automation
                    put("max_tokens", 800)

                    val messagesArray = JSONArray()
                    messagesArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", TASK_SYSTEM_PROMPT)
                    })
                    messagesArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", promptBuilder.toString())
                    })
                    put("messages", messagesArray)
                }

                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))

                val apiKey = prefs.apiKey.value
                if (apiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${response.body?.string()}")
                    }
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    val choices = json.getJSONArray("choices")
                    val rawContent = choices.getJSONObject(0).getJSONObject("message").getString("content")
                    val clean = stripThinking(rawContent)
                    return@withContext Result.success(clean)
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "sendTaskMessage attempt $attempt failed: ${e.message}")
                if (attempt < maxRetries) {
                    delay(500L * attempt)
                }
            }
        }
        Result.failure(lastException ?: Exception("Failed after $maxRetries retries"))
    }

    /**
     * Parses AgentAction from JSON or fuzzy json within text.
     */
    fun parseAction(response: String): AgentAction? {
        val trimmed = response.trim()

        // Extract JSON substring if wrapped in fences or prose
        val jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL)
        val matcher = jsonPattern.matcher(trimmed)
        val candidate = if (matcher.find()) matcher.group() else trimmed

        try {
            val obj = JSONObject(candidate)
            val action = obj.optString("action", "")
            if (action.isBlank()) return null

            val paramsMap = mutableMapOf<String, Any?>()
            val paramsObj = obj.optJSONObject("params")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    paramsMap[k] = paramsObj.get(k)
                }
            }

            val resp = obj.optString("response", "")
            val reasoning = obj.optString("reasoning", null)
            val isComplete = obj.optBoolean("is_complete", false) || action == "done"

            return AgentAction(
                action = action,
                params = paramsMap,
                response = resp,
                reasoning = reasoning,
                isComplete = isComplete
            )
        } catch (_: Exception) {
            // Tolerant fallback: check for loose action pattern
            return tryFuzzyActionParse(trimmed)
        }
    }

    private fun tryFuzzyActionParse(text: String): AgentAction? {
        val clean = text.replace("```json", "").replace("```", "").trim()
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val obj = JSONObject(clean)
                val action = obj.optString("action", "")
                if (action.isNotEmpty()) {
                    return AgentAction(
                        action = action,
                        response = obj.optString("response", ""),
                        reasoning = obj.optString("reasoning", null),
                        isComplete = obj.optBoolean("is_complete", false)
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Fetch available model IDs from /models endpoint.
     */
    suspend fun fetchAvailableModels(baseUrl: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val sanitized = sanitizeBaseUrl(baseUrl)
            val endpoint = if (sanitized.endsWith("/")) "${sanitized}models" else "$sanitized/models"

            val requestBuilder = Request.Builder().url(endpoint).get()
            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val data = json.optJSONArray("data") ?: json.optJSONArray("models") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val item = data.get(i)
                    when (item) {
                        is JSONObject -> {
                            val id = item.optString("id", "")
                            if (id.isNotEmpty()) list.add(id)
                        }
                        is String -> list.add(item)
                    }
                }
                Result.success(list.sorted())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sanitizeBaseUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u
    }

    private fun stripThinking(content: String): String {
        // Strip <think>...</think> blocks from reasoning models like DeepSeek-R1
        return content.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    }
}
