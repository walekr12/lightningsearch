package com.example.lightningsearch.data.repository

import com.example.lightningsearch.data.db.FileDao
import com.example.lightningsearch.data.db.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

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
        fileDao.searchLike(normalizedQuery)
    }

    suspend fun indexFiles(
        rootPaths: List<String>,
        onProgress: (indexed: Int, current: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        fileDao.deleteAll()

        var totalIndexed = 0
        val batch = mutableListOf<FileEntity>()
        val batchSize = 1000

        // Use iterative BFS instead of recursion to avoid StackOverflow
        val queue = LinkedList<File>()

        for (rootPath in rootPaths) {
            val rootFile = File(rootPath)
            if (rootFile.exists() && rootFile.canRead()) {
                queue.add(rootFile)
            }
        }

        while (queue.isNotEmpty()) {
            val currentDir = queue.poll() ?: continue

            try {
                val files = currentDir.listFiles() ?: continue

                for (file in files) {
                    try {
                        // Skip hidden files and system directories
                        if (file.name.startsWith(".")) continue
                        if (file.name == "Android" && file.isDirectory) continue

                        val entity = FileEntity(
                            path = file.absolutePath,
                            name = file.name,
                            name_lower = file.name.lowercase(),
                            extension = if (file.isFile) file.extension.lowercase().ifEmpty { null } else null,
                            size = if (file.isFile) file.length() else 0L,
                            modified_time = file.lastModified(),
                            is_directory = file.isDirectory,
                            parent_path = file.parent
                        )
                        batch.add(entity)
                        totalIndexed++

                        if (batch.size >= batchSize) {
                            fileDao.insertAll(batch.toList())
                            onProgress(totalIndexed, file.absolutePath)
                            batch.clear()
                        }

                        // Add directory to queue for BFS traversal
                        if (file.isDirectory && file.canRead()) {
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
            fileDao.insertAll(batch)
        }

        totalIndexed
    }
}
