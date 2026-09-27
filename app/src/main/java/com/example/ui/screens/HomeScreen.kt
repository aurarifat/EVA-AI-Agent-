package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MessageBubble
import com.example.ui.components.ScreenInspectorPane
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DangerRed
import com.example.viewmodels.MainViewModel
import com.example.viewmodels.ScreenDestination

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isTaskRunning by viewModel.isTaskRunning.collectAsState()
    val currentGoal by viewModel.currentGoal.collectAsState()
    val currentStepNumber by viewModel.currentStepNumber.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isAgentMode by viewModel.preferences.isAgentMode.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val transcript by viewModel.speechTranscript.collectAsState()
    val accessibilityActive by viewModel.accessibilityActive.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(transcript) {
        if (transcript.isNotBlank()) {
            inputText = transcript
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val configuration = LocalConfiguration.current
    val isTabletLayout = configuration.screenWidthDp >= 600

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top App Bar
            HomeTopBar(
                onOpenDrawer = onOpenDrawer,
                onNewChat = { viewModel.createNewChat() },
                onOpenSettings = { viewModel.navigateTo(ScreenDestination.SETTINGS) },
                accessibilityActive = accessibilityActive
            )

            // Segmented Switcher: Chat vs Agent Mode
            ModeSegmentedSwitch(
                isAgentMode = isAgentMode,
                onModeChange = { viewModel.preferences.setAgentMode(it) }
            )

            // Active Task Progress Notification Banner
            AnimatedVisibility(visible = isTaskRunning) {
                TaskProgressBanner(
                    goal = currentGoal ?: "Device automation task in progress",
                    stepNumber = currentStepNumber,
                    onCancel = { viewModel.cancelActiveTask() }
                )
            }

            // Main Content Area: Responsive Split on Infinix Xpad 20 Tablet
            if (isTabletLayout) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Left Pane: Chat Conversation (60% width)
                    Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                        ChatListSection(
                            messages = messages,
                            isGenerating = isGenerating,
                            listState = listState
                        )
                    }

                    // Right Pane: Live Screen & Tablet Automation Inspector (42% width)
                    Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                        ScreenInspectorPane(screenAutomation = viewModel.screenAutomation)
                    }
                }
            } else {
                // Phone compact layout: Full chat list
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ChatListSection(
                        messages = messages,
                        isGenerating = isGenerating,
                        listState = listState
                    )
                }
            }

            // Bottom Input Bar
            BottomInputBar(
                inputText = inputText,
                onInputChanged = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                isTaskRunning = isTaskRunning,
                isGenerating = isGenerating,
                isListening = isListening,
                onToggleVoice = {
                    if (isListening) viewModel.stopVoiceInput() else viewModel.startVoiceInput()
                },
                onCancelTask = { viewModel.cancelActiveTask() }
            )
        }
    }
}

@Composable
fun HomeTopBar(
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    accessibilityActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Drawer Menu Button
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier.testTag("drawer_menu_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = "Open Drawer",
                tint = CyanGlow
            )
        }

        // Center Title + Glowing Status Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(if (accessibilityActive) CyberGreen else CyanGlow)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "EVA AI",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Right Actions: New Chat & Settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onNewChat,
                modifier = Modifier.testTag("new_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ModeSegmentedSwitch(
    isAgentMode: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .testTag("mode_segmented_switch")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp)
        ) {
            // Chat Mode Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isAgentMode) CyanGlow.copy(alpha = 0.2f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (!isAgentMode) CyanGlow else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onModeChange(false) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = null,
                        tint = if (!isAgentMode) CyanGlow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chat Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (!isAgentMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isAgentMode) CyanGlow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Agent Mode Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAgentMode) CyanGlow else Color.Transparent)
                    .clickable { onModeChange(true) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = if (isAgentMode) Color(0xFF00363D) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Agent Mode",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isAgentMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAgentMode) Color(0xFF00363D) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TaskProgressBanner(
    goal: String,
    stepNumber: Int,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("task_progress_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF002830)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanGlow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = CyanGlow,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Running Task (Step $stepNumber)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanGlow,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = goal,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DangerRed
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("cancel_task_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
fun ChatListSection(
    messages: List<com.example.models.ChatMessage>,
    isGenerating: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CyanGlow.copy(alpha = 0.1f))
                        .border(2.dp, CyanGlow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "EVA AI Ready",
                        tint = CyanGlow,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome to EVA AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ask anything or command device actions:\n• \"Open Settings and adjust brightness\"\n• \"Set an alarm for 7:30 AM\"\n• \"Search contacts for Alex\"\n• \"Read the current screen\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(message = msg)
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyanGlow,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EVA AI is thinking...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isTaskRunning: Boolean,
    isGenerating: Boolean,
    isListening: Boolean,
    onToggleVoice: () -> Unit,
    onCancelTask: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Mic Button with glowing pulse
            val micBg = if (isListening) DangerRed else CyanGlow.copy(alpha = 0.15f)
            val micTint = if (isListening) Color.White else CyanGlow

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(micBg)
                    .clickable { onToggleVoice() }
                    .testTag("voice_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = micTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text Input Field
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        if (isListening) "Listening to speech..." else "Ask EVA or command a device task...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanGlow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                enabled = !isTaskRunning
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send or Cancel Action Button
            if (isTaskRunning) {
                Button(
                    onClick = onCancelTask,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("cancel_active_task_button"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Cancel Task",
                        tint = Color.White
                    )
                }
            } else {
                Button(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isGenerating,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanGlow,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("send_button"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isGenerating) Color(0xFF00363D) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
