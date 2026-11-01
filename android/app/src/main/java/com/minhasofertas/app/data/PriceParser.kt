package com.minhasofertas.app.data

import java.util.regex.Pattern

object PriceParser {

    // Regex ampliado de parcelamentos para evitar capturar valores de parcelas como preço do produto
    private val INSTALLMENT_REGEX = Pattern.compile(
        """(?i)(?:em\s+at[ée]\s+|at[ée]\s+)?\d{1,2}\s*x\s*(?:(?:sem\s+juros|com\s+juros|no\s+cart[ãa]o|fixas)\s+)*(?:de\s+)?(?:R\$\s*)?[\d.,]+(?:\s*(?:sem|com)\s+juros)?""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex de valores secundários a serem desconsiderados (frete, cashback, valor de cupom)
    private val SECONDARY_AMOUNTS_REGEX = Pattern.compile(
        """(?i)(?:frete|envio|entrega|cashback|de\s+volta|reembolso|b[oó]nus|cupom(?:\s+de)?|desconto(?:\s+de)?)\s*(?:gr[áa]tis|de\s*)?\s*R?\$?\s*[\d.,]+""",
        Pattern.CASE_INSENSITIVE
    )

    // Padrão De ... Por ...
    private val DE_POR_REGEX = Pattern.compile(
        """(?i)(?:de|de:)\s*R?\$?\s*([\d.,]+)\s*(?:por|por:|por\s+apenas|pra|por\s+R\$)\s*R?\$?\s*([\d.,]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Preço com indicativo explícito de preço final
    private val PRIORITY_PRICE_REGEX = Pattern.compile(
        """(?i)(?:por|por\s+apenas|sai\s+por|apenas|s[oó]|no\s+pix|à\s+vista|a\s+vista|total)\s*(?:de\s*)?R\$\s*([\d.,]+)""",
        Pattern.CASE_INSENSITIVE
    )

    // Preço monetário geral com R$
    private val MONEY_REGEX = Pattern.compile(
        """R\$\s*([\d.,]+)""",
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

    /**
     * Converte representações numéricas no padrão brasileiro (e variantes internacionais) para Double.
     * Suporta inteiros sem centavos (ex: "100", "1.000"), decimais com vírgula ("100,00", "2.499,90")
     * e decimais com ponto ("49.90").
     */
    fun parseAmount(rawStr: String): Double? {
        val s = rawStr.trim()
        if (s.isBlank()) return null

        val normalized = when {
            // Caso 1: tem ponto e vírgula, ex: "1.500,00" ou "2.499,90"
            s.contains(".") && s.contains(",") -> {
                s.replace(".", "").replace(",", ".")
            }
            // Caso 2: tem apenas vírgula, ex: "100,00" ou "49,90"
            s.contains(",") -> {
                s.replace(",", ".")
            }
            // Caso 3: tem apenas ponto, ex: "1.000", "1.500", "10.000" OU "49.90"
            s.contains(".") -> {
                val parts = s.split(".")
                if (parts.size == 2) {
                    // Se tem 3 dígitos após o ponto (ex: "1.000", "2.500"), é milhar brasileiro sem centavos
                    if (parts[1].length == 3) {
                        parts[0] + parts[1]
                    } else {
                        // "49.90" ou "10.5" -> formato decimal
                        s
                    }
                } else {
                    // Mais de um ponto: "1.000.000" -> junta tudo
                    s.replace(".", "")
                }
            }
            // Caso 4: apenas dígitos inteiros: "100", "99", "1500"
            else -> s
        }

        return normalized.toDoubleOrNull()?.takeIf { it > 0 }
    }

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
        // 1. Remove valores de parcelamento para não confundir a parcela com o valor total
        val noInstallments = INSTALLMENT_REGEX.matcher(text).replaceAll(" ")

        // 2. Remove valores secundários (frete, cashback, valor de cupom)
        val cleanText = SECONDARY_AMOUNTS_REGEX.matcher(noInstallments).replaceAll(" ")

        // 3. Tenta capturar padrão explícito "De R$ X por R$ Y"
        val dePorMatcher = DE_POR_REGEX.matcher(cleanText)
        if (dePorMatcher.find()) {
            val v1 = parseAmount(dePorMatcher.group(1) ?: "")
            val v2 = parseAmount(dePorMatcher.group(2) ?: "")
            if (v1 != null && v2 != null) {
                return minOf(v1, v2) to maxOf(v1, v2)
            }
        }

        // 4. Procura preço com indicativo explícito de preço final ("por R$ X", "sai por R$ X", "à vista R$ X")
        val priorityMatcher = PRIORITY_PRICE_REGEX.matcher(cleanText)
        if (priorityMatcher.find()) {
            val price = parseAmount(priorityMatcher.group(1) ?: "")
            if (price != null && price > 0) {
                return price to null
            }
        }

        // 5. Captura todos os preços monetários com R$ presentes
        val matcher = MONEY_REGEX.matcher(cleanText)
        val prices = mutableListOf<Double>()
        while (matcher.find()) {
            val raw = matcher.group(1) ?: ""
            parseAmount(raw)?.let { prices.add(it) }
        }

        if (prices.isEmpty()) return null to null

        // Se houver exatamente 2 preços, assume menor como atual e maior como original
        if (prices.size == 2) {
            return minOf(prices[0], prices[1]) to maxOf(prices[0], prices[1])
        }

        // Se houver preços altos (> 30) e preços espúrios residuais (< 5), descarta os espúrios
        val maxPrice = prices.maxOrNull() ?: 0.0
        val validPrices = if (maxPrice >= 30.0) {
            prices.filter { it >= 5.0 }
        } else {
            prices
        }

        val current = validPrices.firstOrNull() ?: prices.firstOrNull()
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
        keywords: List<String> = emptyList(),
        ignoreTerms: List<String> = AppConfig.DEFAULT_IGNORE_TERMS,
        maxPrice: Double? = null
    ): Boolean {
        val lower = text.lowercase()

        // 1. Ignorar termos indesejados
        if (ignoreTerms.any { lower.contains(it.lowercase()) }) {
            return false
        }

        // 2. Se houver palavras-chave ativas configuradas, filtra por elas.
        // Se a lista estiver vazia, aceita todas as ofertas dos canais por padrão.
        if (keywords.isNotEmpty() && keywords.none { lower.contains(it.lowercase()) }) {
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
