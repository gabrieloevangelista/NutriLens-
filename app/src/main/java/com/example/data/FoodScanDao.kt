package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodScanDao {
    @Query("SELECT * FROM food_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<FoodScan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: FoodScan)

    @Query("DELETE FROM food_scans WHERE id = :id")
    suspend fun deleteScanById(id: Int)

    @Query("DELETE FROM food_scans")
    suspend fun clearAllScans()
}
