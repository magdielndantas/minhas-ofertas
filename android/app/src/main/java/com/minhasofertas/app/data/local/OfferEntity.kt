package com.minhasofertas.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.minhasofertas.app.data.Offer

@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey
    val id: Long,
    val channel: String,
    val price: Double?,
    val originalPrice: Double?,
    val link: String,
    val storeLink: String?,
    val message: String,
    val imagePath: String?,
    val timestamp: String,
    val coupon: String?,
    val discountPercent: Int?,
    val product: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toOffer(): Offer = Offer(
        id = id,
        channel = channel,
        price = price,
        originalPrice = originalPrice,
        link = link,
        storeLink = storeLink,
        message = message,
        imagePath = imagePath,
        timestamp = timestamp,
        coupon = coupon,
        discountPercent = discountPercent,
        product = product
    )

    companion object {
        fun fromOffer(offer: Offer): OfferEntity = OfferEntity(
            id = offer.id,
            channel = offer.channel,
            price = offer.price,
            originalPrice = offer.originalPrice,
            link = offer.link,
            storeLink = offer.storeLink,
            message = offer.message,
            imagePath = offer.imagePath,
            timestamp = offer.timestamp,
            coupon = offer.coupon,
            discountPercent = offer.discountPercent,
            product = offer.product
        )
    }
}
