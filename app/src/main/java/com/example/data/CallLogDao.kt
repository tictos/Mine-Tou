package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentCallLogs(limit: Int): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLogById(id: String)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCallLogs()
}
