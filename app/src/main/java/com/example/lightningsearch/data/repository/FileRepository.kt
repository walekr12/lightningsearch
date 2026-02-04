package com.example.lightningsearch.data.repository

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
            emptyList()
        }
    }

    suspend fun indexFiles(
        rootPaths: List<String>,
        onProgress: (indexed: Int, current: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try {
            fileDao.deleteAll()
        } catch (e: Exception) {
            // Ignore
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
                if (rootFile.exists() && rootFile.canRead()) {
                    queue.add(rootFile)
                }
            } catch (e: Exception) {
                // Skip invalid paths
            }
        }

        while (queue.isNotEmpty() && coroutineContext.isActive) {
            val currentDir = queue.poll() ?: continue

            try {
                val files = currentDir.listFiles() ?: continue

                for (file in files) {
                    if (!coroutineContext.isActive) break

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
                                // Ignore insert errors
                            }
                            batch.clear()

                            // Throttle progress updates
                            if (totalIndexed - lastProgressUpdate >= 1000) {
                                lastProgressUpdate = totalIndexed
                                onProgress(totalIndexed, currentDir.absolutePath)
                            }
                        }

                        // Add directory to queue for BFS traversal
                        if (isDir && file.canRead()) {
                            queue.add(file)
                        }
                    } catch (e: Exception) {
                        // Skip files that can't be accessed
                    }
                }
            } catch (e: Exception) {
                // Skip directories that can't be listed
            }
        }

        // Insert remaining files
        if (batch.isNotEmpty()) {
            try {
                fileDao.insertAll(batch)
            } catch (e: Exception) {
                // Ignore
            }
        }

        totalIndexed
    }
}
