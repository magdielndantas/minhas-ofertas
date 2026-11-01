package com.minhasofertas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PriceSummary(
    val minPrice: Double?,
    val maxPrice: Double?,
    val recordsCount: Int
)

@Dao
interface PriceHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PriceHistoryEntity)

    @Query("SELECT * FROM price_history WHERE groupKey = :groupKey ORDER BY recordedAt ASC")
    fun getHistoryForGroupFlow(groupKey: String): Flow<List<PriceHistoryEntity>>

    @Query("SELECT MIN(price) FROM price_history WHERE groupKey = :groupKey")
    suspend fun getHistoricalMinPrice(groupKey: String): Double?

    @Query("SELECT price FROM price_history WHERE groupKey = :groupKey ORDER BY recordedAt DESC LIMIT 2")
    suspend fun getRecentPrices(groupKey: String): List<Double>

    @Query("SELECT MIN(price) as minPrice, MAX(price) as maxPrice, COUNT(*) as recordsCount FROM price_history WHERE groupKey = :groupKey")
    suspend fun getPriceSummary(groupKey: String): PriceSummary?

    @Query("DELETE FROM price_history")
    suspend fun clearAll()
}
