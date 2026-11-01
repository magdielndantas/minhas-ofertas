package com.minhasofertas.app.data

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
    val latestTimestamp: String
)

data class OfferSource(
    val channel: String,
    val price: Double?,
    val link: String,
    val timestamp: String
)
