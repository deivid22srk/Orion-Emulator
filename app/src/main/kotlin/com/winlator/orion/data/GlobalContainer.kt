package com.winlator.orion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class GlobalContainer(
    val screenSize: String = "1280x720",
    val graphicsDriver: String = "turnip",
    val dxwrapper: String = "dxvk-2.3.1",
    val audioDriver: String = "pulseaudio",
    val box64Preset: String = "compatibility",
    val winVersion: String = "win10",
    val showFPS: Boolean = false,
    val enableWineDebug: Boolean = false,
    val extraEnvVars: String = "ZINK_DESCRIPTORS=lazy",
    val enableDXVKHud: Boolean = false,
    val enableMangoHud: Boolean = false,
    val cpuAffinity: String = "all",
    val protonVersion: String = "proton-9.0-arm64ec",
    val useChroot: Boolean = false,
    val graphicsDriverConfig: String = "vulkanVersion=1.3;version=;blacklistedExtensions=;maxDeviceMemory=0;adrenotoolsTurnip=1;presentMode=mailbox;syncFrame=0;disablePresentWait=0;resourceType=auto;blit=0",
    val dxwrapperConfig: String = "version=2.3.1,framerate=0,async=0,asyncCache=0,vkd3dVersion=2.12,vkd3dLevel=12_1,ddrawrapper=none,csmt=3,gpuName=NVIDIA GeForce GTX 480,videoMemorySize=2048,strict_shader_math=1,OffscreenRenderingMode=fbo,renderer=gl",
    val wincomponents: String = "direct3d=1,directsound=0,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2010=1",
    val drives: String = "D:/storage/emulated/0/Download",
    val startupSelection: Byte = 1,
    val cpuListWoW64: String = "",
    val desktopTheme: String = "default",
    val fexConfig: String = "version=,tsoMode=Fast,x87Mode=1,multiblock=1",
    val midiSoundFont: String = "",
    val inputType: Int = 0,
    val lc_all: String = "",
    val primaryController: Int = 1,
    val controllerMapping: String = ""
) {
    companion object {
        private const val PREF_KEY = "global_container"

        fun load(context: Context): GlobalContainer {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val json = prefs.getString(PREF_KEY, null) ?: return GlobalContainer()
            
            return try {
                Gson().fromJson(json, GlobalContainer::class.java)
            } catch (e: Exception) {
                GlobalContainer()
            }
        }

        fun save(context: Context, container: GlobalContainer) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val json = Gson().toJson(container)
            prefs.edit().putString(PREF_KEY, json).apply()
        }
    }

    fun getEnvVars(context: Context): Map<String, String> {
        val env = mutableMapOf<String, String>()

        val imageFSRoot = File(context.filesDir, "imagefs")
        val winePrefixPath = File(imageFSRoot, "home/xuser/.wine").absolutePath
        
        env["WINEPREFIX"] = winePrefixPath
        env["WINEDEBUG"] = if (enableWineDebug) "+all" else "-all"
        env["WINEARCH"] = "win64"
        env["WINESERVER"] = File(context.filesDir, "proton/bin/wineserver").absolutePath
        env["WINE"] = File(context.filesDir, "proton/bin/wine").absolutePath

        env["BOX64_LOG"] = if (enableWineDebug) "1" else "0"
        env["BOX64_NOBANNER"] = if (enableWineDebug) "0" else "1"
        env["BOX64_SHOWSEGV"] = "0"
        env["BOX64_DYNAREC"] = "1"
        env["BOX64_DYNAREC_STRONGMEM"] = when (box64Preset) {
            "performance" -> "0"
            "compatibility" -> "2"
            else -> "1"
        }
        env["BOX64_DYNAREC_BIGBLOCK"] = when (box64Preset) {
            "performance" -> "3"
            "compatibility" -> "0"
            else -> "1"
        }

        env["mesa_glthread"] = "true"
        env["MESA_GL_VERSION_OVERRIDE"] = "4.6"
        env["MESA_GLSL_VERSION_OVERRIDE"] = "460"
        env["MESA_SHADER_CACHE_DISABLE"] = "false"
        env["MESA_SHADER_CACHE_MAX_SIZE"] = "512MB"

        if (enableDXVKHud) {
            env["DXVK_HUD"] = "fps,devinfo,memory"
        }

        if (lc_all.isNotBlank()) {
            env["LC_ALL"] = lc_all
        }

        if (extraEnvVars.isNotBlank()) {
            extraEnvVars.split("\n").forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    env[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        if (cpuAffinity.isNotBlank()) {
            env["BOX64_CPUAFFINITY"] = cpuAffinity
        }

        if (cpuListWoW64.isNotBlank()) {
            env["BOX86_CPUAFFINITY"] = cpuListWoW64
        }

        if (fexConfig.isNotBlank()) {
            val fexConfigFile = File(context.filesDir, ".fex-config")
            fexConfigFile.writeText(fexConfig)
            env["FEX_CONFIG"] = fexConfigFile.absolutePath
        }

        if (dxwrapperConfig.isNotBlank()) {
            env["DXVK_CONFIG"] = dxwrapperConfig
        }

        if (audioDriver == "alsa") {
            env["PULSE_SERVER"] = ""
        }

        return env
    }
}

data class AppShortcut(
    val id: String,
    val name: String,
    val path: String,
    val icon: String? = null,
    val workingDir: String? = null,
    val arguments: String = "",
    val desktopTheme: String = "default"
) {
    companion object {
        private const val PREF_KEY = "app_shortcuts"

        fun loadAll(context: Context): List<AppShortcut> {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val json = prefs.getString(PREF_KEY, null) ?: return emptyList()
            
            return try {
                val type = object : TypeToken<List<AppShortcut>>() {}.type
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun saveAll(context: Context, shortcuts: List<AppShortcut>) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val json = Gson().toJson(shortcuts)
            prefs.edit().putString(PREF_KEY, json).apply()
        }

        fun add(context: Context, shortcut: AppShortcut) {
            val shortcuts = loadAll(context).toMutableList()
            shortcuts.add(shortcut)
            saveAll(context, shortcuts)
        }

        fun remove(context: Context, id: String) {
            val shortcuts = loadAll(context).filter { it.id != id }
            saveAll(context, shortcuts)
        }

        fun update(context: Context, shortcut: AppShortcut) {
            val shortcuts = loadAll(context).toMutableList()
            val index = shortcuts.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) {
                shortcuts[index] = shortcut
                saveAll(context, shortcuts)
            }
        }
    }
}
