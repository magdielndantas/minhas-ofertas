package com.minhasofertas.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["groupKey", "recordedAt"])]
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupKey: String,
    val price: Double,
    val channel: String,
    val recordedAt: Long = System.currentTimeMillis()
)
