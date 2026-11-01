package com.minhasofertas.app.data

import java.util.regex.Pattern

object PriceParser {
    private val PRICE_REGEX = Pattern.compile(
        """(?:R\$\s*|por\s+R\$\s*|de\s+R\$\s*\d+[.,]\d{2}\s+por\s+R\$\s*)(\d{1,3}(?:\.\d{3})*|\d+)[.,](\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    private val COUPON_REGEX = Pattern.compile(
        """(?:cupom|cupom de desconto|cupom:)\s*[:\s]*([A-Z0-9_\-]{3,20})""",
        Pattern.CASE_INSENSITIVE
    )

    fun extractPrice(text: String): Double? {
        val matcher = PRICE_REGEX.matcher(text)
        val prices = mutableListOf<Double>()
        while (matcher.find()) {
            val intPart = matcher.group(1)?.replace(".", "") ?: ""
            val decPart = matcher.group(2) ?: "00"
            val priceStr = "$intPart.$decPart"
            priceStr.toDoubleOrNull()?.let { prices.add(it) }
        }
        return prices.minOrNull()
    }

    fun extractCoupon(text: String): String? {
        val matcher = COUPON_REGEX.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    fun isOfferMatching(
        text: String,
        keywords: List<String> = AppConfig.DEFAULT_KEYWORDS,
        ignoreTerms: List<String> = AppConfig.DEFAULT_IGNORE_TERMS,
        maxPrice: Double? = null
    ): Boolean {
        val lower = text.lowercase()

        // 1. Ignorar termos indesejados
        if (ignoreTerms.any { lower.contains(it.lowercase()) }) {
            return false
        }

        // 2. Verificar se contém palavras-chave
        if (keywords.none { lower.contains(it.lowercase()) }) {
            return false
        }

        // 3. Validação de teto de preço se definido
        if (maxPrice != null) {
            val price = extractPrice(text)
            if (price != null && price > maxPrice) {
                return false
            }
        }

        return true
    }
}
