package com.karelisio.status.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Insert
    suspend fun insert(snapshot: SnapshotEntity): Long

    @Query("SELECT * FROM snapshots WHERE timestamp >= :from ORDER BY timestamp ASC")
    fun observeSince(from: Long): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE timestamp >= :from ORDER BY timestamp ASC")
    suspend fun since(from: Long): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): SnapshotEntity?

    @Query("DELETE FROM snapshots WHERE timestamp < :before")
    suspend fun purgeBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM snapshots")
    suspend fun count(): Int
}
