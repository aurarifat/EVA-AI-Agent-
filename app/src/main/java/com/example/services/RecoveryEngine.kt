package com.example.services

import com.example.models.AgentAction

class RecoveryEngine {
    private val actionHistory = mutableListOf<String>()
    private val screenHashes = mutableListOf<Int>()
    private var consecutiveStuckCount = 0

    fun reset() {
        actionHistory.clear()
        screenHashes.clear()
        consecutiveStuckCount = 0
    }

    /**
     * Records a step attempt and checks if the automation loop is stuck.
     * Returns a non-null diagnostic string if stuck.
     */
    fun evaluateProgress(action: AgentAction, screenDumpText: String): String? {
        val actionKey = "${action.action}:${action.params}"
        val screenHash = screenDumpText.hashCode()

        val isSameAction = actionHistory.isNotEmpty() && actionHistory.last() == actionKey
        val isSameScreen = screenHashes.isNotEmpty() && screenHashes.last() == screenHash

        actionHistory.add(actionKey)
        screenHashes.add(screenHash)

        if (isSameAction && isSameScreen) {
            consecutiveStuckCount++
        } else {
            consecutiveStuckCount = 0
        }

        if (consecutiveStuckCount >= 2) {
            return "Task stopped: Recovery Engine detected a stuck loop. The screen remained unchanged after 3 consecutive identical actions (${action.action}). The app might be showing a modal, dialog, or waiting for manual intervention."
        }

        return null
    }
}
