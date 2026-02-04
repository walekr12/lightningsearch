package com.example.lightningsearch.data.repository

import com.example.lightningsearch.data.db.FileDao
import com.example.lightningsearch.data.db.FileEntity
import kotlinx.coroutines.Dispatchers
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
    suspend fun getFileCount(): Int = withContext(Dispatchers.IO) {
        try { fileDao.getFileCount() } catch (e: Exception) { 0 }
    }

    suspend fun search(query: String): List<FileEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try { fileDao.searchLike(query.trim().lowercase()) } catch (e: Exception) { emptyList() }
    }

    suspend fun indexFiles(
        rootPaths: List<String>,
        onProgress: (indexed: Int, current: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        try { fileDao.deleteAll() } catch (e: Exception) { }

        var totalIndexed = 0
        val batch = mutableListOf<FileEntity>()
        val batchSize = 500
        var lastProgressUpdate = 0
        val queue = LinkedList<File>()

        rootPaths.forEach { path ->
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) queue.add(f)
            } catch (e: Exception) { }
        }

        while (queue.isNotEmpty() && coroutineContext.isActive) {
            val dir = queue.poll() ?: continue
            try {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (!coroutineContext.isActive) break
                    try {
                        val name = file.name
                        if (name.startsWith(".")) continue  // Only skip hidden files

                        val isDir = file.isDirectory
                        batch.add(FileEntity(
                            path = file.absolutePath,
                            name = name,
                            name_lower = name.lowercase(),
                            extension = if (!isDir) file.extension.lowercase().ifEmpty { null } else null,
                            size = if (!isDir) file.length() else 0L,
                            modified_time = file.lastModified(),
                            is_directory = isDir,
                            parent_path = file.parent
                        ))
                        totalIndexed++

                        if (batch.size >= batchSize) {
                            try { fileDao.insertAll(batch.toList()) } catch (e: Exception) { }
                            batch.clear()
                            if (totalIndexed - lastProgressUpdate >= 1000) {
                                lastProgressUpdate = totalIndexed
                                onProgress(totalIndexed, dir.absolutePath)
                            }
                        }

                        if (isDir && file.canRead()) queue.add(file)
                    } catch (e: Exception) { }
                }
            } catch (e: Exception) { }
        }

        if (batch.isNotEmpty()) {
            try { fileDao.insertAll(batch) } catch (e: Exception) { }
        }

        totalIndexed
    }
}
