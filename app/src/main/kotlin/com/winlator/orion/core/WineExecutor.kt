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
            Log.i(TAG, "Use Chroot: ${container.useChroot}")

            val imageFSRoot = File(context.filesDir, "imagefs")
            val workDir = File(imageFSRoot, "home/xuser")
            
            // Ensure working directory exists
            if (!workDir.exists()) {
                workDir.mkdirs()
                FileUtils.chmod(workDir, "755")
            }

            val command = buildCommand(config, container)
            
            Log.i(TAG, "Command: ${command.joinToString(" ")}")
            Log.i(TAG, "Working directory: ${workDir.absolutePath}")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(workDir)
            processBuilder.environment().putAll(env)
            
            if (container.enableWineDebug) {
                val logFile = getWineLogFile()
                processBuilder.redirectErrorStream(true)
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                Log.i(TAG, "Wine logs will be saved to: ${logFile.absolutePath}")
            } else {
                processBuilder.redirectErrorStream(true)
                processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(File("/dev/null")))
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

    private fun buildCommand(config: ExecutionConfig, container: GlobalContainer): List<String> {
        val commands = mutableListOf<String>()
        
        val imageFSRoot = File(context.filesDir, "imagefs")
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        
        if (container.useChroot) {
            Log.i(TAG, "Mode: Chroot (requires root)")
            
            // Create a script that will be executed with su
            val scriptFile = createChrootScript(imageFSRoot, config, container)
            
            commands.add("su")
            commands.add("-c")
            commands.add("sh ${scriptFile.absolutePath}")
        } else {
            Log.i(TAG, "Mode: Direct execution (using system libraries)")
            
            // Build command to execute box64+wine directly
            // Box64 path
            val box64 = File(imageFSRoot, "usr/bin/box64")
            
            // Wine path  
            val wineBin = File(context.filesDir, "proton/bin/wine")
            
            if (box64.exists()) {
                commands.add(box64.absolutePath)
            }
            
            commands.add(wineBin.absolutePath)
            commands.add(config.executablePath)
            
            if (config.arguments.isNotBlank()) {
                commands.addAll(config.arguments.split(" ").filter { it.isNotBlank() })
            }
        }
        
        return commands
    }
    
    private fun createChrootScript(
        imageFSRoot: File,
        config: ExecutionConfig,
        container: GlobalContainer
    ): File {
        val scriptFile = File(context.cacheDir, "wine_chroot_${System.currentTimeMillis()}.sh")
        
        val containerDir = FileUtils.getContainerDir(context)
        val wineBin = File(context.filesDir, "proton/bin/wine")
        val box64Bin = File(imageFSRoot, "usr/bin/box64")
        
        val scriptContent = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("set -e")
            appendLine()
            appendLine("# Prepare chroot environment")
            appendLine("ROOTFS='${imageFSRoot.absolutePath}'")
            appendLine()
            appendLine("# Mount filesystems if not already mounted")
            appendLine("mountpoint -q \$ROOTFS/proc || mount -t proc proc \$ROOTFS/proc")
            appendLine("mountpoint -q \$ROOTFS/sys || mount -t sysfs sys \$ROOTFS/sys")
            appendLine("mountpoint -q \$ROOTFS/dev || mount --bind /dev \$ROOTFS/dev")
            appendLine()
            appendLine("# Create wine prefix bind mount")
            appendLine("mkdir -p \$ROOTFS/root/.wine")
            appendLine("mountpoint -q \$ROOTFS/root/.wine || mount --bind ${containerDir.absolutePath} \$ROOTFS/root/.wine")
            appendLine()
            appendLine("# Execute wine inside chroot")
            append("chroot \$ROOTFS /bin/sh -c 'cd /root && ")
            
            if (box64Bin.exists()) {
                append("/usr/bin/box64 ")
            }
            
            // Wine is installed outside imagefs, so we need to bind mount it
            appendLine("mkdir -p \$ROOTFS/opt/wine")
            appendLine("mountpoint -q \$ROOTFS/opt/wine || mount --bind ${wineBin.parentFile.absolutePath} \$ROOTFS/opt/wine")
            
            append("/opt/wine/wine ")
            append(config.executablePath)
            
            if (config.arguments.isNotBlank()) {
                append(" ")
                append(config.arguments)
            }
            
            appendLine("'")
            appendLine()
            appendLine("# Cleanup on exit")
            appendLine("umount \$ROOTFS/opt/wine 2>/dev/null || true")
            appendLine("umount \$ROOTFS/root/.wine 2>/dev/null || true")
            appendLine("umount \$ROOTFS/dev 2>/dev/null || true")
            appendLine("umount \$ROOTFS/proc 2>/dev/null || true")
            appendLine("umount \$ROOTFS/sys 2>/dev/null || true")
        }
        
        scriptFile.writeText(scriptContent)
        FileUtils.makeExecutable(scriptFile)
        
        if (container.enableWineDebug) {
            Log.d(TAG, "Chroot script:\n$scriptContent")
        }
        
        return scriptFile
    }

    private fun buildEnvironment(container: GlobalContainer): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        env.putAll(container.getEnvVars(context))
        
        val imageFSRoot = File(context.filesDir, "imagefs")
        
        env["PATH"] = buildString {
            append(File(context.filesDir, "proton/bin").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/bin").absolutePath)
            append(":")
            append(File(imageFSRoot, "bin").absolutePath)
            append(":")
            append(File(context.filesDir, "box64/bin").absolutePath)
            append(":")
            append(System.getenv("PATH") ?: "/system/bin:/system/xbin")
        }
        
        env["LD_LIBRARY_PATH"] = buildString {
            append(File(context.filesDir, "proton/lib64").absolutePath)
            append(":")
            append(File(context.filesDir, "proton/lib").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/lib64").absolutePath)
            append(":")
            append(File(imageFSRoot, "usr/lib").absolutePath)
            append(":")
            append("/system/lib64")
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
        
        // Like Winlator Ludashi: use imagefs home
        env["HOME"] = File(imageFSRoot, "home/xuser").absolutePath
        env["USER"] = "xuser"
        env["TMPDIR"] = File(imageFSRoot, "usr/tmp").absolutePath
        
        // Ensure tmp directory exists
        val tmpDir = File(imageFSRoot, "usr/tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
            FileUtils.chmod(tmpDir, "777")
        }
        
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
