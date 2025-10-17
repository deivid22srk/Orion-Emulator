package com.winlator.orion.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.winlator.orion.core.WineExecutor
import com.winlator.orion.core.XServerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XServerDisplayScreen(
    executablePath: String,
    arguments: String = "",
    windowTitle: String = "Wine Application",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val xServerManager = remember { XServerManager(context) }
    val wineExecutor = remember { WineExecutor(context) }
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                wineExecutor.killWineServer()
                xServerManager.stop()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(windowTitle) },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            wineExecutor.killWineServer()
                        }
                    }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop Wine")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        "Failed to Start",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Button(onClick = onClose) {
                        Text("Close")
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            setZOrderOnTop(false)
                            holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
                            
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    xServerManager.start(holder.surface, object : XServerManager.XServerCallback {
                                        override fun onStarted() {
                                            scope.launch {
                                                Thread.sleep(1000)
                                                
                                                val process = wineExecutor.execute(
                                                    WineExecutor.ExecutionConfig(
                                                        executablePath = executablePath,
                                                        arguments = arguments,
                                                        windowTitle = windowTitle
                                                    )
                                                )
                                                
                                                if (process != null) {
                                                    isLoading = false
                                                } else {
                                                    errorMessage = "Failed to start Wine application"
                                                }
                                            }
                                        }

                                        override fun onStopped() {
                                        }

                                        override fun onError(error: String) {
                                            errorMessage = error
                                            isLoading = false
                                        }
                                    })
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    xServerManager.stop()
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Starting Wine Application...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Application?") },
            text = { Text("Are you sure you want to close this Wine application?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        scope.launch {
                            wineExecutor.killWineServer()
                            xServerManager.stop()
                            onClose()
                        }
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
