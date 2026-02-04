package com.example.lightningsearch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FileEntity::class, FileFtsEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
}
