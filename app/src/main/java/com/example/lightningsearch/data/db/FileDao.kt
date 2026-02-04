package com.example.lightningsearch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileEntity>)

    @Query("DELETE FROM files")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM files")
    suspend fun getFileCount(): Int

    @Query("SELECT COUNT(*) FROM files")
    fun getFileCountFlow(): Flow<Int>

    @Query("""
        SELECT files.* FROM files
        JOIN files_fts ON files.path = files_fts.rowid
        WHERE files_fts MATCH :query || '*'
        ORDER BY files.is_directory DESC, files.name_lower ASC
        LIMIT :limit
    """)
    suspend fun searchFts(query: String, limit: Int = 500): List<FileEntity>

    @Query("""
        SELECT * FROM files
        WHERE name_lower LIKE '%' || :query || '%'
        ORDER BY is_directory DESC, name_lower ASC
        LIMIT :limit
    """)
    suspend fun searchLike(query: String, limit: Int = 500): List<FileEntity>

    @Query("""
        SELECT * FROM files
        WHERE name_lower GLOB :pattern
        ORDER BY is_directory DESC, name_lower ASC
        LIMIT :limit
    """)
    suspend fun searchGlob(pattern: String, limit: Int = 500): List<FileEntity>
}
