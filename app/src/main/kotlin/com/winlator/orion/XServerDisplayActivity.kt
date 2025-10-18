package com.winlator.orion

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.winlator.orion.core.WineExecutor
import com.winlator.orion.core.XServerManager
import com.winlator.orion.ui.theme.OrionEmulatorTheme
import kotlinx.coroutines.launch

class XServerDisplayActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full-screen immersive mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        val executablePath = intent.getStringExtra("executable") ?: "explorer.exe"
        val arguments = intent.getStringExtra("arguments") ?: ""
        val windowTitle = intent.getStringExtra("title") ?: "Wine Application"
        
        setContent {
            OrionEmulatorTheme {
                XServerFullScreenContent(
                    executablePath = executablePath,
                    arguments = arguments,
                    windowTitle = windowTitle,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun XServerFullScreenContent(
    executablePath: String,
    arguments: String,
    windowTitle: String,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    val xServerManager = remember { XServerManager(context) }
    val wineExecutor = remember { WineExecutor(context) }
    
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onDispose {
            scope.launch {
                wineExecutor.killWineServer()
                xServerManager.stop()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                        
                        setOnClickListener {
                            showControls = !showControls
                        }
                        
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                xServerManager.start(holder.surface, object : XServerManager.XServerCallback {
                                    override fun onStarted() {
                                        scope.launch {
                                            kotlinx.coroutines.delay(1000)
                                            
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
                                "Starting $windowTitle...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // Floating controls overlay
            if (showControls && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.align(Alignment.TopEnd),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        wineExecutor.killWineServer()
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop Wine")
                            }
                            
                            IconButton(onClick = { showExitDialog = true }) {
                                Icon(Icons.Filled.Close, contentDescription = "Exit")
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
