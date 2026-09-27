package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("eva_ai_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai/"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_MAX_STEPS = 15
        const val DEFAULT_MAX_TOKENS = 1024
        const val DEFAULT_TEMP = 1.0f
    }

    private val _apiKey = MutableStateFlow(getInitialApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _baseUrl = MutableStateFlow(prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _modelName = MutableStateFlow(prefs.getString("model_name", DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _temperature = MutableStateFlow(prefs.getFloat("temperature", DEFAULT_TEMP))
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(prefs.getInt("max_tokens", DEFAULT_MAX_TOKENS))
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _maxSteps = MutableStateFlow(prefs.getInt("max_steps", DEFAULT_MAX_STEPS))
    val maxSteps: StateFlow<Int> = _maxSteps.asStateFlow()

    private val _disableMaxSteps = MutableStateFlow(prefs.getBoolean("disable_max_steps", false))
    val disableMaxSteps: StateFlow<Boolean> = _disableMaxSteps.asStateFlow()

    private val _useScreenCompression = MutableStateFlow(prefs.getBoolean("screen_compression", true))
    val useScreenCompression: StateFlow<Boolean> = _useScreenCompression.asStateFlow()

    private val _sendSystemPrompt = MutableStateFlow(prefs.getBoolean("send_system_prompt", true))
    val sendSystemPrompt: StateFlow<Boolean> = _sendSystemPrompt.asStateFlow()

    private val _isAgentMode = MutableStateFlow(prefs.getBoolean("is_agent_mode", true))
    val isAgentMode: StateFlow<Boolean> = _isAgentMode.asStateFlow()

    private val _telegramBotToken = MutableStateFlow(prefs.getString("telegram_token", "") ?: "")
    val telegramBotToken: StateFlow<String> = _telegramBotToken.asStateFlow()

    private val _telegramEnabled = MutableStateFlow(prefs.getBoolean("telegram_enabled", false))
    val telegramEnabled: StateFlow<Boolean> = _telegramEnabled.asStateFlow()

    private val _telegramPairedChatId = MutableStateFlow(prefs.getString("telegram_paired_chat_id", "") ?: "")
    val telegramPairedChatId: StateFlow<String> = _telegramPairedChatId.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(prefs.getBoolean("tts_enabled", false))
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _overlayOrbEnabled = MutableStateFlow(prefs.getBoolean("overlay_orb_enabled", false))
    val overlayOrbEnabled: StateFlow<Boolean> = _overlayOrbEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "dark") ?: "dark")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean("completed_onboarding", false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private fun getInitialApiKey(): String {
        val saved = prefs.getString("api_key", null)
        if (!saved.isNullOrBlank()) return saved
        // Fallback to BuildConfig if provided in environment
        return try {
            val keyField = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val v = keyField.get(null) as? String
            if (!v.isNullOrBlank() && v != "MY_GEMINI_API_KEY") v else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun setApiKey(value: String) {
        prefs.edit().putString("api_key", value).apply()
        _apiKey.value = value
    }

    fun setBaseUrl(value: String) {
        prefs.edit().putString("base_url", value).apply()
        _baseUrl.value = value
    }

    fun setModelName(value: String) {
        prefs.edit().putString("model_name", value).apply()
        _modelName.value = value
    }

    fun setTemperature(value: Float) {
        prefs.edit().putFloat("temperature", value).apply()
        _temperature.value = value
    }

    fun setMaxTokens(value: Int) {
        prefs.edit().putInt("max_tokens", value).apply()
        _maxTokens.value = value
    }

    fun setMaxSteps(value: Int) {
        prefs.edit().putInt("max_steps", value).apply()
        _maxSteps.value = value
    }

    fun setDisableMaxSteps(value: Boolean) {
        prefs.edit().putBoolean("disable_max_steps", value).apply()
        _disableMaxSteps.value = value
    }

    fun setUseScreenCompression(value: Boolean) {
        prefs.edit().putBoolean("screen_compression", value).apply()
        _useScreenCompression.value = value
    }

    fun setSendSystemPrompt(value: Boolean) {
        prefs.edit().putBoolean("send_system_prompt", value).apply()
        _sendSystemPrompt.value = value
    }

    fun setAgentMode(value: Boolean) {
        prefs.edit().putBoolean("is_agent_mode", value).apply()
        _isAgentMode.value = value
    }

    fun setTelegramBotToken(value: String) {
        prefs.edit().putString("telegram_token", value).apply()
        _telegramBotToken.value = value
    }

    fun setTelegramEnabled(value: Boolean) {
        prefs.edit().putBoolean("telegram_enabled", value).apply()
        _telegramEnabled.value = value
    }

    fun setTelegramPairedChatId(value: String) {
        prefs.edit().putString("telegram_paired_chat_id", value).apply()
        _telegramPairedChatId.value = value
    }

    fun unpairTelegram() {
        setTelegramPairedChatId("")
    }

    fun setTtsEnabled(value: Boolean) {
        prefs.edit().putBoolean("tts_enabled", value).apply()
        _ttsEnabled.value = value
    }

    fun setOverlayOrbEnabled(value: Boolean) {
        prefs.edit().putBoolean("overlay_orb_enabled", value).apply()
        _overlayOrbEnabled.value = value
    }

    fun setThemeMode(value: String) {
        prefs.edit().putString("theme_mode", value).apply()
        _themeMode.value = value
    }

    fun setCompletedOnboarding(value: Boolean) {
        prefs.edit().putBoolean("completed_onboarding", value).apply()
        _hasCompletedOnboarding.value = value
    }
}
