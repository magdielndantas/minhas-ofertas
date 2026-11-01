package com.minhasofertas.app.data

import android.content.Context
import com.minhasofertas.app.data.local.AppDatabase
import com.minhasofertas.app.data.local.OfferEntity
import com.minhasofertas.app.data.local.PriceHistoryEntity
import com.minhasofertas.app.util.UrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object OfferRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + Job())
    private var database: AppDatabase? = null

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers = _offers.asStateFlow()

    private val _monitoringActive = MutableStateFlow(false)
    val monitoringActive = _monitoringActive.asStateFlow()

    // Cache em memória de estatísticas de preços para renderização instantânea
    private val minPriceCache = ConcurrentHashMap<String, Double>()
    private val previousPriceCache = ConcurrentHashMap<String, Double>()

    fun init(context: Context) {
        if (database != null) return
        val db = AppDatabase.getInstance(context)
        database = db
        repositoryScope.launch {
            db.offerDao().getAllOffersFlow().collect { entities ->
                val domainOffers = entities.map { it.toOffer() }
                _offers.value = domainOffers
            }
        }
    }

    fun computeGroupKey(offer: Offer): String {
        // 1. Identificador canônico de e-commerce (ASIN, MLB, Shopee, Magalu, Kabum)
        val canonical = offer.storeLink?.let { UrlResolver.extractCanonicalKey(it) }
        if (canonical != null) return canonical

        // 2. Simplificação de link de loja direta
        if (!offer.storeLink.isNullOrBlank()) {
            val cleanUrl = offer.storeLink.substringBefore("?").lowercase()
            if (cleanUrl.length >= 10) return cleanUrl
        }

        // 3. Normalização textual do título
        if (!offer.product.isNullOrBlank()) {
            val norm = PriceParser.normalizeTitleForGrouping(offer.product)
            if (norm.length >= 4) return norm
        }

        val title = PriceParser.extractProductTitle(offer.message)
        val norm = PriceParser.normalizeTitleForGrouping(title)
        return if (norm.length >= 4) norm else "single_${offer.id}"
    }

    fun addOffer(rawOffer: Offer) {
        val current = _offers.value
        val exists = current.any {
            it.channel == rawOffer.channel && (it.message == rawOffer.message || (it.storeLink != null && it.storeLink == rawOffer.storeLink))
        }
        if (exists) return

        repositoryScope.launch {
            // Desencurta URL de afiliado/encurtador de forma assíncrona on-device
            val resolvedStoreLink = rawOffer.storeLink?.let { UrlResolver.resolveRedirect(it) } ?: rawOffer.storeLink
            val offer = if (resolvedStoreLink != rawOffer.storeLink) {
                rawOffer.copy(storeLink = resolvedStoreLink)
            } else {
                rawOffer
            }

            // Salva a oferta no Room
            database?.offerDao()?.insertOffer(OfferEntity.fromOffer(offer))

            // Registra no histórico de preços se houver preço
            val price = offer.price
            if (price != null && price > 0) {
                val groupKey = computeGroupKey(offer)
                val db = database
                if (db != null) {
                    val prevMin = db.priceHistoryDao().getHistoricalMinPrice(groupKey)
                    val recents = db.priceHistoryDao().getRecentPrices(groupKey)
                    if (recents.isNotEmpty()) {
                        previousPriceCache[groupKey] = recents.first()
                    }
                    if (prevMin != null) {
                        minPriceCache[groupKey] = prevMin
                    }
                    db.priceHistoryDao().insert(
                        PriceHistoryEntity(
                            groupKey = groupKey,
                            price = price,
                            channel = offer.channel
                        )
                    )
                }
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
            database?.priceHistoryDao()?.clearAll()
            minPriceCache.clear()
            previousPriceCache.clear()
        }
    }

    /**
     * Agrupa ofertas de produtos idênticos vindas de fontes/canais diferentes,
     * calculando o menor preço, descontos, fontes e indicador de tendência.
     */
    fun groupOffers(rawOffers: List<Offer>): List<GroupedOffer> {
        val groups = linkedMapOf<String, MutableList<Offer>>()

        for (offer in rawOffers) {
            val key = computeGroupKey(offer)
            groups.getOrPut(key) { mutableListOf() }.add(offer)
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

            // Cálculo da tendência de preço (Mínima histórica, Queda ou Estável)
            val historicalMin = minPriceCache[key]
            val previousPrice = previousPriceCache[key]
            val trend = when {
                lowestPrice != null && historicalMin != null && lowestPrice < historicalMin -> PriceTrend.ALL_TIME_LOW
                lowestPrice != null && previousPrice != null && lowestPrice < previousPrice -> PriceTrend.PRICE_DROP
                lowestPrice != null && previousPrice != null && lowestPrice > previousPrice -> PriceTrend.PRICE_UP
                discountPercent != null && discountPercent >= 25 -> PriceTrend.ALL_TIME_LOW
                discountPercent != null && discountPercent >= 10 -> PriceTrend.PRICE_DROP
                else -> PriceTrend.STABLE
            }

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
                latestTimestamp = firstOffer.timestamp,
                priceTrend = trend,
                historicalMinPrice = historicalMin,
                previousPrice = previousPrice
            )
        }
    }
}
