package com.example.services

import android.util.Log
import com.example.data.EvaRepository
import com.example.data.PreferencesManager
import com.example.models.AgentAction
import com.example.models.ChatMessage
import com.example.models.MessageRole
import com.example.models.TaskRecord
import com.example.models.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class TaskExecutor(
    private val aiService: AiService,
    private val screenAutomation: ScreenAutomationService,
    private val appLauncher: AppLauncherService,
    private val repository: EvaRepository,
    private val prefs: PreferencesManager
) {
    companion object {
        private const val TAG = "TaskExecutor"
    }

    private val recoveryEngine = RecoveryEngine()
    private val isCancelledFlag = AtomicBoolean(false)

    private val _isTaskRunning = MutableStateFlow(false)
    val isTaskRunning: StateFlow<Boolean> = _isTaskRunning.asStateFlow()

    private val _currentGoal = MutableStateFlow<String?>(null)
    val currentGoal: StateFlow<String?> = _currentGoal.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    fun cancel() {
        if (_isTaskRunning.value) {
            isCancelledFlag.set(true)
            Log.i(TAG, "Task cancellation requested by user")
        }
    }

    suspend fun execute(
        goal: String,
        conversationId: String,
        onStepProgress: suspend (ChatMessage) -> Unit
    ): TaskRecord = withContext(Dispatchers.IO) {
        val taskId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        val traceLogs = StringBuilder()
        val executedActions = mutableListOf<AgentAction>()
        val previousStepSummaries = mutableListOf<String>()

        _isTaskRunning.value = true
        _currentGoal.value = goal
        _currentStep.value = 0
        isCancelledFlag.set(false)
        recoveryEngine.reset()

        var status = TaskStatus.RUNNING
        var finalSummary = ""
        val maxSteps = if (prefs.disableMaxSteps.value) 50 else prefs.maxSteps.value

        try {
            if (!screenAutomation.isAccessibilityActive) {
                status = TaskStatus.FAILED
                finalSummary = "Accessibility Service is not active. Enable EVA AI in system Accessibility settings to allow screen automation."
                val failStep = ChatMessage(
                    conversationId = conversationId,
                    role = MessageRole.STEP,
                    content = finalSummary,
                    isTaskStep = true,
                    stepIcon = "⚠️",
                    isSuccess = false
                )
                onStepProgress(failStep)
                repository.saveMessage(failStep)
            } else {
                var stepCounter = 0

                while (stepCounter < maxSteps && !isCancelledFlag.get()) {
                    stepCounter++
                    _currentStep.value = stepCounter

                    // 1. Capture current screen state
                    val screenDump = screenAutomation.getScreenDescription(compress = prefs.useScreenCompression.value)

                    // 2. Ask AI for next single action
                    val aiResult = aiService.sendTaskMessage(
                        goal = goal,
                        screenDump = screenDump,
                        stepCount = stepCounter - 1,
                        previousSteps = previousStepSummaries
                    )

                    if (aiResult.isFailure) {
                        status = TaskStatus.FAILED
                        finalSummary = "AI Decision Error: ${aiResult.exceptionOrNull()?.message}"
                        break
                    }

                    val responseText = aiResult.getOrNull() ?: ""
                    traceLogs.append("\n[Step $stepCounter RAW AI]\n").append(responseText).append("\n")

                    // 3. Parse action JSON
                    val action = aiService.parseAction(responseText)
                    if (action == null) {
                        // AI returned conversational or malformed text
                        traceLogs.append("Failed to parse action JSON: $responseText\n")
                        finalSummary = "AI returned non-action response: $responseText"
                        break
                    }

                    executedActions.add(action)

                    // Check for completion
                    if (action.isComplete || action.action.equals("done", ignoreCase = true)) {
                        status = TaskStatus.SUCCESS
                        finalSummary = action.response.ifBlank { "Task completed successfully!" }

                        val completeStep = ChatMessage(
                            conversationId = conversationId,
                            role = MessageRole.STEP,
                            content = "Task Completed: $finalSummary",
                            isTaskStep = true,
                            stepIcon = "✅",
                            reasoning = action.reasoning,
                            stepNumber = stepCounter,
                            isSuccess = true
                        )
                        onStepProgress(completeStep)
                        repository.saveMessage(completeStep)
                        break
                    }

                    // 4. Stuck Detection via RecoveryEngine
                    val stuckDiagnosis = recoveryEngine.evaluateProgress(action, screenDump)
                    if (stuckDiagnosis != null) {
                        status = TaskStatus.FAILED
                        finalSummary = stuckDiagnosis
                        val stuckStep = ChatMessage(
                            conversationId = conversationId,
                            role = MessageRole.STEP,
                            content = stuckDiagnosis,
                            isTaskStep = true,
                            stepIcon = "🛑",
                            reasoning = action.reasoning,
                            stepNumber = stepCounter,
                            isSuccess = false
                        )
                        onStepProgress(stuckStep)
                        repository.saveMessage(stuckStep)
                        break
                    }

                    // 5. Emit step progress bubble
                    val stepDescription = formatStepDescription(action)
                    previousStepSummaries.add(stepDescription)

                    val stepMsg = ChatMessage(
                        conversationId = conversationId,
                        role = MessageRole.STEP,
                        content = "Step $stepCounter: $stepDescription",
                        isTaskStep = true,
                        stepIcon = "⏳",
                        reasoning = action.reasoning,
                        actionJson = action.action,
                        stepNumber = stepCounter,
                        isSuccess = true
                    )
                    onStepProgress(stepMsg)
                    repository.saveMessage(stepMsg)

                    // 6. Execute device step
                    val stepSuccess = executeStepAction(action)
                    traceLogs.append("Step $stepCounter executed [${action.action}]: success=$stepSuccess\n")

                    // 7. Brief delay for screen to settle
                    delay(700)

                    // Check if cancelled during execution
                    if (isCancelledFlag.get()) {
                        status = TaskStatus.CANCELLED
                        finalSummary = "Task was cancelled by user at step $stepCounter."
                        break
                    }
                }

                if (stepCounter >= maxSteps && status == TaskStatus.RUNNING) {
                    status = TaskStatus.FAILED
                    finalSummary = "Task reached the maximum step limit ($maxSteps steps)."
                }
            }
        } catch (e: CancellationException) {
            status = TaskStatus.CANCELLED
            finalSummary = "Task cancelled."
        } catch (e: Exception) {
            Log.e(TAG, "Task execution unexpected error", e)
            status = TaskStatus.FAILED
            finalSummary = "Unexpected error: ${e.message}"
        } finally {
            _isTaskRunning.value = false
            _currentGoal.value = null
            _currentStep.value = 0

            if (isCancelledFlag.get() && status != TaskStatus.CANCELLED) {
                status = TaskStatus.CANCELLED
                finalSummary = "Task was cancelled by user."
            }

            // Post final summary message
            val finalBubbleIcon = when (status) {
                TaskStatus.SUCCESS -> "🎯"
                TaskStatus.CANCELLED -> "⏹️"
                TaskStatus.FAILED -> "⚠️"
                else -> "ℹ️"
            }

            val finalMessage = ChatMessage(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "EXECUTE TASK ($status):\n$finalSummary",
                isTaskStep = false,
                stepIcon = finalBubbleIcon,
                isSuccess = status == TaskStatus.SUCCESS
            )
            onStepProgress(finalMessage)
            repository.saveMessage(finalMessage)
        }

        val record = TaskRecord(
            id = taskId,
            goal = goal,
            startTime = startTime,
            endTime = System.currentTimeMillis(),
            status = status,
            stepCount = executedActions.size,
            stepsSummary = finalSummary,
            traceLog = traceLogs.toString()
        )
        repository.saveTask(record)
        record
    }

    private suspend fun executeStepAction(action: AgentAction): Boolean {
        return when (action.action.lowercase()) {
            "click_text" -> {
                val text = action.getStringParam("text")
                screenAutomation.clickByText(text)
            }
            "click_at" -> {
                val x = action.getFloatParam("x")
                val y = action.getFloatParam("y")
                screenAutomation.clickAt(x, y)
            }
            "type_text" -> {
                val text = action.getStringParam("text")
                val hint = action.getStringParam("field_hint").ifEmpty { null }
                screenAutomation.typeText(text, hint)
            }
            "press_enter" -> screenAutomation.pressEnter()
            "scroll" -> {
                val dir = action.getStringParam("direction", "down")
                screenAutomation.scroll(dir)
            }
            "swipe" -> {
                val sx = action.getFloatParam("startX", 500f)
                val sy = action.getFloatParam("startY", 800f)
                val ex = action.getFloatParam("endX", 500f)
                val ey = action.getFloatParam("endY", 300f)
                screenAutomation.swipe(sx, sy, ex, ey)
            }
            "press_back" -> screenAutomation.pressBack()
            "press_home" -> screenAutomation.pressHome()
            "open_app" -> {
                val name = action.getStringParam("app_name")
                appLauncher.openAppByName(name).first
            }
            "wait" -> {
                delay(1000)
                true
            }
            else -> false
        }
    }

    private fun formatStepDescription(action: AgentAction): String {
        return when (action.action.lowercase()) {
            "click_text" -> "Clicking \"${action.getStringParam("text")}\""
            "click_at" -> "Tapping at (${action.getIntParam("x")}, ${action.getIntParam("y")})"
            "type_text" -> "Typing \"${action.getStringParam("text")}\""
            "press_enter" -> "Submitting input (Enter/Search)"
            "scroll" -> "Scrolling ${action.getStringParam("direction", "down")}"
            "swipe" -> "Swiping on screen"
            "press_back" -> "Pressing device Back"
            "press_home" -> "Pressing Home screen"
            "open_app" -> "Opening app \"${action.getStringParam("app_name")}\""
            "wait" -> "Waiting for screen to update"
            else -> action.action
        }
    }
}
