package com.winlator.orion.core

import android.content.Context
import android.util.Log
import com.winlator.orion.data.GlobalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WineExecutor(private val context: Context) {
    private val TAG = "WineExecutor"
    
    data class ExecutionConfig(
        val executablePath: String,
        val arguments: String = "",
        val workingDirectory: String? = null,
        val windowTitle: String = "Wine Application"
    )

    suspend fun execute(config: ExecutionConfig): Process? = withContext(Dispatchers.IO) {
        try {
            val container = GlobalContainer.load(context)
            val env = buildEnvironment(container)
            
            Log.i(TAG, "Executing: ${config.executablePath}")
            Log.i(TAG, "Arguments: ${config.arguments}")
            Log.i(TAG, "Working Directory: ${config.workingDirectory ?: "default"}")

            val workDir = if (config.workingDirectory != null) {
                File(config.workingDirectory)
            } else {
                val containerDir = FileUtils.getContainerDir(context)
                File(containerDir, "drive_c")
            }

            val command = buildCommand(config, container, env)
            
            if (container.enableWineDebug) {
                val logFile = getWineLogFile()
                Log.i(TAG, "Wine logs will be saved to: ${logFile.absolutePath}")
            }
            
            // Execute command via ProcessBuilder
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workDir)
            processBuilder.environment().putAll(env)
            
            if (container.enableWineDebug) {
                val logFile = getWineLogFile()
                processBuilder.redirectErrorStream(true)
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            } else {
                processBuilder.redirectErrorStream(true)
            }
            
            val process = processBuilder.start()
            
            Log.i(TAG, "Wine process started with PID: ${getPid(process)}")
            
            if (container.enableWineDebug) {
                monitorProcessOutput(process)
            }
            
            process
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Wine application", e)
            null
        }
    }

    private fun buildCommand(config: ExecutionConfig, container: GlobalContainer, env: Map<String, String>): List<String> {
        val commands = mutableListOf<String>()
        
        val imageFSRoot = File(context.filesDir, "imagefs")
        val box64Bin = File(context.filesDir, "box64/bin/box64")
        val wineBin = File(context.filesDir, "proton/bin/wine")
        val containerDir = FileUtils.getContainerDir(context)
        
        // Check if user wants to use chroot (requires root) or proot
        if (container.useChroot) {
            Log.i(TAG, "Using chroot (requires root access)")
            
            // Use a wrapper script for chroot
            val chrootScript = createChrootScript(
                imageFSRoot,
                containerDir,
                box64Bin,
                wineBin,
                config,
                env
            )
            
            commands.add("su")
            commands.add("-c")
            commands.add(chrootScript.absolutePath)
            
        } else {
            // Use PRoot via JNI (no root required)
            Log.i(TAG, "Using PRoot via JNI (no root required)")
            
            // Use a wrapper script that calls PRoot via JNI
            val prootScript = createPRootScript(
                imageFSRoot,
                containerDir,
                box64Bin,
                wineBin,
                config,
                env
            )
            
            commands.add("/system/bin/sh")
            commands.add(prootScript.absolutePath)
        }
        
        return commands
    }
    
    private fun createPRootScript(
        imageFSRoot: File,
        containerDir: File,
        box64Bin: File,
        wineBin: File,
        config: ExecutionConfig,
        env: Map<String, String>
    ): File {
        val scriptFile = File(context.cacheDir, "proot_launch_${System.currentTimeMillis()}.sh")
        
        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine()
            
            // Export environment variables
            env.forEach { (key, value) ->
                appendLine("export $key='$value'")
            }
            appendLine()
            
            // Get native library directory
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val prootBin = "$nativeLibDir/libproot.so"
            
            appendLine("# Execute PRoot from native lib directory")
            append("$prootBin")
            append(" -r ${imageFSRoot.absolutePath}")
            append(" -b /dev")
            append(" -b /proc")
            append(" -b /sys")
            append(" -b ${containerDir.absolutePath}:/root/.wine")
            append(" -w /root")
            
            if (box64Bin.exists()) {
                append(" ${box64Bin.absolutePath}")
            }
            
            append(" ${wineBin.absolutePath}")
            append(" ${config.executablePath}")
            
            if (config.arguments.isNotBlank()) {
                append(" ${config.arguments}")
            }
            
            appendLine()
        }
        
        scriptFile.writeText(scriptContent)
        FileUtils.makeExecutable(scriptFile)
        
        Log.d(TAG, "Created PRoot script: ${scriptFile.absolutePath}")
        Log.d(TAG, "Script content:\n$scriptContent")
        
        return scriptFile
    }
    
    private fun createChrootScript(
        imageFSRoot: File,
        containerDir: File,
        box64Bin: File,
        wineBin: File,
        config: ExecutionConfig,
        env: Map<String, String>
    ): File {
        val scriptFile = File(context.cacheDir, "chroot_launch_${System.currentTimeMillis()}.sh")
        
        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine()
            appendLine("# Mount necessary filesystems")
            appendLine("mount -t proc proc ${imageFSRoot.absolutePath}/proc 2>/dev/null || true")
            appendLine("mount -t sysfs sys ${imageFSRoot.absolutePath}/sys 2>/dev/null || true")
            appendLine("mount --bind /dev ${imageFSRoot.absolutePath}/dev 2>/dev/null || true")
            appendLine("mount --bind ${containerDir.absolutePath} ${imageFSRoot.absolutePath}/root/.wine 2>/dev/null || true")
            appendLine()
            
            // Export environment variables (will be available inside chroot)
            env.forEach { (key, value) ->
                appendLine("export $key='$value'")
            }
            appendLine()
            
            // Build command to execute inside chroot
            appendLine("# Execute Wine inside chroot")
            append("chroot ${imageFSRoot.absolutePath} /bin/sh -c '")
            append("cd /root && ")
            
            if (box64Bin.exists()) {
                // Box64 path relative to imagefs root
                val box64RelPath = box64Bin.absolutePath.removePrefix(imageFSRoot.absolutePath)
                append("$box64RelPath ")
            }
            
            // Wine path relative to imagefs root
            val wineRelPath = wineBin.absolutePath.removePrefix(imageFSRoot.absolutePath)
            append("$wineRelPath ")
            append(config.executablePath)
            
            if (config.arguments.isNotBlank()) {
                append(" ${config.arguments}")
            }
            
            appendLine("'")
            appendLine()
            appendLine("# Cleanup mounts")
            appendLine("umount ${imageFSRoot.absolutePath}/dev 2>/dev/null || true")
            appendLine("umount ${imageFSRoot.absolutePath}/proc 2>/dev/null || true")
            appendLine("umount ${imageFSRoot.absolutePath}/sys 2>/dev/null || true")
            appendLine("umount ${imageFSRoot.absolutePath}/root/.wine 2>/dev/null || true")
        }
        
        scriptFile.writeText(scriptContent)
        FileUtils.makeExecutable(scriptFile)
        
        Log.d(TAG, "Created chroot script: ${scriptFile.absolutePath}")
        Log.d(TAG, "Script content:\n$scriptContent")
        
        return scriptFile
    }

    private fun buildEnvironment(container: GlobalContainer): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        env.putAll(container.getEnvVars(context))
        
        val imageFSRoot = File(context.filesDir, "imagefs")
        
        env["PATH"] = buildString {
            append(File(context.filesDir, "box64/bin").absolutePath)
            append(":")
            append(File(context.filesDir, "proton/bin").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/bin").absolutePath)
            append(":")
            append(File(imageFSRoot, "bin").absolutePath)
            append(":")
            append(System.getenv("PATH") ?: "")
        }
        
        env["LD_LIBRARY_PATH"] = buildString {
            append(File(context.filesDir, "proton/lib64").absolutePath)
            append(":")
            append(File(context.filesDir, "proton/lib").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/lib64").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/lib").absolutePath)
        }
        
        env["WINEDLLOVERRIDES"] = buildDllOverrides(container)
        
        val graphicsDriverPath = File(context.filesDir, "graphics_driver")
        if (graphicsDriverPath.exists()) {
            env["LIBGL_DRIVERS_PATH"] = File(graphicsDriverPath, "lib64/dri").absolutePath
            env["VK_ICD_FILENAMES"] = File(graphicsDriverPath, "share/vulkan/icd.d/freedreno_icd.aarch64.json").absolutePath
        }
        
        env["PULSE_SERVER"] = "/tmp/pulse-socket"
        env["PULSE_RUNTIME_PATH"] = File(context.filesDir, "pulseaudio").absolutePath
        
        env["DISPLAY"] = ":0"
        env["WAYLAND_DISPLAY"] = ""
        
        env["HOME"] = File(FileUtils.getContainerDir(context), "drive_c/users/root").absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath
        
        return env
    }

    private fun buildDllOverrides(container: GlobalContainer): String {
        val overrides = mutableListOf<String>()
        
        when (container.dxwrapper) {
            "dxvk-2.3.1", "dxvk-1.10.3" -> {
                overrides.add("d3d11=n")
                overrides.add("d3d10core=n")
                overrides.add("d3d9=n")
                overrides.add("dxgi=n")
            }
            "vkd3d-2.14.1", "vkd3d-2.8" -> {
                overrides.add("d3d12=n")
                overrides.add("d3d11=n")
                overrides.add("dxgi=n")
            }
            "wined3d" -> {
                overrides.add("d3d11=b")
                overrides.add("d3d10core=b")
                overrides.add("d3d9=b")
                overrides.add("dxgi=b")
            }
        }
        
        return overrides.joinToString(";")
    }

    suspend fun executeWineExplorer(): Process? {
        return execute(ExecutionConfig(
            executablePath = "explorer.exe",
            arguments = "/desktop=shell,${getScreenSize()}",
            windowTitle = "Wine Desktop"
        ))
    }

    suspend fun executeWineCfg(): Process? {
        return execute(ExecutionConfig(
            executablePath = "winecfg.exe",
            windowTitle = "Wine Configuration"
        ))
    }

    suspend fun executeCmd(): Process? {
        return execute(ExecutionConfig(
            executablePath = "cmd.exe",
            windowTitle = "Wine Command Prompt"
        ))
    }

    private fun getScreenSize(): String {
        val container = GlobalContainer.load(context)
        return container.screenSize
    }

    private fun getPid(process: Process): String {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.get(process).toString()
        } catch (e: Exception) {
            "unknown"
        }
    }

    suspend fun killWineServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val wineServerBin = File(context.filesDir, "proton/bin/wineserver")
            if (!wineServerBin.exists()) {
                Log.w(TAG, "wineserver binary not found")
                return@withContext false
            }

            val container = GlobalContainer.load(context)
            val env = container.getEnvVars(context)
            
            val processBuilder = ProcessBuilder(
                wineServerBin.absolutePath,
                "-k"
            )
            
            processBuilder.environment().putAll(env)
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            
            Log.i(TAG, "wineserver killed with exit code: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to kill wineserver", e)
            false
        }
    }
    
    private fun getWineLogFile(): File {
        val logsDir = FileUtils.getLogsDir(context)
        FileUtils.ensureDirectoryExists(logsDir)
        
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
            .format(java.util.Date())
        
        return File(logsDir, "wine_$timestamp.log")
    }
    
    private fun monitorProcessOutput(process: Process) {
        Thread {
            try {
                process.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence().forEach { line ->
                        Log.d(TAG, "Wine: $line")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring process output", e)
            }
        }.start()
    }
    
    suspend fun getRecentLogs(limit: Int = 100): List<String> = withContext(Dispatchers.IO) {
        try {
            val logsDir = FileUtils.getLogsDir(context)
            val logFiles = logsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            
            if (logFiles.isEmpty()) {
                return@withContext listOf("No logs available")
            }
            
            val latestLog = logFiles.first()
            latestLog.readLines().takeLast(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read logs", e)
            listOf("Error reading logs: ${e.message}")
        }
    }
    
    suspend fun clearLogs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val logsDir = FileUtils.getLogsDir(context)
            FileUtils.deleteRecursive(logsDir)
            FileUtils.ensureDirectoryExists(logsDir)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
            false
        }
    }
}
