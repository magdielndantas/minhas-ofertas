package com.minhasofertas.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object OfferRepository {
    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    val offers = _offers.asStateFlow()

    private val _monitoringActive = MutableStateFlow(false)
    val monitoringActive = _monitoringActive.asStateFlow()

    fun addOffer(offer: Offer) {
        val current = _offers.value.toMutableList()
        // Evita duplicados pelo link ou mensagem idêntica
        if (current.none { it.link == offer.link && it.message == offer.message }) {
            current.add(0, offer)
            _offers.value = current
        }
    }

    fun setMonitoringStatus(active: Boolean) {
        _monitoringActive.value = active
    }

    fun clearOffers() {
        _offers.value = emptyList()
    }
}
