package com.minhasofertas.app.data

import android.content.Context
import com.minhasofertas.app.data.local.AppDatabase
import com.minhasofertas.app.data.local.OfferEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object OfferRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())
    private var database: AppDatabase? = null

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers = _offers.asStateFlow()

    private val _monitoringActive = MutableStateFlow(false)
    val monitoringActive = _monitoringActive.asStateFlow()

    fun init(context: Context) {
        if (database != null) return
        val db = AppDatabase.getInstance(context)
        database = db
        repositoryScope.launch {
            db.offerDao().getAllOffersFlow().collect { entities ->
                _offers.value = entities.map { it.toOffer() }
            }
        }
    }

    fun addOffer(offer: Offer) {
        val current = _offers.value
        // Evita duplicata exata no mesmo canal com a mesma mensagem ou mesmo link de loja
        val exists = current.any {
            it.channel == offer.channel && (it.message == offer.message || (it.storeLink != null && it.storeLink == offer.storeLink))
        }
        if (!exists) {
            repositoryScope.launch {
                database?.offerDao()?.insertOffer(OfferEntity.fromOffer(offer))
            }
        }
    }

    fun updateOfferImage(offerId: Long, imagePath: String) {
        repositoryScope.launch {
            database?.offerDao()?.updateImagePath(offerId, imagePath)
        }
    }

    fun setMonitoringStatus(active: Boolean) {
        _monitoringActive.value = active
    }

    fun clearOffers() {
        repositoryScope.launch {
            database?.offerDao()?.clearAll()
        }
    }

    /**
     * Agrupa ofertas de produtos idênticos vindas de fontes/canais diferentes,
     * calculando o menor preço, descontos e listando todos os canais de origem.
     */
    fun groupOffers(rawOffers: List<Offer>): List<GroupedOffer> {
        val groups = linkedMapOf<String, MutableList<Offer>>()

        for (offer in rawOffers) {
            val key = when {
                !offer.storeLink.isNullOrBlank() -> {
                    // Simplifica URLs para chave comum (ex: sem parâmetros de rastreamento)
                    offer.storeLink.substringBefore("?").lowercase()
                }
                !offer.product.isNullOrBlank() -> {
                    PriceParser.normalizeTitleForGrouping(offer.product)
                }
                else -> {
                    val title = PriceParser.extractProductTitle(offer.message)
                    PriceParser.normalizeTitleForGrouping(title)
                }
            }

            val finalKey = if (key.length >= 4) key else "single_${offer.id}"
            groups.getOrPut(finalKey) { mutableListOf() }.add(offer)
        }

        return groups.map { (key, groupList) ->
            val firstOffer = groupList.first()
            val title = firstOffer.product ?: PriceParser.extractProductTitle(firstOffer.message)

            // Menor preço entre todas as fontes do grupo
            val validPrices = groupList.mapNotNull { it.price }
            val lowestPrice = validPrices.minOrNull()

            // Maior preço de referência (De R$ ...)
            val validOriginalPrices = groupList.mapNotNull { it.originalPrice }
            val highestOriginalPrice = validOriginalPrices.maxOrNull()

            val discountPercent = if (lowestPrice != null && highestOriginalPrice != null && highestOriginalPrice > lowestPrice) {
                (((highestOriginalPrice - lowestPrice) / highestOriginalPrice) * 100).toInt()
            } else {
                groupList.mapNotNull { it.discountPercent }.maxOrNull()
            }

            // Pega o cupom da oferta de menor preço ou o primeiro disponível
            val coupon = groupList.firstOrNull { it.price == lowestPrice && !it.coupon.isNullOrBlank() }?.coupon
                ?: groupList.firstOrNull { !it.coupon.isNullOrBlank() }?.coupon

            // Imagem disponível em qualquer uma das mensagens do grupo
            val imagePath = groupList.firstOrNull { !it.imagePath.isNullOrBlank() }?.imagePath

            // Link principal: do canal com menor preço ou loja direta
            val bestOffer = groupList.firstOrNull { it.price == lowestPrice } ?: firstOffer
            val primaryLink = bestOffer.storeLink ?: bestOffer.link

            val sources = groupList.map { item ->
                OfferSource(
                    channel = item.channel,
                    price = item.price,
                    link = item.storeLink ?: item.link,
                    timestamp = item.timestamp
                )
            }.distinctBy { it.channel }

            GroupedOffer(
                groupKey = key,
                title = title,
                lowestPrice = lowestPrice,
                originalPrice = highestOriginalPrice,
                discountPercent = discountPercent,
                coupon = coupon,
                imagePath = imagePath,
                primaryLink = primaryLink,
                message = bestOffer.message,
                sources = sources,
                latestTimestamp = firstOffer.timestamp
            )
        }
    }
}
