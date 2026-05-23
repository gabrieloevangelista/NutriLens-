package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "food_scans")
data class FoodScan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val foodName: String,
    val calories: Int,
    val isHealthy: Boolean,
    val healthExplanation: String,
    val vitaminsPresent: String, // Comma separated list
    val vitaminsMissing: String, // Comma separated list
    val improvements: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageBase64: String? = null // Compressed thumbnail Base64 string for persistent display
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}
