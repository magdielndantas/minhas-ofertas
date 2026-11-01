package com.minhasofertas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlistFlow(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE groupKey = :groupKey LIMIT 1")
    suspend fun getWatchlistItem(groupKey: String): WatchlistEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE groupKey = :groupKey)")
    fun isFavoriteFlow(groupKey: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE groupKey = :groupKey")
    suspend fun delete(groupKey: String)

    @Query("UPDATE watchlist SET targetPrice = :targetPrice WHERE groupKey = :groupKey")
    suspend fun updateTargetPrice(groupKey: String, targetPrice: Double?)

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}
