package com.minhasofertas.app.data

import androidx.compose.runtime.Immutable

enum class PriceTrend {
    ALL_TIME_LOW, // Menor preço já registrado
    PRICE_DROP,   // Queda de preço recente
    PRICE_UP,     // Preço subiu
    STABLE        // Preço estável ou primeira ocorrência
}

@Immutable
data class Offer(
    val id: Long = 0,
    val channel: String,
    val price: Double?,
    val originalPrice: Double? = null,
    val link: String,
    val storeLink: String? = null,
    val message: String,
    val imagePath: String? = null,
    val timestamp: String,
    val coupon: String? = null,
    val discountPercent: Int? = null,
    val product: String? = null
)

@Immutable
data class GroupedOffer(
    val groupKey: String,
    val title: String,
    val lowestPrice: Double?,
    val originalPrice: Double?,
    val discountPercent: Int?,
    val coupon: String?,
    val imagePath: String?,
    val primaryLink: String,
    val message: String,
    val sources: List<OfferSource>,
    val latestTimestamp: String,
    val priceTrend: PriceTrend = PriceTrend.STABLE,
    val historicalMinPrice: Double? = null,
    val previousPrice: Double? = null
)

@Immutable
data class OfferSource(
    val channel: String,
    val price: Double?,
    val link: String,
    val timestamp: String
)
