package com.example.services

import com.example.data.EvaRepository
import com.example.models.AgentAction
import com.example.models.SavedSkill
import org.json.JSONArray
import org.json.JSONObject

class SkillMemoryService(private val repository: EvaRepository) {

    suspend fun findMatchingSkill(goal: String): SavedSkill? {
        val clean = goal.trim().lowercase()
        return repository.findMatchingSkill(clean)
    }

    suspend fun saveCompletedTaskAsSkill(name: String, goal: String, executedActions: List<AgentAction>): SavedSkill {
        val array = JSONArray()
        for (a in executedActions) {
            val obj = JSONObject().apply {
                put("action", a.action)
                put("params", JSONObject(a.params))
                put("reasoning", a.reasoning ?: "")
            }
            array.put(obj)
        }

        val skill = SavedSkill(
            name = name,
            goal = goal,
            stepsJson = array.toString(),
            createdAt = System.currentTimeMillis(),
            usageCount = 1
        )
        repository.saveSkill(skill)
        return skill
    }

    fun parseSkillSteps(stepsJson: String): List<AgentAction> {
        return try {
            val array = JSONArray(stepsJson)
            val list = mutableListOf<AgentAction>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val paramsObj = obj.optJSONObject("params")
                val paramsMap = mutableMapOf<String, Any?>()
                if (paramsObj != null) {
                    val keys = paramsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        paramsMap[k] = paramsObj.get(k)
                    }
                }
                list.add(
                    AgentAction(
                        action = obj.getString("action"),
                        params = paramsMap,
                        reasoning = obj.optString("reasoning", null)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
