package com.example.models

data class AgentAction(
    val action: String,
    val params: Map<String, Any?> = emptyMap(),
    val response: String = "",
    val reasoning: String? = null,
    val isComplete: Boolean = false
) {
    fun getStringParam(key: String, default: String = ""): String {
        return params[key]?.toString() ?: default
    }

    fun getIntParam(key: String, default: Int = 0): Int {
        val v = params[key] ?: return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    fun getFloatParam(key: String, default: Float = 0f): Float {
        val v = params[key] ?: return default
        return when (v) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: default
            else -> default
        }
    }
}

data class AgentActionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)
