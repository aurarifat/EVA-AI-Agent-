package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SavedSkillsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TaskHistoryScreen
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DangerRed
import com.example.viewmodels.MainViewModel
import com.example.viewmodels.ScreenDestination
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 840

    // Handle system back button
    BackHandler(enabled = currentScreen != ScreenDestination.CHAT) {
        viewModel.navigateTo(ScreenDestination.CHAT)
    }

    if (currentScreen == ScreenDestination.ONBOARDING) {
        OnboardingScreen(viewModel = viewModel)
        return
    }

    if (isTablet) {
        // Infinix Xpad 20 Tablet Landscape / Expanded Mode: Side Navigation Rail
        Row(modifier = modifier.fillMaxSize()) {
            TabletNavigationRail(
                currentScreen = currentScreen,
                onSelectScreen = { viewModel.navigateTo(it) },
                onNewChat = { viewModel.createNewChat() }
            )

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ScreenContent(
                    currentScreen = currentScreen,
                    viewModel = viewModel,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    } else {
        // Phone / Standard Portrait Layout: Modal Drawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(300.dp)
                ) {
                    DrawerContent(
                        viewModel = viewModel,
                        onCloseDrawer = { scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            ScreenContent(
                currentScreen = currentScreen,
                viewModel = viewModel,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@Composable
fun ScreenContent(
    currentScreen: ScreenDestination,
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit
) {
    when (currentScreen) {
        ScreenDestination.CHAT -> HomeScreen(viewModel = viewModel, onOpenDrawer = onOpenDrawer)
        ScreenDestination.SETTINGS -> SettingsScreen(viewModel = viewModel, onBack = { viewModel.navigateTo(ScreenDestination.CHAT) })
        ScreenDestination.TASK_HISTORY -> TaskHistoryScreen(viewModel = viewModel, onBack = { viewModel.navigateTo(ScreenDestination.CHAT) })
        ScreenDestination.SAVED_SKILLS -> SavedSkillsScreen(viewModel = viewModel, onBack = { viewModel.navigateTo(ScreenDestination.CHAT) })
        ScreenDestination.ONBOARDING -> OnboardingScreen(viewModel = viewModel)
        ScreenDestination.SCREEN_INSPECTOR -> HomeScreen(viewModel = viewModel, onOpenDrawer = onOpenDrawer)
    }
}

@Composable
fun TabletNavigationRail(
    currentScreen: ScreenDestination,
    onSelectScreen: (ScreenDestination) -> Unit,
    onNewChat: () -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.statusBarsPadding().navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Top App Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyanGlow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "EVA AI",
                        tint = CyanGlow,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // New Chat FAB in Rail
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CyanGlow)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = Color(0xFF00363D)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                NavigationRailItem(
                    selected = currentScreen == ScreenDestination.CHAT,
                    onClick = { onSelectScreen(ScreenDestination.CHAT) },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat") },
                    label = { Text("Chat", fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = CyanGlow,
                        indicatorColor = CyanGlow.copy(alpha = 0.2f)
                    )
                )

                NavigationRailItem(
                    selected = currentScreen == ScreenDestination.TASK_HISTORY,
                    onClick = { onSelectScreen(ScreenDestination.TASK_HISTORY) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Tasks") },
                    label = { Text("Tasks", fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = CyanGlow,
                        indicatorColor = CyanGlow.copy(alpha = 0.2f)
                    )
                )

                NavigationRailItem(
                    selected = currentScreen == ScreenDestination.SAVED_SKILLS,
                    onClick = { onSelectScreen(ScreenDestination.SAVED_SKILLS) },
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Skills") },
                    label = { Text("Skills", fontSize = 11.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = CyanGlow,
                        indicatorColor = CyanGlow.copy(alpha = 0.2f)
                    )
                )
            }

            // Bottom Settings
            NavigationRailItem(
                selected = currentScreen == ScreenDestination.SETTINGS,
                onClick = { onSelectScreen(ScreenDestination.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings", fontSize = 11.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = CyanGlow,
                    indicatorColor = CyanGlow.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
fun DrawerContent(
    viewModel: MainViewModel,
    onCloseDrawer: () -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val currentConvId by viewModel.currentConvId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // App Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CyanGlow.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = CyanGlow,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "EVA AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "On-Device Personal Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // New Chat Button
        Button(
            onClick = {
                viewModel.createNewChat()
                onCloseDrawer()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_new_chat_button")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF00363D))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Chat History",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scrollable Chat History
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(conversations) { conv ->
                val isSelected = conv.id == currentConvId
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) CyanGlow.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectConversation(conv.id)
                            onCloseDrawer()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = conv.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) CyanGlow else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { viewModel.deleteConversation(conv.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = DangerRed.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        // Bottom-Pinned Links
        DrawerLinkRow(
            icon = Icons.Default.History,
            title = "Task History",
            onClick = {
                viewModel.navigateTo(ScreenDestination.TASK_HISTORY)
                onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        DrawerLinkRow(
            icon = Icons.Default.Memory,
            title = "Saved Skills Memory",
            onClick = {
                viewModel.navigateTo(ScreenDestination.SAVED_SKILLS)
                onCloseDrawer()
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        DrawerLinkRow(
            icon = Icons.Default.Settings,
            title = "Settings",
            onClick = {
                viewModel.navigateTo(ScreenDestination.SETTINGS)
                onCloseDrawer()
            }
        )
    }
}

@Composable
fun DrawerLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
