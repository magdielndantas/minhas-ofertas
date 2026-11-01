package com.minhasofertas.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfferDao {

    @Query("SELECT * FROM offers ORDER BY createdAt DESC")
    fun getAllOffersFlow(): Flow<List<OfferEntity>>

    @Query("SELECT * FROM offers")
    suspend fun getAllOffersSync(): List<OfferEntity>

    @Query("UPDATE offers SET price = :price, originalPrice = :originalPrice, discountPercent = :discountPercent WHERE id = :offerId")
    suspend fun updatePrices(offerId: Long, price: Double?, originalPrice: Double?, discountPercent: Int?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: OfferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<OfferEntity>)

    @Query("UPDATE offers SET imagePath = :imagePath WHERE id = :offerId")
    suspend fun updateImagePath(offerId: Long, imagePath: String)

    @Query("DELETE FROM offers")
    suspend fun clearAll()

    @Query("""
        SELECT * FROM offers 
        WHERE product LIKE '%' || :query || '%' 
           OR message LIKE '%' || :query || '%' 
           OR coupon LIKE '%' || :query || '%' 
           OR channel LIKE '%' || :query || '%' 
        ORDER BY createdAt DESC
    """)
    fun searchOffersFlow(query: String): Flow<List<OfferEntity>>
}
