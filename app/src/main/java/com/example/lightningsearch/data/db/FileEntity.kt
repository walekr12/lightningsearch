package com.example.lightningsearch.data.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    indices = [
        Index(value = ["name_lower"]),
        Index(value = ["extension"]),
        Index(value = ["parent_path"])
    ]
)
data class FileEntity(
    @PrimaryKey
    val path: String,
    val name: String,
    val name_lower: String,
    val extension: String?,
    val size: Long,
    val modified_time: Long,
    val is_directory: Boolean,
    val parent_path: String?
)

@Entity(tableName = "files_fts")
@Fts4(contentEntity = FileEntity::class)
data class FileFtsEntity(
    val name: String,
    val name_lower: String
)
