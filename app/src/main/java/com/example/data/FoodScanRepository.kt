package com.example.data

import kotlinx.coroutines.flow.Flow

class FoodScanRepository(private val foodScanDao: FoodScanDao) {
    val allScans: Flow<List<FoodScan>> = foodScanDao.getAllScans()

    suspend fun insert(scan: FoodScan) {
        foodScanDao.insertScan(scan)
    }

    suspend fun deleteById(id: Int) {
        foodScanDao.deleteScanById(id)
    }

    suspend fun clearAll() {
        foodScanDao.clearAllScans()
    }
}
