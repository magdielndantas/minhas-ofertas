package com.minhasofertas.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object UrlResolver {

    private val resolvedCache = ConcurrentHashMap<String, String>()

    private val ASIN_PATTERN = Pattern.compile("""(?:/dp/|/gp/product/|/d/|/gp/aw/d/)([A-Z0-9]{10})""", Pattern.CASE_INSENSITIVE)
    private val MELI_PATTERN = Pattern.compile("""(?:(MLB-?\d{8,12})|/p/(MLB\d+))""", Pattern.CASE_INSENSITIVE)
    private val SHOPEE_PATTERN = Pattern.compile("""(?:-i\.(\d+)\.(\d+)|/product/(\d+)/(\d+))""", Pattern.CASE_INSENSITIVE)
    private val MAGALU_PATTERN = Pattern.compile("""/[a-z0-9\-]+/p/([a-z0-9]+)""", Pattern.CASE_INSENSITIVE)
    private val KABUM_PATTERN = Pattern.compile("""/produto/(\d+)""", Pattern.CASE_INSENSITIVE)
    private val ALIEXPRESS_PATTERN = Pattern.compile("""/item/(\d+)""", Pattern.CASE_INSENSITIVE)

    private val KNOWN_SHORTENERS = setOf(
        "amzn.to", "meli.la", "shope.ee", "s.shopee.com.br", "magalu.me",
        "maga.lu", "t2m.io", "bit.ly", "tinyurl.com", "t.co", "cutt.ly",
        "promobit.com.br", "pelando.com.br", "boletando.com", "gatry.com"
    )

    fun isShortener(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val host = URL(url).host.lowercase()
            KNOWN_SHORTENERS.any { host.contains(it) } || url.contains("/sec/")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolve redirecionamento HTTP HEAD on-device para obter a URL final
     */
    suspend fun resolveRedirect(url: String): String = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext url
        resolvedCache[url]?.let { return@withContext it }

        var currentUrl = url
        var redirects = 0
        val maxRedirects = 3

        try {
            while (redirects < maxRedirects) {
                val host = try {
                    URL(currentUrl).host.lowercase()
                } catch (e: Exception) {
                    break
                }

                // Só precisa resolver redirecionamento se for um encurtador conhecido ou redirect seguro
                val isShortener = KNOWN_SHORTENERS.any { host.contains(it) } || currentUrl.contains("/sec/")
                if (!isShortener && redirects > 0) {
                    break
                }

                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                }

                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                        redirects++
                    } else {
                        break
                    }
                } else {
                    conn.disconnect()
                    break
                }
            }
        } catch (ignored: Exception) {
            // Em caso de timeout ou falta de rede, usa a URL original
        }

        resolvedCache[url] = currentUrl
        currentUrl
    }

    /**
     * Extrai identificador canônico do produto a partir de URLs de e-commerce
     */
    fun extractCanonicalKey(url: String): String? {
        val lower = url.lowercase()

        // 1. Amazon ASIN
        val asinMatcher = ASIN_PATTERN.matcher(url)
        if (asinMatcher.find()) {
            return "amazon_${asinMatcher.group(1)?.uppercase()}"
        }

        // 2. Mercado Livre MLB
        val meliMatcher = MELI_PATTERN.matcher(url)
        if (meliMatcher.find()) {
            val rawId = (meliMatcher.group(1) ?: meliMatcher.group(2))?.replace("-", "")?.uppercase()
            if (rawId != null) return "meli_$rawId"
        }

        // 3. Shopee shopid + itemid
        val shopeeMatcher = SHOPEE_PATTERN.matcher(url)
        if (shopeeMatcher.find()) {
            val shop = shopeeMatcher.group(1) ?: shopeeMatcher.group(3)
            val item = shopeeMatcher.group(2) ?: shopeeMatcher.group(4)
            if (shop != null && item != null) {
                return "shopee_${shop}_$item"
            }
        }

        // 4. Magalu Código de Produto
        val magaluMatcher = MAGALU_PATTERN.matcher(url)
        if (magaluMatcher.find()) {
            return "magalu_${magaluMatcher.group(1)?.lowercase()}"
        }

        // 5. Kabum Código do Produto
        val kabumMatcher = KABUM_PATTERN.matcher(url)
        if (kabumMatcher.find()) {
            return "kabum_${kabumMatcher.group(1)}"
        }

        // 6. AliExpress Item ID
        val aliMatcher = ALIEXPRESS_PATTERN.matcher(url)
        if (aliMatcher.find()) {
            return "aliexpress_${aliMatcher.group(1)}"
        }

        return null
    }
}
