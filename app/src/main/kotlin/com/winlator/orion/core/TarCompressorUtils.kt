package com.winlator.orion.core

import android.util.Log
import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.zip.GZIPInputStream

object TarCompressorUtils {
    private const val TAG = "TarCompressorUtils"

    interface ProgressCallback {
        fun onProgress(current: Long, total: Long, currentFile: String)
        fun onError(error: String)
    }

    fun extract(
        compressedFile: File,
        destinationDir: File,
        callback: ProgressCallback? = null
    ): Boolean {
        return try {
            if (!compressedFile.exists()) {
                callback?.onError("File not found: ${compressedFile.path}")
                return false
            }

            FileUtils.ensureDirectoryExists(destinationDir)

            val totalSize = compressedFile.length()
            var processedSize = 0L

            val inputStream = when {
                compressedFile.name.endsWith(".txz") -> {
                    XZInputStream(FileInputStream(compressedFile))
                }
                compressedFile.name.endsWith(".tar.xz") -> {
                    XZInputStream(FileInputStream(compressedFile))
                }
                compressedFile.name.endsWith(".tzst") -> {
                    ZstdInputStream(FileInputStream(compressedFile))
                }
                compressedFile.name.endsWith(".tar.zst") -> {
                    ZstdInputStream(FileInputStream(compressedFile))
                }
                compressedFile.name.endsWith(".tar.gz") || compressedFile.name.endsWith(".tgz") -> {
                    GZIPInputStream(FileInputStream(compressedFile))
                }
                compressedFile.name.endsWith(".tar") -> {
                    FileInputStream(compressedFile)
                }
                else -> {
                    callback?.onError("Unsupported file format: ${compressedFile.name}")
                    return false
                }
            }

            TarArchiveInputStream(inputStream).use { tarInput ->
                var entry: TarArchiveEntry?
                
                while (tarInput.nextTarEntry.also { entry = it } != null) {
                    val tarEntry = entry ?: continue
                    val outputFile = File(destinationDir, tarEntry.name)

                    callback?.onProgress(processedSize, totalSize, tarEntry.name)

                    if (tarEntry.isDirectory) {
                        FileUtils.ensureDirectoryExists(outputFile)
                    } else if (tarEntry.isFile) {
                        outputFile.parentFile?.let { parent ->
                            FileUtils.ensureDirectoryExists(parent)
                        }

                        FileOutputStream(outputFile).use { output ->
                            tarInput.copyTo(output, bufferSize = 8192)
                        }

                        if (tarEntry.mode and 0x49 != 0) {
                            FileUtils.makeExecutable(outputFile)
                        }
                    } else if (tarEntry.isSymbolicLink) {
                        createSymlink(tarEntry.linkName, outputFile)
                    }

                    processedSize += tarEntry.size
                }
            }

            callback?.onProgress(totalSize, totalSize, "Done")
            Log.i(TAG, "Successfully extracted: ${compressedFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract: ${compressedFile.path}", e)
            callback?.onError("Extraction failed: ${e.message}")
            false
        }
    }

    fun extractMultiple(
        files: List<File>,
        destinationDir: File,
        callback: ProgressCallback? = null
    ): Boolean {
        var totalFiles = files.size
        var currentFileIndex = 0

        files.forEach { file ->
            currentFileIndex++
            Log.i(TAG, "Extracting ($currentFileIndex/$totalFiles): ${file.name}")
            
            val success = extract(file, destinationDir, object : ProgressCallback {
                override fun onProgress(current: Long, total: Long, currentFile: String) {
                    callback?.onProgress(current, total, "[${currentFileIndex}/${totalFiles}] $currentFile")
                }

                override fun onError(error: String) {
                    callback?.onError(error)
                }
            })

            if (!success) {
                return false
            }
        }

        return true
    }

    private fun createSymlink(target: String, link: File) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("ln", "-s", target, link.absolutePath)
            )
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create symlink: $target -> ${link.path}", e)
        }
    }

    fun compress(
        sourceDir: File,
        outputFile: File,
        compression: CompressionType = CompressionType.ZSTD,
        callback: ProgressCallback? = null
    ): Boolean {
        return try {
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                callback?.onError("Source directory not found: ${sourceDir.path}")
                return false
            }

            outputFile.parentFile?.let { parent ->
                FileUtils.ensureDirectoryExists(parent)
            }

            val outputStream = when (compression) {
                CompressionType.ZSTD -> {
                    com.github.luben.zstd.ZstdOutputStream(FileOutputStream(outputFile))
                }
                CompressionType.XZ -> {
                    org.tukaani.xz.XZOutputStream(FileOutputStream(outputFile), 
                        org.tukaani.xz.LZMA2Options())
                }
                CompressionType.GZIP -> {
                    GZIPOutputStream(FileOutputStream(outputFile))
                }
            }

            org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(outputStream).use { tarOutput ->
                addFileToTar(tarOutput, sourceDir, "", callback)
            }

            Log.i(TAG, "Successfully compressed: ${sourceDir.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress: ${sourceDir.path}", e)
            callback?.onError("Compression failed: ${e.message}")
            false
        }
    }

    private fun addFileToTar(
        tarOutput: org.apache.commons.compress.archivers.tar.TarArchiveOutputStream,
        file: File,
        parentPath: String,
        callback: ProgressCallback?
    ) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"
        
        val tarEntry = org.apache.commons.compress.archivers.tar.TarArchiveEntry(file, entryName)
        tarOutput.putArchiveEntry(tarEntry)

        if (file.isFile) {
            FileInputStream(file).use { input ->
                input.copyTo(tarOutput, bufferSize = 8192)
            }
            callback?.onProgress(0, 0, entryName)
        }

        tarOutput.closeArchiveEntry()

        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                addFileToTar(tarOutput, child, entryName, callback)
            }
        }
    }

    enum class CompressionType {
        ZSTD,
        XZ,
        GZIP
    }
}
