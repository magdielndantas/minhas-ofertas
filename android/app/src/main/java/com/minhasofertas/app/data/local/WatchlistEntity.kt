package com.minhasofertas.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey
    val groupKey: String,
    val title: String,
    val targetPrice: Double? = null,
    val addedAt: Long = System.currentTimeMillis()
)
