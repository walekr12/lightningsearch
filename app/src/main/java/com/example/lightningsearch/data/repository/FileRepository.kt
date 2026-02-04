package com.example.lightningsearch.data.repository

import com.example.lightningsearch.data.db.FileDao
import com.example.lightningsearch.data.db.FileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
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
        // Use LIKE search for better Chinese support
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

        for (rootPath in rootPaths) {
            val rootFile = File(rootPath)
            if (!rootFile.exists() || !rootFile.canRead()) continue

            indexDirectory(rootFile, batch, batchSize) { indexed, current ->
                totalIndexed = indexed
                onProgress(indexed, current)
            }
        }

        // Insert remaining files
        if (batch.isNotEmpty()) {
            fileDao.insertAll(batch)
        }

        totalIndexed
    }

    private suspend fun indexDirectory(
        directory: File,
        batch: MutableList<FileEntity>,
        batchSize: Int,
        onProgress: (indexed: Int, current: String) -> Unit
    ): Int {
        var count = 0
        val files = directory.listFiles() ?: return 0

        for (file in files) {
            try {
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
                count++

                if (batch.size >= batchSize) {
                    fileDao.insertAll(batch.toList())
                    onProgress(count, file.absolutePath)
                    batch.clear()
                }

                if (file.isDirectory && file.canRead()) {
                    count += indexDirectory(file, batch, batchSize, onProgress)
                }
            } catch (e: Exception) {
                // Skip files that can't be accessed
            }
        }

        return count
    }
}
