package com.example

import android.graphics.Rect
import com.example.models.AgentAction
import com.example.models.ScreenDump
import com.example.models.ScreenNode
import com.example.services.RecoveryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EvaAiUnitTest {

    @Test
    fun testScreenDumpPasswordRedaction() {
        val node1 = ScreenNode(
            index = 0,
            text = "secret_password_123",
            isPassword = true,
            isClickable = false,
            isEditable = true,
            bounds = Rect(0, 0, 100, 50)
        )
        val node2 = ScreenNode(
            index = 1,
            text = "Submit",
            isPassword = false,
            isClickable = true,
            bounds = Rect(0, 60, 100, 110)
        )

        val dump = ScreenDump(
            packageName = "com.example.bank",
            nodes = listOf(node1, node2)
        )

        val llmOutput = dump.toCompactDescription(compress = true)
        // Ensure the password text is strictly redacted
        assertFalse(llmOutput.contains("secret_password_123"))
        assertTrue(llmOutput.contains("PASSWORD_PROTECTED_FIELD") || llmOutput.contains("password_redacted"))
        assertTrue(llmOutput.contains("Submit"))
    }

    @Test
    fun testRecoveryEngineDetectsStuckLoop() {
        val recoveryEngine = RecoveryEngine()
        val action = AgentAction(action = "click_text", params = mapOf("text" to "Retry"))
        val screen = "Screen with same contents"

        assertNull(recoveryEngine.evaluateProgress(action, screen)) // attempt 1
        assertNull(recoveryEngine.evaluateProgress(action, screen)) // attempt 2
        val stuckMsg = recoveryEngine.evaluateProgress(action, screen) // attempt 3 -> stuck!
        assertNotNull(stuckMsg)
        assertTrue(stuckMsg!!.contains("stuck"))
    }
}
