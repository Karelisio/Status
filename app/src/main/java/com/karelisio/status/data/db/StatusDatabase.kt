package com.karelisio.status.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SnapshotEntity::class], version = 1, exportSchema = true)
abstract class StatusDatabase : RoomDatabase() {
    abstract fun snapshots(): SnapshotDao

    companion object {
        fun create(context: Context): StatusDatabase =
            Room.databaseBuilder(context, StatusDatabase::class.java, "status.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
