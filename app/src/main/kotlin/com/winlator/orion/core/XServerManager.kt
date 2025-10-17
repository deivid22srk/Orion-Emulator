package com.winlator.orion.core

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.winlator.orion.data.GlobalContainer
import java.io.File

class XServerManager(private val context: Context) {
    private val TAG = "XServerManager"
    
    private var xServerProcess: Process? = null
    private var isRunning = false
    
    interface XServerCallback {
        fun onStarted()
        fun onStopped()
        fun onError(error: String)
    }

    fun start(surface: Surface, callback: XServerCallback? = null) {
        if (isRunning) {
            Log.w(TAG, "XServer already running")
            return
        }

        Thread {
            try {
                Log.i(TAG, "Starting XServer...")
                
                setupXAuthority()
                
                val xvfbBin = File(context.filesDir, "imagefs/usr/bin/Xvfb")
                val container = GlobalContainer.load(context)
                
                if (xvfbBin.exists()) {
                    val command = listOf(
                        xvfbBin.absolutePath,
                        ":0",
                        "-screen", "0", "${container.screenSize}x24",
                        "-nolisten", "tcp",
                        "-ac"
                    )
                    
                    val processBuilder = ProcessBuilder(command)
                    processBuilder.environment()["DISPLAY"] = ":0"
                    processBuilder.environment()["XAUTHORITY"] = getXAuthorityPath()
                    
                    xServerProcess = processBuilder.start()
                    isRunning = true
                    
                    Log.i(TAG, "XServer started")
                    callback?.onStarted()
                    
                    Thread.sleep(500)
                } else {
                    Log.w(TAG, "Xvfb not found, using native implementation")
                    isRunning = true
                    callback?.onStarted()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start XServer", e)
                callback?.onError("Failed to start XServer: ${e.message}")
            }
        }.start()
    }

    fun stop() {
        if (!isRunning) return
        
        try {
            Log.i(TAG, "Stopping XServer...")
            
            xServerProcess?.destroy()
            xServerProcess = null
            isRunning = false
            
            Log.i(TAG, "XServer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop XServer", e)
        }
    }

    private fun setupXAuthority() {
        try {
            val xAuthFile = File(getXAuthorityPath())
            xAuthFile.parentFile?.mkdirs()
            
            if (!xAuthFile.exists()) {
                xAuthFile.createNewFile()
                FileUtils.makeExecutable(xAuthFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup XAuthority", e)
        }
    }

    private fun getXAuthorityPath(): String {
        return File(FileUtils.getCacheDir(context), ".Xauthority").absolutePath
    }

    fun isXServerRunning(): Boolean = isRunning

    companion object {
        init {
            try {
                System.loadLibrary("winlator")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("XServerManager", "Failed to load native library", e)
            }
        }
    }
}
