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

            callback.onProgress(5, "Extracting base system...")
            extractImageFS(cacheDir, callback)

            callback.onProgress(25, "Installing Proton Wine...")
            extractProton(callback)

            callback.onProgress(45, "Setting up Wine prefix...")
            setupWinePrefix(containerDir, callback)

            callback.onProgress(60, "Installing core components...")
            extractCoreComponents(callback)

            callback.onProgress(80, "Configuring system...")
            configureSystem(callback)

            callback.onProgress(95, "Finalizing installation...")
            finalizeInstallation()

            callback.onProgress(100, "Installation complete!")
            callback.onComplete()

            Log.i(TAG, "Installation completed successfully")
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
                }
            }
        )
        
        if (!success) {
            callback.onError("Failed to extract Proton Wine")
        }
    }

    private fun setupWinePrefix(containerDir: File, callback: InstallationCallback) {
        callback.onProgress(50, "Creating Wine prefix...")

        val patternAsset = "container_pattern_common.tzst"
        val patternCache = File(FileUtils.getCacheDir(context), patternAsset)

        if (!patternCache.exists()) {
            FileUtils.copyFromAssets(context, patternAsset, patternCache)
        }

        TarCompressorUtils.extract(
            patternCache,
            containerDir,
            object : TarCompressorUtils.ProgressCallback {
                override fun onProgress(current: Long, total: Long, currentFile: String) {
                    val percent = if (total > 0) ((current * 10 / total) + 50).toInt() else 50
                    callback.onProgress(percent, "Setting up: ${currentFile.substringAfterLast('/')}")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Wine prefix setup error: $error")
                }
            }
        )

        File(containerDir, "dosdevices").mkdirs()
        File(containerDir, "drive_c").mkdirs()
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

    private fun extractComponent(assetPath: String, destinationDir: File) {
        try {
            val componentCache = File(FileUtils.getCacheDir(context), assetPath.substringAfterLast('/'))
            
            if (!componentCache.exists()) {
                FileUtils.copyFromAssets(context, assetPath, componentCache)
            }

            FileUtils.ensureDirectoryExists(destinationDir)
            TarCompressorUtils.extract(componentCache, destinationDir)
            
            Log.i(TAG, "Extracted component: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract component: $assetPath", e)
        }
    }

    private fun configureSystem(callback: InstallationCallback) {
        callback.onProgress(85, "Configuring system...")

        val container = GlobalContainer.load(context)
        GlobalContainer.save(context, container)

        val containerDir = FileUtils.getContainerDir(context)
        val dosDevices = File(containerDir, "dosdevices")
        
        createDosDevices(dosDevices)

        callback.onProgress(90, "Setting permissions...")
        setExecutablePermissions()
    }

    private fun createDosDevices(dosDevices: File) {
        dosDevices.mkdirs()

        val cDrive = File(dosDevices, "c:")
        val containerDriveC = File(FileUtils.getContainerDir(context), "drive_c")
        
        if (!cDrive.exists()) {
            try {
                Runtime.getRuntime().exec(
                    arrayOf("ln", "-s", containerDriveC.absolutePath, cDrive.absolutePath)
                ).waitFor()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create C: drive symlink", e)
            }
        }

        val zDrive = File(dosDevices, "z:")
        val rootDir = File("/")
        
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
                    }
                }
            }
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
