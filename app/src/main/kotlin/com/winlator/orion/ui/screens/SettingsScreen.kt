package com.winlator.orion.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.winlator.orion.data.GlobalContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    
    var container by remember { mutableStateOf(GlobalContainer.load(context)) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var bigPictureMode by remember { mutableStateOf(prefs.getBoolean("big_picture_mode", false)) }
    
    var showScreenSizeDialog by remember { mutableStateOf(false) }
    var showGraphicsDriverDialog by remember { mutableStateOf(false) }
    var showDXWrapperDialog by remember { mutableStateOf(false) }
    var showBox64PresetDialog by remember { mutableStateOf(false) }
    var showWinVersionDialog by remember { mutableStateOf(false) }

    fun saveContainer() {
        GlobalContainer.save(context, container)
    }

    fun savePreference(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            SettingSwitchItem(
                icon = Icons.Filled.DarkMode,
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = darkMode,
                onCheckedChange = { 
                    darkMode = it
                    savePreference("dark_mode", it)
                }
            )
        }
        
        item {
            SettingSwitchItem(
                icon = Icons.Filled.Tv,
                title = "Big Picture Mode",
                subtitle = "Console-like interface",
                checked = bigPictureMode,
                onCheckedChange = { 
                    bigPictureMode = it
                    savePreference("big_picture_mode", it)
                }
            )
        }
        
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Display",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.AspectRatio,
                title = "Screen Size",
                subtitle = container.screenSize,
                onClick = { showScreenSizeDialog = true }
            )
        }

        item {
            SettingSwitchItem(
                icon = Icons.Filled.Speed,
                title = "Show FPS",
                subtitle = "Display frame rate overlay",
                checked = container.showFPS,
                onCheckedChange = {
                    container = container.copy(showFPS = it)
                    saveContainer()
                }
            )
        }
        
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Graphics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            SettingClickableItem(
                icon = Icons.Filled.Videocam,
                title = "Graphics Driver",
                subtitle = container.graphicsDriver,
                onClick = { showGraphicsDriverDialog = true }
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.Layers,
                title = "DirectX Wrapper",
                subtitle = container.dxwrapper,
                onClick = { showDXWrapperDialog = true }
            )
        }

        item {
            SettingSwitchItem(
                icon = Icons.Filled.BugReport,
                title = "DXVK HUD",
                subtitle = "Show performance overlay",
                checked = container.enableDXVKHud,
                onCheckedChange = {
                    container = container.copy(enableDXVKHud = it)
                    saveContainer()
                }
            )
        }
        
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Performance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.Memory,
                title = "Box64 Preset",
                subtitle = container.box64Preset,
                onClick = { showBox64PresetDialog = true }
            )
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Wine Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.DesktopWindows,
                title = "Windows Version",
                subtitle = container.winVersion,
                onClick = { showWinVersionDialog = true }
            )
        }

        item {
            SettingSwitchItem(
                icon = Icons.Filled.BugReport,
                title = "Wine Debug",
                subtitle = "Enable Wine debug output",
                checked = container.enableWineDebug,
                onCheckedChange = {
                    container = container.copy(enableWineDebug = it)
                    saveContainer()
                }
            )
        }
        
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            SettingSwitchItem(
                icon = Icons.Filled.Security,
                title = "Use Chroot (Requires Root)",
                subtitle = if (container.useChroot) "Better performance, requires root access" else "Using PRoot (no root required)",
                checked = container.useChroot,
                onCheckedChange = {
                    container = container.copy(useChroot = it)
                    saveContainer()
                }
            )
        }
        
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Orion Emulator",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android application for running Windows applications with Wine and Box86/Box64",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showScreenSizeDialog) {
        SelectionDialog(
            title = "Screen Size",
            options = listOf("800x600", "1024x768", "1280x720", "1280x1024", "1366x768", "1600x900", "1920x1080"),
            selectedOption = container.screenSize,
            onSelect = {
                container = container.copy(screenSize = it)
                saveContainer()
                showScreenSizeDialog = false
            },
            onDismiss = { showScreenSizeDialog = false }
        )
    }

    if (showGraphicsDriverDialog) {
        SelectionDialog(
            title = "Graphics Driver",
            options = listOf("turnip", "virgl", "zink"),
            selectedOption = container.graphicsDriver,
            onSelect = {
                container = container.copy(graphicsDriver = it)
                saveContainer()
                showGraphicsDriverDialog = false
            },
            onDismiss = { showGraphicsDriverDialog = false }
        )
    }

    if (showDXWrapperDialog) {
        SelectionDialog(
            title = "DirectX Wrapper",
            options = listOf("dxvk-2.3.1", "dxvk-1.10.3", "vkd3d-2.14.1", "wined3d"),
            selectedOption = container.dxwrapper,
            onSelect = {
                container = container.copy(dxwrapper = it)
                saveContainer()
                showDXWrapperDialog = false
            },
            onDismiss = { showDXWrapperDialog = false }
        )
    }

    if (showBox64PresetDialog) {
        SelectionDialog(
            title = "Box64 Preset",
            options = listOf("performance", "balanced", "compatibility"),
            selectedOption = container.box64Preset,
            onSelect = {
                container = container.copy(box64Preset = it)
                saveContainer()
                showBox64PresetDialog = false
            },
            onDismiss = { showBox64PresetDialog = false }
        )
    }

    if (showWinVersionDialog) {
        SelectionDialog(
            title = "Windows Version",
            options = listOf("win10", "win81", "win7", "winxp"),
            selectedOption = container.winVersion,
            onSelect = {
                container = container.copy(winVersion = it)
                saveContainer()
                showWinVersionDialog = false
            },
            onDismiss = { showWinVersionDialog = false }
        )
    }
}

@Composable
fun SettingSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun SettingClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { index ->
                    val option = options[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onSelect(option) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
