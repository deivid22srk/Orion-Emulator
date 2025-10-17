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
import com.winlator.orion.ui.widgets.MultiSelectionDialog
import com.winlator.orion.ui.widgets.SelectionDialog
import com.winlator.orion.ui.widgets.SettingClickableItem

private fun parseWinComponents(wincomponents: String): Map<String, Boolean> {
    val map = mutableMapOf<String, Boolean>()
    wincomponents.split(",").forEach {
        val parts = it.split("=")
        if (parts.size == 2) {
            map[parts[0]] = parts[1] == "1"
        }
    }
    return map
}

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
    var showAudioDriverDialog by remember { mutableStateOf(false) }
    var showDesktopThemeDialog by remember { mutableStateOf(false) }
    var showStartupSelectionDialog by remember { mutableStateOf(false) }
    var showWinComponentsDialog by remember { mutableStateOf(false) }
    var showPrimaryControllerDialog by remember { mutableStateOf(false) }

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
            OutlinedTextField(
                value = container.drives,
                onValueChange = {
                    container = container.copy(drives = it)
                    saveContainer()
                },
                label = { Text("Drives") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.Gamepad,
                title = "Primary Controller",
                subtitle = "Controller ${container.primaryController}",
                onClick = { showPrimaryControllerDialog = true }
            )
        }

        item {
            OutlinedTextField(
                value = container.lc_all,
                onValueChange = {
                    container = container.copy(lc_all = it)
                    saveContainer()
                },
                label = { Text("LC_ALL") },
                modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = container.graphicsDriverConfig,
                onValueChange = {
                    container = container.copy(graphicsDriverConfig = it)
                    saveContainer()
                },
                label = { Text("Graphics Driver Config") },
                modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = container.dxwrapperConfig,
                onValueChange = {
                    container = container.copy(dxwrapperConfig = it)
                    saveContainer()
                },
                label = { Text("DX Wrapper Config") },
                modifier = Modifier.fillMaxWidth()
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
            OutlinedTextField(
                value = container.fexConfig,
                onValueChange = {
                    container = container.copy(fexConfig = it)
                    saveContainer()
                },
                label = { Text("FEX Config") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = container.cpuAffinity,
                onValueChange = {
                    container = container.copy(cpuAffinity = it)
                    saveContainer()
                },
                label = { Text("CPU Affinity") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = container.cpuListWoW64,
                onValueChange = {
                    container = container.copy(cpuListWoW64 = it)
                    saveContainer()
                },
                label = { Text("CPU Affinity (WoW64)") },
                modifier = Modifier.fillMaxWidth()
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
                icon = Icons.Filled.Audiotrack,
                title = "Audio Driver",
                subtitle = container.audioDriver,
                onClick = { showAudioDriverDialog = true }
            )
        }

        item {
            OutlinedTextField(
                value = container.midiSoundFont,
                onValueChange = {
                    container = container.copy(midiSoundFont = it)
                    saveContainer()
                },
                label = { Text("MIDI Soundfont") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.Wallpaper,
                title = "Desktop Theme",
                subtitle = container.desktopTheme,
                onClick = { showDesktopThemeDialog = true }
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.Extension,
                title = "Win Components",
                subtitle = "Select which Windows components to use",
                onClick = { showWinComponentsDialog = true }
            )
        }

        item {
            SettingClickableItem(
                icon = Icons.Filled.PlayArrow,
                title = "Startup Selection",
                subtitle = when (container.startupSelection.toInt()) {
                    0 -> "Normal"
                    1 -> "Essential"
                    else -> "Aggressive"
                },
                onClick = { showStartupSelectionDialog = true }
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

    if (showAudioDriverDialog) {
        SelectionDialog(
            title = "Audio Driver",
            options = listOf("pulseaudio", "alsa"),
            selectedOption = container.audioDriver,
            onSelect = {
                container = container.copy(audioDriver = it)
                saveContainer()
                showAudioDriverDialog = false
            },
            onDismiss = { showAudioDriverDialog = false }
        )
    }

    if (showDesktopThemeDialog) {
        SelectionDialog(
            title = "Desktop Theme",
            options = listOf("default", "forest", "sunset", "windows"),
            selectedOption = container.desktopTheme,
            onSelect = {
                container = container.copy(desktopTheme = it)
                saveContainer()
                showDesktopThemeDialog = false
            },
            onDismiss = { showDesktopThemeDialog = false }
        )
    }

    if (showStartupSelectionDialog) {
        SelectionDialog(
            title = "Startup Selection",
            options = listOf("Normal", "Essential", "Aggressive"),
            selectedOption = when (container.startupSelection.toInt()) {
                0 -> "Normal"
                1 -> "Essential"
                else -> "Aggressive"
            },
            onSelect = {
                val selection = when (it) {
                    "Normal" -> 0
                    "Essential" -> 1
                    else -> 2
                }
                container = container.copy(startupSelection = selection.toByte())
                saveContainer()
                showStartupSelectionDialog = false
            },
            onDismiss = { showStartupSelectionDialog = false }
        )
    }

    if (showWinComponentsDialog) {
        val winComponents = remember {
            parseWinComponents(container.wincomponents)
        }

        MultiSelectionDialog(
            title = "Win Components",
            options = winComponents,
            onConfirm = {
                container = container.copy(wincomponents = it.entries.joinToString(",") { entry -> "${entry.key}=${if (entry.value) "1" else "0"}" })
                saveContainer()
                showWinComponentsDialog = false
            },
            onDismiss = { showWinComponentsDialog = false }
        )
    }

    if (showPrimaryControllerDialog) {
        SelectionDialog(
            title = "Primary Controller",
            options = (1..4).map { "Controller $it" },
            selectedOption = "Controller ${container.primaryController}",
            onSelect = {
                val controller = it.substringAfter(" ").toInt()
                container = container.copy(primaryController = controller)
                saveContainer()
                showPrimaryControllerDialog = false
            },
            onDismiss = { showPrimaryControllerDialog = false }
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

