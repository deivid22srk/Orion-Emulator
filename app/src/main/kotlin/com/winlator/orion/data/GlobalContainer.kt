package com.winlator.orion.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class GlobalContainer(
    val screenSize: String = "1280x720",
    val graphicsDriver: String = "turnip",
    val dxwrapper: String = "dxvk-2.3.1",
    val audioDriver: String = "pulseaudio",
    val box64Preset: String = "compatibility",
    val winVersion: String = "win10",
    val showFPS: Boolean = false,
    val enableWineDebug: Boolean = false,
    val extraEnvVars: String = "",
    val enableDXVKHud: Boolean = false,
    val enableMangoHud: Boolean = false,
    val cpuAffinity: String = "all",
    val protonVersion: String = "proton-9.0-arm64ec"
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

    fun getEnvVars(): Map<String, String> {
        val env = mutableMapOf<String, String>()

        env["WINEPREFIX"] = "/data/data/com.winlator.orion/container"
        env["WINEDEBUG"] = if (enableWineDebug) "+all" else "-all"
        env["WINEARCH"] = "win64"
        env["WINESERVER"] = "/data/data/com.winlator.orion/files/proton/bin/wineserver"
        env["WINE"] = "/data/data/com.winlator.orion/files/proton/bin/wine"

        env["BOX64_LOG"] = if (enableWineDebug) "1" else "0"
        env["BOX64_SHOWSEGV"] = "0"
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

        if (enableDXVKHud) {
            env["DXVK_HUD"] = "fps,devinfo,memory"
        }

        if (extraEnvVars.isNotBlank()) {
            extraEnvVars.split("\n").forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    env[parts[0].trim()] = parts[1].trim()
                }
            }
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
