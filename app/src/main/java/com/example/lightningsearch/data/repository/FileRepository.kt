package com.example.lightningsearch.data.repository

import android.util.Log
import com.example.lightningsearch.data.db.FileDao
import com.example.lightningsearch.data.db.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

private const val TAG = "FileRepository"

@Singleton
class FileRepository @Inject constructor(
    private val fileDao: FileDao
) {
    fun getFileCountFlow(): Flow<Int> = fileDao.getFileCountFlow()

    suspend fun getFileCount(): Int = fileDao.getFileCount()

    suspend fun search(query: String): List<FileEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        val normalizedQuery = query.trim().lowercase()
        try {
            fileDao.searchLike(normalizedQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching", e)
            emptyList()
        }
    }

    suspend fun indexFiles(
        rootPaths: List<String>,
        onProgress: (indexed: Int, current: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "indexFiles started with paths: $rootPaths")

        try {
            fileDao.deleteAll()
            Log.d(TAG, "Deleted all existing files")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting files", e)
        }

        var totalIndexed = 0
        val batch = mutableListOf<FileEntity>()
        val batchSize = 500
        var lastProgressUpdate = 0

        // Use iterative BFS instead of recursion to avoid StackOverflow
        val queue = LinkedList<File>()

        for (rootPath in rootPaths) {
            try {
                val rootFile = File(rootPath)
                Log.d(TAG, "Root path: $rootPath, exists: ${rootFile.exists()}, canRead: ${rootFile.canRead()}")
                if (rootFile.exists() && rootFile.canRead()) {
                    queue.add(rootFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding root path: $rootPath", e)
            }
        }

        Log.d(TAG, "Starting BFS traversal, queue size: ${queue.size}")

        while (queue.isNotEmpty() && coroutineContext.isActive) {
            val currentDir = queue.poll() ?: continue

            try {
                val files = currentDir.listFiles()
                if (files == null) {
                    Log.w(TAG, "listFiles returned null for: ${currentDir.absolutePath}")
                    continue
                }

                for (file in files) {
                    if (!coroutineContext.isActive) {
                        Log.d(TAG, "Coroutine cancelled, breaking")
                        break
                    }

                    try {
                        // Skip hidden files and system directories
                        val fileName = file.name
                        if (fileName.startsWith(".")) continue
                        if (fileName == "Android" && file.isDirectory) continue
                        if (fileName == "data" && file.isDirectory && file.parent == "/") continue

                        val isDir = file.isDirectory
                        val entity = FileEntity(
                            path = file.absolutePath,
                            name = fileName,
                            name_lower = fileName.lowercase(),
                            extension = if (!isDir) file.extension.lowercase().ifEmpty { null } else null,
                            size = if (!isDir) file.length() else 0L,
                            modified_time = file.lastModified(),
                            is_directory = isDir,
                            parent_path = file.parent
                        )
                        batch.add(entity)
                        totalIndexed++

                        if (batch.size >= batchSize) {
                            try {
                                fileDao.insertAll(batch.toList())
                            } catch (e: Exception) {
                                Log.e(TAG, "Error inserting batch", e)
                            }
                            batch.clear()

                            // Throttle progress updates
                            if (totalIndexed - lastProgressUpdate >= 1000) {
                                lastProgressUpdate = totalIndexed
                                try {
                                    onProgress(totalIndexed, currentDir.absolutePath)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in onProgress callback", e)
                                }
                            }
                        }

                        // Add directory to queue for BFS traversal
                        if (isDir && file.canRead()) {
                            queue.add(file)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file: ${file.absolutePath}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing directory: ${currentDir.absolutePath}", e)
            }
        }

        // Insert remaining files
        if (batch.isNotEmpty()) {
            try {
                fileDao.insertAll(batch)
                Log.d(TAG, "Inserted final batch of ${batch.size} files")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting final batch", e)
            }
        }

        Log.d(TAG, "indexFiles completed, total: $totalIndexed")
        totalIndexed
    }
}
