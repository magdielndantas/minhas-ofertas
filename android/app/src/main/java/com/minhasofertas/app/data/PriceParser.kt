package com.minhasofertas.app.data

import java.util.regex.Pattern

object PriceParser {

    private val INSTALLMENT_REGEX = Pattern.compile(
        """\d+\s*x\s*(?:de\s*)?R\$\s*[\d.,]+""",
        Pattern.CASE_INSENSITIVE
    )

    private val SECONDARY_AMOUNTS_REGEX = Pattern.compile(
        """(?i)(?:frete|envio|entrega|cashback|de\s+volta|reembolso|b[oó]nus|cupom(?:\s+de)?|desconto(?:\s+de)?)\s*(?:gr[áa]tis|de\s*)?\s*R?\$?\s*(\d{1,3}(?:\.\d{3})*|\d+)[.,](\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    private val DE_POR_REGEX = Pattern.compile(
        """(?:de|de:\s*|de\s+R\$)\s*R?\$?\s*([\d.]+,\d{2}|\d+,\d{2})\s*(?:por|por:\s*|por\s+apenas|pra|por\s+R\$)\s*R?\$?\s*([\d.]+,\d{2}|\d+,\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    private val PRIORITY_PRICE_REGEX = Pattern.compile(
        """(?i)(?:por|por\s+apenas|sai\s+por|apenas|s[oó]|no\s+pix|à\s+vista|total)\s*(?:de\s*)?R?\$?\s*(\d{1,3}(?:\.\d{3})*|\d+)[.,](\d{2})"""
    )

    private val PRICE_REGEX = Pattern.compile(
        """(?:R\$\s*|por\s+R\$\s*)(\d{1,3}(?:\.\d{3})*|\d+)[.,](\d{2})""",
        Pattern.CASE_INSENSITIVE
    )

    private val COUPON_REGEX = Pattern.compile(
        """(?:cupom|use o cupom|cupom de desconto|código|cupom:)\s*[:\s]*([A-Z0-9_\-]{3,20})""",
        Pattern.CASE_INSENSITIVE
    )

    private val URL_REGEX = Pattern.compile(
        """https?://[^\s)\]>]+""",
        Pattern.CASE_INSENSITIVE
    )

    fun extractProductTitle(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return "Oferta Telegram"

        for (line in lines) {
            // Remove emojis promocionais e pontuações do início
            val cleaned = line.replace(Regex("""^[^\w\s\d]+"""), "").trim()
            // Ignora linhas que são só links ou apenas o preço
            if (cleaned.length > 5 && !cleaned.startsWith("http") && !cleaned.startsWith("R$")) {
                return cleaned.take(90)
            }
        }
        return lines.first().take(90)
    }

    fun extractPrices(text: String): Pair<Double?, Double?> {
        // 1. Remove valores de parcelamento
        val noInstallments = INSTALLMENT_REGEX.matcher(text).replaceAll("")

        // 2. Remove valores secundários (frete, cashback, valor de cupom)
        val cleanText = SECONDARY_AMOUNTS_REGEX.matcher(noInstallments).replaceAll("")

        // 3. Tenta capturar padrão "De R$ X por R$ Y"
        val dePorMatcher = DE_POR_REGEX.matcher(cleanText)
        if (dePorMatcher.find()) {
            val p1Str = dePorMatcher.group(1)?.replace(".", "")?.replace(",", ".") ?: ""
            val p2Str = dePorMatcher.group(2)?.replace(".", "")?.replace(",", ".") ?: ""
            val p1 = p1Str.toDoubleOrNull()
            val p2 = p2Str.toDoubleOrNull()
            if (p1 != null && p2 != null) {
                val current = minOf(p1, p2)
                val original = maxOf(p1, p2)
                return current to original
            }
        }

        // 4. Procura preço com indicativo explícito de preço final ("por R$ X", "sai por R$ X", "à vista R$ X")
        val priorityMatcher = PRIORITY_PRICE_REGEX.matcher(cleanText)
        if (priorityMatcher.find()) {
            val intPart = priorityMatcher.group(1)?.replace(".", "") ?: ""
            val decPart = priorityMatcher.group(2) ?: "00"
            val price = "$intPart.$decPart".toDoubleOrNull()
            if (price != null && price > 0) {
                return price to null
            }
        }

        // 5. Captura o menor preço geral encontrado (filtrando valores anômalos espúrios)
        val matcher = PRICE_REGEX.matcher(cleanText)
        val prices = mutableListOf<Double>()
        while (matcher.find()) {
            val intPart = matcher.group(1)?.replace(".", "") ?: ""
            val decPart = matcher.group(2) ?: "00"
            val priceStr = "$intPart.$decPart"
            priceStr.toDoubleOrNull()?.let { prices.add(it) }
        }

        if (prices.isEmpty()) return null to null

        val maxPrice = prices.maxOrNull() ?: 0.0
        // Se houver preços altos (> 30) e preços insignificantes (< 5), descarta os insignificantes (prováveis taxas ou tokens)
        val validPrices = if (maxPrice >= 30.0) {
            prices.filter { it >= 5.0 }
        } else {
            prices
        }

        val current = (if (validPrices.isNotEmpty()) validPrices else prices).minOrNull()
        return current to null
    }

    fun extractPrice(text: String): Double? {
        return extractPrices(text).first
    }

    fun extractCoupon(text: String): String? {
        val matcher = COUPON_REGEX.matcher(text)
        return if (matcher.find()) matcher.group(1)?.uppercase() else null
    }

    fun extractStoreLink(text: String, extraUrls: List<String> = emptyList()): String? {
        val urls = mutableListOf<String>()

        // 1. URLs vindas de entidades de formatação (TextEntityTypeTextUrl)
        for (u in extraUrls) {
            val trimmed = u.trim().trimEnd('.', ',', ')', ']')
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                urls.add(trimmed)
            }
        }

        // 2. URLs presentes no texto corrido
        val matcher = URL_REGEX.matcher(text)
        while (matcher.find()) {
            matcher.group(0)?.let {
                val cleaned = it.trim().trimEnd('.', ',', ')', ']')
                if (!urls.contains(cleaned)) {
                    urls.add(cleaned)
                }
            }
        }

        // Prioriza links de lojas conhecidas ou encurtadores comuns de afiliados
        val storeUrl = urls.firstOrNull { url ->
            val u = url.lowercase()
            u.contains("amzn") || u.contains("amazon") ||
            u.contains("mercadolivre") || u.contains("meli") ||
            u.contains("shopee") || u.contains("magalu") ||
            u.contains("kabum") || u.contains("pichau") ||
            u.contains("terabyte") || u.contains("aliexpress") ||
            u.contains("fastshop") || u.contains("casasbahia") ||
            u.contains("pontofrio") || u.contains("extra.com")
        }
        return storeUrl ?: urls.firstOrNull()
    }

    fun normalizeTitleForGrouping(title: String): String {
        return title.lowercase()
            .replace(Regex("""[^\w\s\d]"""), " ")
            .split(" ")
            .filter { word ->
                word.length > 2 && word !in setOf(
                    "com", "para", "por", "oferta", "promo", "promocao",
                    "gamer", "original", "novo", "menor", "preco", "super"
                )
            }
            .take(5)
            .sorted()
            .joinToString(" ")
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
