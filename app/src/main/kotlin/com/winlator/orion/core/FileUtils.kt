package com.winlator.orion.core

import android.content.Context
import android.os.Environment
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object FileUtils {
    fun getOrionDir(context: Context): File {
        val externalStorage = Environment.getExternalStorageDirectory()
        return File(externalStorage, ".orion")
    }

    fun getContainerDir(context: Context): File {
        return File(getOrionDir(context), "container")
    }

    fun getCacheDir(context: Context): File {
        return File(getOrionDir(context), "cache")
    }

    fun getLogsDir(context: Context): File {
        return File(getOrionDir(context), "logs")
    }

    fun ensureDirectoryExists(directory: File): Boolean {
        return if (!directory.exists()) {
            directory.mkdirs()
        } else {
            true
        }
    }

    fun copyFromAssets(context: Context, assetPath: String, destinationFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to copy asset: $assetPath", e)
            false
        }
    }

    fun copyFile(source: File, destination: File): Boolean {
        return try {
            if (!source.exists()) return false
            
            destination.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to copy file: ${source.path}", e)
            false
        }
    }

    fun deleteRecursive(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { deleteRecursive(it) }
            }
            file.delete()
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to delete: ${file.path}", e)
            false
        }
    }

    fun calculateSHA256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to calculate SHA256: ${file.path}", e)
            null
        }
    }

    fun getFileSize(file: File): Long {
        return if (file.isFile) {
            file.length()
        } else if (file.isDirectory) {
            file.listFiles()?.sumOf { getFileSize(it) } ?: 0L
        } else {
            0L
        }
    }

    fun formatFileSize(sizeInBytes: Long): String {
        val kb = sizeInBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> "%.2f GB".format(gb)
            mb >= 1.0 -> "%.2f MB".format(mb)
            kb >= 1.0 -> "%.2f KB".format(kb)
            else -> "$sizeInBytes bytes"
        }
    }

    fun readTextFile(file: File): String? {
        return try {
            file.readText()
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to read text file: ${file.path}", e)
            null
        }
    }

    fun writeTextFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            file.writeText(content)
            true
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to write text file: ${file.path}", e)
            false
        }
    }

    fun chmod(file: File, permissions: String): Boolean {
        return try {
            android.system.Os.chmod(file.absolutePath, Integer.parseInt(permissions, 8))
            true
        } catch (e: Exception) {
            android.util.Log.e("FileUtils", "Failed to chmod: ${file.path}", e)
            try {
                Runtime.getRuntime().exec("chmod $permissions ${file.absolutePath}").waitFor() == 0
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun chmod(file: File, mode: Int): Boolean {
        return try {
            val permissions = mode and 0x1FFF
            android.system.Os.chmod(file.absolutePath, permissions)
            true
        } catch (e: Exception) {
            android.util.Log.w("FileUtils", "Failed to chmod with int mode: ${file.path}, falling back to string method.", e)
            chmod(file, Integer.toOctalString(mode and 0x1FFF))
        }
    }

    fun makeExecutable(file: File): Boolean {
        return chmod(file, "755")
    }
}
