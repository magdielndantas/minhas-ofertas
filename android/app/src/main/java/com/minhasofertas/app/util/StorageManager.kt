package com.minhasofertas.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageStats(
    val mediaCacheSizeMb: Double,
    val appCacheSizeMb: Double,
    val databaseSizeMb: Double,
    val totalSizeMb: Double
)

object StorageManager {

    suspend fun getStorageStats(context: Context): StorageStats = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "tdlib_files")
        val cacheDir = context.cacheDir
        val dbFile = context.getDatabasePath("minhas_ofertas.db")

        val mediaBytes = getFolderSize(mediaDir)
        val cacheBytes = getFolderSize(cacheDir)
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L

        val mediaMb = mediaBytes / (1024.0 * 1024.0)
        val cacheMb = cacheBytes / (1024.0 * 1024.0)
        val dbMb = dbBytes / (1024.0 * 1024.0)

        StorageStats(
            mediaCacheSizeMb = String.format("%.2f", mediaMb).replace(",", ".").toDoubleOrNull() ?: mediaMb,
            appCacheSizeMb = String.format("%.2f", cacheMb).replace(",", ".").toDoubleOrNull() ?: cacheMb,
            databaseSizeMb = String.format("%.2f", dbMb).replace(",", ".").toDoubleOrNull() ?: dbMb,
            totalSizeMb = String.format("%.2f", mediaMb + cacheMb + dbMb).replace(",", ".").toDoubleOrNull() ?: (mediaMb + cacheMb + dbMb)
        )
    }

    suspend fun clearMediaCache(context: Context): Long = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "tdlib_files")
        val cacheDir = context.cacheDir

        val beforeBytes = getFolderSize(mediaDir) + getFolderSize(cacheDir)

        deleteContents(mediaDir)
        deleteContents(cacheDir)

        val afterBytes = getFolderSize(mediaDir) + getFolderSize(cacheDir)
        maxOf(0L, beforeBytes - afterBytes)
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { child ->
            size += getFolderSize(child)
        }
        return size
    }

    private fun deleteContents(file: File?) {
        if (file == null || !file.exists()) return
        file.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteContents(child)
                child.delete()
            } else {
                child.delete()
            }
        }
    }
}
