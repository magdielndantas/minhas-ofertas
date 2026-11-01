package com.minhasofertas.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val username: String,
    val title: String = "",
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
