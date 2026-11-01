package com.minhasofertas.app.data

data class Offer(
    val id: Long = 0,
    val channel: String,
    val price: Double?,
    val link: String,
    val message: String,
    val imagePath: String? = null,
    val timestamp: String,
    val coupon: String? = null,
    val discount: String? = null,
    val product: String? = null
)
