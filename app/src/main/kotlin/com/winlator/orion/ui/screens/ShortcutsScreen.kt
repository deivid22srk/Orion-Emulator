package com.winlator.orion.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.winlator.orion.core.ContainerManager
import com.winlator.orion.data.AppShortcut
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(onLaunchApp: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf(AppShortcut.loadAll(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showNotInstalledDialog by remember { mutableStateOf(false) }
    
    val containerManager = remember { com.winlator.orion.core.ContainerManager(context) }
    var isInstalled by remember { mutableStateOf<Boolean?>(null) }
    
    LaunchedEffect(Unit) {
        isInstalled = containerManager.isInstalled()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "Wine Applications",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(defaultShortcuts) { shortcut ->
                    DefaultShortcutGridCard(
                        shortcut = shortcut,
                        onClick = {
                            if (isInstalled == true) {
                                onLaunchApp(shortcut.executable, shortcut.arguments, shortcut.title)
                            } else {
                                showNotInstalledDialog = true
                            }
                        }
                    )
                }
                
                items(shortcuts) { shortcut ->
                    CustomShortcutGridCard(
                        shortcut = shortcut,
                        onClick = {
                            if (isInstalled == true) {
                                onLaunchApp(shortcut.path, shortcut.arguments, shortcut.name)
                            } else {
                                showNotInstalledDialog = true
                            }
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
    
    if (showNotInstalledDialog) {
        AlertDialog(
            onDismissRequest = { showNotInstalledDialog = false },
            icon = { Icon(Icons.Filled.Error, contentDescription = null) },
            title = { Text("Installation Required") },
            text = { 
                Text("Wine and its components are not installed yet. Please complete the setup process first.")
            },
            confirmButton = {
                Button(onClick = { showNotInstalledDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun DefaultShortcutGridCard(
    shortcut: DefaultShortcut,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                shortcut.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                shortcut.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                minLines = 2
            )
        }
    }
}

@Composable
fun CustomShortcutGridCard(
    shortcut: AppShortcut,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    shortcut.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2
                )
            }
            
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
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
