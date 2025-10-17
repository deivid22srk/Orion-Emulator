package com.winlator.orion.core

import android.content.Context
import android.util.Log
import com.winlator.orion.data.GlobalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ContainerManager(private val context: Context) {
    private val TAG = "ContainerManager"

    interface InstallationCallback {
        fun onProgress(progress: Int, message: String)
        fun onComplete()
        fun onError(error: String)
    }

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        val markerFile = File(context.filesDir, ".installation_complete")
        markerFile.exists()
    }

    suspend fun install(callback: InstallationCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress(0, "Preparing installation...")

            val orionDir = FileUtils.getOrionDir(context)
            val containerDir = FileUtils.getContainerDir(context)
            val cacheDir = FileUtils.getCacheDir(context)

            FileUtils.ensureDirectoryExists(orionDir)
            FileUtils.ensureDirectoryExists(containerDir)
            FileUtils.ensureDirectoryExists(cacheDir)

            callback.onProgress(5, "Installing base system...")
            extractImageFS(cacheDir, callback)

            callback.onProgress(25, "Installing Proton Wine...")
            extractProton(callback)

            callback.onProgress(40, "Setting up Wine prefix...")
            setupWinePrefixMinimal(containerDir, callback)

            callback.onProgress(70, "Installing Box64...")
            val imageFSRoot = File(context.filesDir, "imagefs")
            val box64Dir = File(imageFSRoot, "usr/bin")
            if (!extractComponent("box64/box64-0.3.7.tzst", box64Dir)) {
                callback.onError("Failed to extract Box64")
                return@withContext
            }

            callback.onProgress(85, "Configuring system...")
            configureSystemMinimal(callback)

            callback.onProgress(95, "Finalizing installation...")
            finalizeInstallation()

            callback.onProgress(98, "Installing themes...")
            extractComponent("themes.tzst", File(context.filesDir, "themes"))

            callback.onProgress(99, "Installing soundfonts...")
            extractComponent("soundfonts.tzst", File(context.filesDir, "soundfonts"))

            callback.onProgress(100, "Installation complete!")
            callback.onComplete()

            Log.i(TAG, "Installation completed successfully")
            
            // Instalar componentes opcionais em background
            withContext(Dispatchers.Default) {
                installOptionalComponents()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            callback.onError("Installation failed: ${e.message}")
        }
    }

    private fun extractImageFS(cacheDir: File, callback: InstallationCallback) {
        val imageFSAsset = "imagefs.txz"
        val imageFSCache = File(cacheDir, imageFSAsset)

        callback.onProgress(10, "Checking ImageFS...")
        
        // Verificar se asset existe
        val assetExists = try {
            context.assets.open(imageFSAsset).use { true }
        } catch (e: Exception) {
            Log.e(TAG, "ImageFS asset not found in APK", e)
            callback.onError("ImageFS not found. The APK may be incomplete. Size: ${getApkSize()}")
            return
        }
        
        if (!assetExists) {
            callback.onError("ImageFS asset not found in APK")
            return
        }

        callback.onProgress(12, "Copying base system...")
        if (!imageFSCache.exists()) {
            if (!FileUtils.copyFromAssets(context, imageFSAsset, imageFSCache)) {
                callback.onError("Failed to copy ImageFS from assets")
                return
            }
        }
        
        if (!imageFSCache.exists() || imageFSCache.length() == 0L) {
            callback.onError("ImageFS file is missing or empty")
            return
        }

        callback.onProgress(15, "Extracting base system...")
        val extractDir = File(context.filesDir, "imagefs")
        
        val success = TarCompressorUtils.extract(
            imageFSCache,
            extractDir,
            object : TarCompressorUtils.ProgressCallback {
                override fun onProgress(current: Long, total: Long, currentFile: String) {
                    val percent = if (total > 0) ((current * 10 / total) + 15).toInt() else 15
                    callback.onProgress(percent, "Extracting: ${currentFile.substringAfterLast('/')}")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "ImageFS extraction error: $error")
                    callback.onError("ImageFS extraction error: $error")
                }
            }
        )
        
        if (!success) {
            callback.onError("Failed to extract ImageFS")
        }
    }

    private fun extractProton(callback: InstallationCallback) {
        val container = GlobalContainer.load(context)
        val protonVersion = container.protonVersion
        val protonAsset = "$protonVersion.txz"
        val protonCache = File(FileUtils.getCacheDir(context), protonAsset)
        
        Log.i(TAG, "Extracting Proton: version=$protonVersion, asset=$protonAsset")
        Log.i(TAG, "Proton cache path: ${protonCache.absolutePath}")

        callback.onProgress(30, "Checking Proton Wine...")
        
        // Verificar se asset existe
        val assetExists = try {
            context.assets.open(protonAsset).use { true }
        } catch (e: Exception) {
            Log.e(TAG, "Proton asset not found: $protonAsset", e)
            callback.onError("Proton Wine ($protonVersion) not found in APK. Available assets: ${listAssets()}")
            return
        }
        
        if (!assetExists) {
            callback.onError("Proton asset not found: $protonAsset")
            return
        }

        callback.onProgress(32, "Copying Proton Wine...")
        if (!protonCache.exists()) {
            if (!FileUtils.copyFromAssets(context, protonAsset, protonCache)) {
                callback.onError("Failed to copy Proton from assets")
                return
            }
        }
        
        if (!protonCache.exists() || protonCache.length() == 0L) {
            callback.onError("Proton file is missing or empty")
            return
        }

        callback.onProgress(35, "Extracting Proton Wine...")
        val protonDir = File(context.filesDir, "proton")
        Log.i(TAG, "Proton extraction dir: ${protonDir.absolutePath}")

        val success = TarCompressorUtils.extract(
            protonCache,
            protonDir,
            object : TarCompressorUtils.ProgressCallback {
                override fun onProgress(current: Long, total: Long, currentFile: String) {
                    val percent = if (total > 0) ((current * 10 / total) + 35).toInt() else 35
                    callback.onProgress(percent, "Extracting: ${currentFile.substringAfterLast('/')}")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Proton extraction error: $error")
                    callback.onError("Proton extraction error: $error")
                }
            }
        )
        
        if (!success) {
            callback.onError("Failed to extract Proton Wine")
            return
        }
        
        // Verificar se wine foi extraído
        var wineBinary = File(protonDir, "bin/wine")
        Log.i(TAG, "Checking wine binary: exists=${wineBinary.exists()}, path=${wineBinary.absolutePath}")
        
        if (!wineBinary.exists()) {
            // O tar pode ter um diretório raiz, tentar encontrar
            Log.w(TAG, "wine not found at expected location, searching...")
            Log.i(TAG, "Proton dir contents: ${protonDir.listFiles()?.joinToString(", ") { it.name } ?: "empty"}")
            
            val rootDirs = protonDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (rootDirs.size == 1) {
                // Tem apenas um diretório raiz, mover conteúdo para cima
                val rootDir = rootDirs[0]
                Log.i(TAG, "Found root directory: ${rootDir.name}, moving contents up...")
                
                rootDir.listFiles()?.forEach { file ->
                    val dest = File(protonDir, file.name)
                    if (file.renameTo(dest)) {
                        Log.i(TAG, "Moved ${file.name}")
                    } else {
                        Log.e(TAG, "Failed to move ${file.name}")
                    }
                }
                
                rootDir.delete()
                wineBinary = File(protonDir, "bin/wine")
            }
            
            if (!wineBinary.exists()) {
                Log.e(TAG, "wine still not found after reorganization!")
                if (File(protonDir, "bin").exists()) {
                    Log.e(TAG, "Proton bin contents: ${File(protonDir, "bin").listFiles()?.joinToString(", ") { it.name } ?: "empty"}")
                } else {
                    Log.e(TAG, "bin directory does not exist!")
                }
                callback.onError("wine binary not found. Archive structure may be incorrect.")
                return
            }
        }
    }
    
    private suspend fun installOptionalComponents() {
        Log.i(TAG, "Installing optional components in background...")
        
        try {
            // Esses componentes não são necessários para Wine rodar
            // Serão instalados em background após setup
            
            Log.i(TAG, "Installing PulseAudio...")
            extractComponent("pulseaudio.tzst", File(context.filesDir, "pulseaudio"))
            
            Log.i(TAG, "Installing graphics drivers...")
            val graphicsDir = File(context.filesDir, "graphics_driver")
            extractComponent("graphics_driver/adrenotools-turnip25.1.0.tzst", graphicsDir)
            
            Log.i(TAG, "Optional components installed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install optional components (non-critical)", e)
        }
    }

    private fun setupWinePrefixMinimal(containerDir: File, callback: InstallationCallback) {
        callback.onProgress(50, "Creating Wine prefix...")

        val imageFSRoot = File(context.filesDir, "imagefs")
        val winePrefix = File(imageFSRoot, "home/xuser/.wine")
        val driveC = File(winePrefix, "drive_c")
        val dosDevices = File(winePrefix, "dosdevices")
        val users = File(driveC, "users/xuser")
        val programFiles = File(driveC, "Program Files")
        val programFilesX86 = File(driveC, "Program Files (x86)")
        val windows = File(driveC, "windows")
        val system32 = File(windows, "system32")
        val syswow64 = File(windows, "syswow64")
        
        listOf(winePrefix, driveC, dosDevices, users, programFiles, programFilesX86, windows, system32, syswow64).forEach {
            it.mkdirs()
            FileUtils.chmod(it, "755")
        }
        
        callback.onProgress(60, "Configuring Wine prefix...")
        
        // Criar arquivos de configuração básicos
        val userReg = File(winePrefix, "user.reg")
        if (!userReg.exists()) {
            userReg.writeText("""WINE REGISTRY Version 2

[Software\\Wine] 1234567890
"Version"="win10"
""")
        }
        
        // Create symlink for external storage in container dir (backward compatibility)
        try {
            if (containerDir.exists()) {
                Runtime.getRuntime().exec(
                    arrayOf("ln", "-sf", winePrefix.absolutePath, containerDir.absolutePath + "_link")
                ).waitFor()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create compatibility symlink", e)
        }
    }

    private fun extractCoreComponents(callback: InstallationCallback) {
        callback.onProgress(65, "Installing Box64...")
        extractComponent("box64/box64-0.3.7.tzst", File(context.filesDir, "box64"))

        callback.onProgress(70, "Installing PulseAudio...")
        extractComponent("pulseaudio.tzst", File(context.filesDir, "pulseaudio"))

        callback.onProgress(75, "Installing graphics drivers...")
        val graphicsDir = File(context.filesDir, "graphics_driver")
        extractComponent("graphics_driver/adrenotools-turnip25.1.0.tzst", graphicsDir)
    }

    private fun extractComponent(assetPath: String, destinationDir: File): Boolean {
        return try {
            val componentCache = File(FileUtils.getCacheDir(context), assetPath.substringAfterLast('/'))
            
            if (!componentCache.exists()) {
                if (!FileUtils.copyFromAssets(context, assetPath, componentCache)) {
                    Log.e(TAG, "Failed to copy asset: $assetPath")
                    return false
                }
            }

            FileUtils.ensureDirectoryExists(destinationDir)
            val success = TarCompressorUtils.extract(componentCache, destinationDir)
            
            if (success) {
                Log.i(TAG, "Extracted component: $assetPath")
            } else {
                Log.e(TAG, "Failed to extract component: $assetPath")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract component: $assetPath", e)
            false
        }
    }

    private fun configureSystemMinimal(callback: InstallationCallback) {
        callback.onProgress(87, "Configuring system...")

        val container = GlobalContainer.load(context)
        GlobalContainer.save(context, container)

        val imageFSRoot = File(context.filesDir, "imagefs")
        val winePrefix = File(imageFSRoot, "home/xuser/.wine")
        val dosDevices = File(winePrefix, "dosdevices")
        
        createDosDevices(dosDevices)

        callback.onProgress(90, "Setting permissions...")
        setExecutablePermissions()
    }

    private fun createDosDevices(dosDevices: File) {
        // dosDevices is already created in setupWinePrefixMinimal
        // This function is now for backward compatibility
        val imageFSRoot = File(context.filesDir, "imagefs")
        val winePrefix = File(imageFSRoot, "home/xuser/.wine")
        val dosDevicesDir = File(winePrefix, "dosdevices")
        
        dosDevicesDir.mkdirs()

        val cDrive = File(dosDevicesDir, "c:")
        val driveC = File(winePrefix, "drive_c")
        
        if (!cDrive.exists()) {
            try {
                Runtime.getRuntime().exec(
                    arrayOf("ln", "-s", driveC.absolutePath, cDrive.absolutePath)
                ).waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create C: drive symlink", e)
            }
        }

        val zDrive = File(dosDevicesDir, "z:")
        val rootDir = imageFSRoot
        
        if (!zDrive.exists()) {
            try {
                Runtime.getRuntime().exec(
                    arrayOf("ln", "-s", rootDir.absolutePath, zDrive.absolutePath)
                ).waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Z: drive symlink", e)
            }
        }
    }

    private fun setExecutablePermissions() {
        // Copy proot from native libs to imagefs (optional, for future use)
        installPRoot()
        
        val binDirs = listOf(
            File(context.filesDir, "imagefs/bin"),
            File(context.filesDir, "imagefs/usr/bin"),
            File(context.filesDir, "proton/bin"),
            File(context.filesDir, "box64/bin")
        )

        binDirs.forEach { binDir ->
            if (binDir.exists()) {
                binDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        FileUtils.makeExecutable(file)
                        Log.d(TAG, "Made executable: ${file.absolutePath}")
                    }
                }
            } else {
                Log.w(TAG, "Binary directory doesn't exist: ${binDir.absolutePath}")
            }
        }
        
        // Create home directory structure
        val imageFSRoot = File(context.filesDir, "imagefs")
        val homeXuser = File(imageFSRoot, "home/xuser")
        if (!homeXuser.exists()) {
            homeXuser.mkdirs()
            FileUtils.chmod(homeXuser, "755")
        }
        
        // Create tmp directory
        val tmpDir = File(imageFSRoot, "usr/tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
            FileUtils.chmod(tmpDir, "777")
        }
    }
    
    private fun installPRoot() {
        try {
            // PRoot is compiled as a native binary
            val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
            val prootSource = File(nativeLibDir, "libproot.so")
            
            val imageFsUsrBin = File(context.filesDir, "imagefs/usr/bin")
            FileUtils.ensureDirectoryExists(imageFsUsrBin)
            
            val prootDest = File(imageFsUsrBin, "proot")
            
            if (prootSource.exists()) {
                prootSource.copyTo(prootDest, overwrite = true)
                FileUtils.makeExecutable(prootDest)
                Log.i(TAG, "PRoot installed successfully to ${prootDest.absolutePath}")
            } else {
                Log.w(TAG, "PRoot binary not found at ${prootSource.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install PRoot (non-critical)", e)
        }
    }

    private fun finalizeInstallation() {
        val markerFile = File(context.filesDir, ".installation_complete")
        markerFile.writeText("installed_at=${System.currentTimeMillis()}")
        
        Log.i(TAG, "Installation marker created")
    }

    suspend fun uninstall(callback: InstallationCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress(0, "Removing container...")

            val containerDir = FileUtils.getContainerDir(context)
            FileUtils.deleteRecursive(containerDir)

            callback.onProgress(50, "Cleaning up...")

            val cacheDir = FileUtils.getCacheDir(context)
            FileUtils.deleteRecursive(cacheDir)

            val markerFile = File(context.filesDir, ".installation_complete")
            markerFile.delete()

            callback.onProgress(100, "Uninstall complete")
            callback.onComplete()

            Log.i(TAG, "Uninstallation completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Uninstallation failed", e)
            callback.onError("Uninstall failed: ${e.message}")
        }
    }
    
    private fun listAssets(): String {
        return try {
            context.assets.list("")?.joinToString(", ") ?: "none"
        } catch (e: Exception) {
            "error listing assets"
        }
    }
    
    private fun getApkSize(): String {
        return try {
            val apkPath = context.packageCodePath
            val size = File(apkPath).length()
            FileUtils.formatFileSize(size)
        } catch (e: Exception) {
            "unknown"
        }
    }
}
