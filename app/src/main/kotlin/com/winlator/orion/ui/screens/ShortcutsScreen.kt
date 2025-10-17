package com.winlator.orion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winlator.orion.data.AppShortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(onLaunchApp: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(AppShortcut.loadAll(context)) }
    var showAddDialog by remember { mutableStateOf(false) }

    val defaultShortcuts = remember {
        listOf(
            DefaultShortcut(
                icon = Icons.Filled.DesktopWindows,
                title = "Wine Desktop",
                description = "Windows Explorer Desktop",
                executable = "explorer.exe",
                arguments = "/desktop=shell,1280x720"
            ),
            DefaultShortcut(
                icon = Icons.Filled.Settings,
                title = "Wine Configuration",
                description = "Configure Wine Settings",
                executable = "winecfg.exe",
                arguments = ""
            ),
            DefaultShortcut(
                icon = Icons.Filled.Terminal,
                title = "Command Prompt",
                description = "Windows Command Prompt",
                executable = "cmd.exe",
                arguments = ""
            ),
            DefaultShortcut(
                icon = Icons.Filled.Folder,
                title = "File Manager",
                description = "Windows File Explorer",
                executable = "explorer.exe",
                arguments = "C:\\"
            ),
            DefaultShortcut(
                icon = Icons.Filled.TextFields,
                title = "Notepad",
                description = "Windows Notepad",
                executable = "notepad.exe",
                arguments = ""
            ),
            DefaultShortcut(
                icon = Icons.Filled.Computer,
                title = "Registry Editor",
                description = "Windows Registry Editor",
                executable = "regedit.exe",
                arguments = ""
            ),
            DefaultShortcut(
                icon = Icons.Filled.Task,
                title = "Task Manager",
                description = "Windows Task Manager",
                executable = "taskmgr.exe",
                arguments = ""
            ),
            DefaultShortcut(
                icon = Icons.Filled.Calculate,
                title = "Calculator",
                description = "Windows Calculator",
                executable = "calc.exe",
                arguments = ""
            )
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Shortcut")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Wine Applications",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(defaultShortcuts) { shortcut ->
                DefaultShortcutCard(
                    shortcut = shortcut,
                    onClick = {
                        onLaunchApp(shortcut.executable, shortcut.arguments, shortcut.title)
                    }
                )
            }

            if (shortcuts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Custom Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(shortcuts) { shortcut ->
                    CustomShortcutCard(
                        shortcut = shortcut,
                        onClick = {
                            onLaunchApp(shortcut.path, shortcut.arguments, shortcut.name)
                        },
                        onDelete = {
                            AppShortcut.remove(context, shortcut.id)
                            shortcuts = AppShortcut.loadAll(context)
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddShortcutDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, path, arguments ->
                val newShortcut = AppShortcut(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    path = path,
                    arguments = arguments
                )
                AppShortcut.add(context, newShortcut)
                shortcuts = AppShortcut.loadAll(context)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun DefaultShortcutCard(
    shortcut: DefaultShortcut,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                shortcut.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shortcut.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    shortcut.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (shortcut.executable.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        shortcut.executable,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
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
fun CustomShortcutCard(
    shortcut: AppShortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                Icons.Filled.Apps,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    shortcut.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    shortcut.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Shortcut?") },
            text = { Text("Are you sure you want to delete '${shortcut.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddShortcutDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var arguments by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shortcut") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Executable Path") },
                    placeholder = { Text("C:\\Program Files\\app.exe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = arguments,
                    onValueChange = { arguments = it },
                    label = { Text("Arguments (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, path, arguments) },
                enabled = name.isNotBlank() && path.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

data class DefaultShortcut(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val executable: String,
    val arguments: String = ""
)
