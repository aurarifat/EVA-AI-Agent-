package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.services.EvaAccessibilityService
import com.example.services.EvaOverlayService
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NeonViolet
import com.example.viewmodels.MainViewModel
import com.example.viewmodels.ScreenDestination
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = viewModel.preferences

    val apiKey by prefs.apiKey.collectAsState()
    val baseUrl by prefs.baseUrl.collectAsState()
    val modelName by prefs.modelName.collectAsState()
    val temperature by prefs.temperature.collectAsState()
    val maxTokens by prefs.maxTokens.collectAsState()
    val maxSteps by prefs.maxSteps.collectAsState()
    val disableMaxSteps by prefs.disableMaxSteps.collectAsState()
    val useScreenCompression by prefs.useScreenCompression.collectAsState()
    val sendSystemPrompt by prefs.sendSystemPrompt.collectAsState()
    val ttsEnabled by prefs.ttsEnabled.collectAsState()
    val themeMode by prefs.themeMode.collectAsState()

    val telegramBotToken by prefs.telegramBotToken.collectAsState()
    val telegramEnabled by prefs.telegramEnabled.collectAsState()
    val telegramPairedChatId by prefs.telegramPairedChatId.collectAsState()
    val overlayOrbEnabled by prefs.overlayOrbEnabled.collectAsState()

    val accessibilityActive by viewModel.accessibilityActive.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    // Permission launcher
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Refreshes composable state */ }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EVA AI Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // SECTION 1: Appearance
            SectionCard(title = "1. Appearance") {
                Text(
                    text = "Theme Preference",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (key, label) ->
                        val selected = themeMode == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) CyanGlow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) CyanGlow else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { prefs.setThemeMode(key) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) CyanGlow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // SECTION 2: AI Engine Configuration
            SectionCard(title = "2. AI Engine Configuration") {
                Text(
                    text = "API Key (Stored safely on-device)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { prefs.setApiKey(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_field"),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Key",
                                tint = CyanGlow
                            )
                        }
                    },
                    placeholder = { Text("Paste OpenAI / Gemini / Groq / Ollama API key") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Provider Presets Chips
                Text(
                    text = "Quick-Select Provider Defaults:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProviderChip("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-flash") { u, m ->
                        prefs.setBaseUrl(u); prefs.setModelName(m)
                    }
                    ProviderChip("Groq Cloud", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile") { u, m ->
                        prefs.setBaseUrl(u); prefs.setModelName(m)
                    }
                    ProviderChip("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat") { u, m ->
                        prefs.setBaseUrl(u); prefs.setModelName(m)
                    }
                    ProviderChip("Ollama Local", "http://10.0.2.2:11434/v1", "llama3.2") { u, m ->
                        prefs.setBaseUrl(u); prefs.setModelName(m)
                    }
                    ProviderChip("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "meta/llama-3.1-70b-instruct") { u, m ->
                        prefs.setBaseUrl(u); prefs.setModelName(m)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "API Base URL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { prefs.setBaseUrl(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("base_url_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Model Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { prefs.setModelName(it) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("model_name_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanGlow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isFetchingModels = true
                                fetchError = null
                                val result = viewModel.aiService.fetchAvailableModels(baseUrl, apiKey)
                                if (result.isSuccess) {
                                    fetchedModels = result.getOrDefault(emptyList())
                                    showModelDialog = true
                                } else {
                                    fetchError = result.exceptionOrNull()?.message
                                }
                                isFetchingModels = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("fetch_models_button")
                    ) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00363D))
                        } else {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF00363D))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fetch", color = Color(0xFF00363D))
                        }
                    }
                }

                if (fetchError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fetch error: $fetchError",
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // SECTION 3: Tuning & Boundaries
            SectionCard(title = "3. Tuning & Boundaries") {
                // Disable Max Steps Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disable Maximum Steps",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Can cause infinite loops in automation tasks",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (disableMaxSteps) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = disableMaxSteps,
                        onCheckedChange = { prefs.setDisableMaxSteps(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = DangerRed, checkedTrackColor = DangerRed.copy(alpha = 0.3f))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Max Steps Slider
                Text(
                    text = "Maximum Steps Per Task: ${if (disableMaxSteps) "Unlimited (Caution)" else maxSteps}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxSteps.toFloat(),
                    onValueChange = { prefs.setMaxSteps(it.toInt()) },
                    valueRange = 5f..35f,
                    steps = 30,
                    enabled = !disableMaxSteps,
                    colors = SliderDefaults.colors(thumbColor = CyanGlow, activeTrackColor = CyanGlow)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Context Limit (Max Tokens)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Context Limit (Max Tokens)", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Tokens generated per response", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = maxTokens.toString(),
                        onValueChange = { it.toIntOrNull()?.let { v -> prefs.setMaxTokens(v) } },
                        modifier = Modifier.width(100.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Temperature Slider
                Text(
                    text = "Temperature: ${String.format("%.2f", temperature)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = temperature,
                    onValueChange = { prefs.setTemperature(it) },
                    valueRange = 0.0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = NeonViolet, activeTrackColor = NeonViolet)
                )
            }

            // SECTION 4: Behavior & Extensions
            SectionCard(title = "4. Behavior & Extensions") {
                ToggleRow(
                    title = "Use Screen Compression",
                    subtitle = "Deduplicates empty UI nodes to save token usage",
                    checked = useScreenCompression,
                    onCheckedChange = { prefs.setUseScreenCompression(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                ToggleRow(
                    title = "Send System Prompt",
                    subtitle = "Recommended ON unless running custom fine-tuned model",
                    checked = sendSystemPrompt,
                    onCheckedChange = { prefs.setSendSystemPrompt(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                ToggleRow(
                    title = "Speak Assistant Replies (TTS)",
                    subtitle = "Reads EVA AI natural responses aloud using speech synthesis",
                    checked = ttsEnabled,
                    onCheckedChange = { prefs.setTtsEnabled(it) }
                )
            }

            // SECTION 5: Telegram Remote Access
            SectionCard(title = "5. Telegram Remote Access") {
                Text(
                    text = "Control your device remotely via a private paired Telegram bot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = telegramBotToken,
                    onValueChange = { prefs.setTelegramBotToken(it) },
                    label = { Text("Telegram Bot Token") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_token_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                ToggleRow(
                    title = "Enable Telegram Remote Control",
                    subtitle = if (telegramEnabled) "Bot listener is actively polling" else "Bot listener is stopped",
                    checked = telegramEnabled,
                    onCheckedChange = { viewModel.toggleTelegram(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Pairing Security Card
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Pairing Lock-Down Status:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (telegramPairedChatId.isNotBlank())
                                        "Paired with Chat ID: $telegramPairedChatId"
                                    else "Unpaired (First sender will become authorized controller)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (telegramPairedChatId.isNotBlank()) CyberGreen else AccentAmber
                                )
                            }

                            if (telegramPairedChatId.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { prefs.unpairTelegram() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Unpair")
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 6: Screen Control (Accessibility)
            SectionCard(title = "6. Screen Control (Accessibility)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (accessibilityActive) CyberGreen.copy(alpha = 0.2f) else AccentAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = if (accessibilityActive) CyberGreen else AccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (accessibilityActive) "Screen Control is Active" else "Screen Control Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (accessibilityActive) CyberGreen else AccentAmber
                        )
                        Text(
                            text = "Enables EVA AI to read screen elements and perform taps/swipes/text entry on your behalf.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { EvaAccessibilityService.openAccessibilitySettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_accessibility_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (accessibilityActive) "Review Accessibility Settings" else "Open Accessibility Settings",
                        color = Color(0xFF00363D),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SECTION 7: App Permissions List
            SectionCard(title = "7. App Permissions") {
                Text(
                    text = "EVA AI requires permissions only for the features you explicitly choose to use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                PermissionRow("Microphone (Voice)", Manifest.permission.RECORD_AUDIO) {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
                PermissionRow("Contacts (Search/Call)", Manifest.permission.READ_CONTACTS) {
                    permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                }
                PermissionRow("Phone (Direct Call)", Manifest.permission.CALL_PHONE) {
                    permLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                }
                PermissionRow("SMS (Send Messages)", Manifest.permission.SEND_SMS) {
                    permLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionRow("Notifications", Manifest.permission.POST_NOTIFICATIONS) {
                        permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }

                // Floating Overlay Orb Permission
                val canDraw = Settings.canDrawOverlays(context)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Floating Assistant Orb", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(text = "Draw over other apps for quick on-screen access", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (canDraw) {
                        Switch(
                            checked = overlayOrbEnabled,
                            onCheckedChange = { viewModel.toggleOverlayOrb(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanGlow)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            // SECTION 8: Execution Logs & Skills
            SectionCard(title = "8. Execution Logs & Memory") {
                NavigationRow(
                    icon = Icons.Default.History,
                    title = "View Task History",
                    subtitle = "Inspect step-by-step traces of all completed or cancelled tasks",
                    onClick = { viewModel.navigateTo(ScreenDestination.TASK_HISTORY) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                NavigationRow(
                    icon = Icons.Default.Memory,
                    title = "Saved Skills Memory",
                    subtitle = "Browse and replay recorded multi-step workflows",
                    onClick = { viewModel.navigateTo(ScreenDestination.SAVED_SKILLS) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Model Selection Dialog
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Select Fetched Model") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    fetchedModels.forEach { id ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (modelName == id) CyanGlow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    prefs.setModelName(id)
                                    showModelDialog = false
                                }
                        ) {
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CyanGlow
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ProviderChip(
    name: String,
    url: String,
    model: String,
    onSelect: (String, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanGlow.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onSelect(url, model) }
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = CyanGlow,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CyanGlow)
        )
    }
}

@Composable
fun PermissionRow(
    name: String,
    permission: String,
    onGrant: () -> Unit
) {
    val context = LocalContext.current
    val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
        }

        if (isGranted) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CyberGreen.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = CyberGreen, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Granted", color = CyberGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            OutlinedButton(
                onClick = onGrant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
fun NavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CyanGlow.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
