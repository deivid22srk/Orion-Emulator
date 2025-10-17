package com.winlator.orion

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.winlator.orion.core.ContainerManager
import com.winlator.orion.ui.screens.*
import com.winlator.orion.ui.theme.OrionEmulatorTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkMode by mutableStateOf(false)
    private var isInstalled by mutableStateOf<Boolean?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeApp()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestAllFilesAccess()
            } else {
                initializeApp()
            }
        } else {
            checkStoragePermission()
        }
    }

    private fun checkStoragePermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeApp()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun requestAllFilesAccess() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Storage Permission Required")
            .setMessage("This app needs storage permission to function properly.")
            .setPositiveButton("Grant") { dialog, which ->
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("Cancel") { dialog, which ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Denied")
            .setMessage("Storage permission is required for this app to work.")
            .setPositiveButton("OK") { dialog, which ->
                finish()
            }
            .show()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun initializeApp() {
        val containerManager = ContainerManager(this)
        
        GlobalScope.launch {
            isInstalled = containerManager.isInstalled()
        }
        
        setContent {
            OrionEmulatorTheme(darkTheme = isDarkMode) {
                when (isInstalled) {
                    null -> LoadingScreen()
                    false -> SetupScreen(onSetupComplete = {
                        isInstalled = true
                    })
                    true -> MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    val items = listOf(
        NavigationItem("containers", "Containers", Icons.Filled.Folder),
        NavigationItem("contents", "Contents", Icons.Filled.Download),
        NavigationItem("shortcuts", "Shortcuts", Icons.Filled.Apps),
        NavigationItem("controls", "Controls", Icons.Filled.Gamepad),
        NavigationItem("settings", "Settings", Icons.Filled.Settings)
    )

    var selectedItem by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orion Emulator") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            selectedItem = index
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "containers",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("containers") { ContainersScreen() }
            composable("contents") { ContentsScreen() }
            composable("shortcuts") { ShortcutsScreen() }
            composable("controls") { ControlsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                "Loading...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
