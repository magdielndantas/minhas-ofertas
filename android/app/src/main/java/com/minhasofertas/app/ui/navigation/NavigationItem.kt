package com.minhasofertas.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val title: String,
    val icon: ImageVector
) {
    OFFERS("Ofertas", Icons.Default.LocalOffer),
    WATCHLIST("Favoritos", Icons.Default.Favorite),
    DEALS("Mínimas", Icons.Default.TrendingDown),
    SETTINGS("Canais", Icons.Default.Tune)
}

data class StoreBadge(
    val name: String,
    val containerColor: Color,
    val contentColor: Color
)

object StoreBadgeResolver {
    fun resolve(url: String?): StoreBadge {
        val lower = url?.lowercase() ?: ""
        return when {
            lower.contains("amazon.com") || lower.contains("amzn.to") -> {
                StoreBadge("Amazon", Color(0xFF131921), Color(0xFFFF9900))
            }
            lower.contains("mercadolivre.com") || lower.contains("mercadolibre.com") || lower.contains("meli.la") -> {
                StoreBadge("Mercado Livre", Color(0xFFFFE600), Color(0xFF2D3277))
            }
            lower.contains("shopee.com") || lower.contains("s.shopee") -> {
                StoreBadge("Shopee", Color(0xFFEE4D2D), Color.White)
            }
            lower.contains("kabum.com") -> {
                StoreBadge("KaBuM!", Color(0xFFFF6500), Color.White)
            }
            lower.contains("magazineluiza.com") || lower.contains("magalu") -> {
                StoreBadge("Magalu", Color(0xFF0086FF), Color.White)
            }
            lower.contains("aliexpress.com") -> {
                StoreBadge("AliExpress", Color(0xFFFF4747), Color.White)
            }
            lower.contains("terabyteshop.com") -> {
                StoreBadge("Terabyte", Color(0xFF1E293B), Color(0xFF38BDF8))
            }
            lower.contains("pichau.com") -> {
                StoreBadge("Pichau", Color(0xFF991B1B), Color.White)
            }
            else -> {
                StoreBadge("Web", Color(0xFF334155), Color.White)
            }
        }
    }
}
