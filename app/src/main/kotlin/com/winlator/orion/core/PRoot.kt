package com.winlator.orion.core

import android.content.Context
import android.util.Log
import java.io.File

object PRoot {
    private const val TAG = "PRoot"
    
    init {
        System.loadLibrary("winlator")
    }
    
    /**
     * Execute PRoot via JNI to bypass Android's noexec restrictions
     */
    private external fun execPRoot(
        args: Array<String>,
        envVars: Array<String>?,
        workDir: String?
    ): Int
    
    /**
     * Wait for a process to complete
     */
    private external fun waitForProcess(pid: Int): Int
    
    /**
     * Execute PRoot with given arguments
     * Returns the PID of the spawned process, or -1 on error
     */
    fun execute(
        context: Context,
        rootPath: String,
        bindings: Map<String, String>,
        workingDir: String,
        command: List<String>,
        envVars: Map<String, String> = emptyMap()
    ): Int {
        // Get PRoot binary path from native library directory
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val prootBinary = File(nativeLibDir, "libproot.so")
        
        if (!prootBinary.exists()) {
            Log.e(TAG, "PRoot binary not found at: ${prootBinary.absolutePath}")
            return -1
        }
        
        Log.i(TAG, "Using PRoot from: ${prootBinary.absolutePath}")
        
        // Build PRoot arguments
        val args = mutableListOf<String>()
        args.add(prootBinary.absolutePath)
        args.add("-r")
        args.add(rootPath)
        
        // Add bindings
        bindings.forEach { (host, guest) ->
            args.add("-b")
            if (host == guest) {
                args.add(host)
            } else {
                args.add("$host:$guest")
            }
        }
        
        // Add working directory
        args.add("-w")
        args.add(workingDir)
        
        // Add the actual command to execute
        args.addAll(command)
        
        Log.i(TAG, "PRoot command: ${args.joinToString(" ")}")
        
        // Convert environment variables to array
        val envArray = envVars.map { (k, v) -> "$k=$v" }.toTypedArray()
        
        // Execute via JNI
        val pid = execPRoot(args.toTypedArray(), envArray, null)
        
        if (pid > 0) {
            Log.i(TAG, "PRoot started with PID: $pid")
        } else {
            Log.e(TAG, "Failed to start PRoot")
        }
        
        return pid
    }
    
    /**
     * Execute PRoot and wait for completion
     */
    fun executeAndWait(
        context: Context,
        rootPath: String,
        bindings: Map<String, String>,
        workingDir: String,
        command: List<String>,
        envVars: Map<String, String> = emptyMap()
    ): Int {
        val pid = execute(context, rootPath, bindings, workingDir, command, envVars)
        
        if (pid <= 0) {
            return -1
        }
        
        return waitForProcess(pid)
    }
}
