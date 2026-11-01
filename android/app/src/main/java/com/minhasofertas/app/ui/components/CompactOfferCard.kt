package com.minhasofertas.app.ui.components

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minhasofertas.app.data.GroupedOffer
import com.minhasofertas.app.data.PriceTrend
import com.minhasofertas.app.ui.navigation.StoreBadgeResolver
import com.minhasofertas.app.ui.theme.*
import java.io.File

@Composable
fun CompactOfferCard(
    offer: GroupedOffer,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val storeBadge = StoreBadgeResolver.resolve(offer.primaryLink)

    val relativeTime = remember(offer.latestTimestamp) {
        try {
            val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val date = format.parse(offer.latestTimestamp)
            if (date != null) {
                DateUtils.getRelativeTimeSpanString(
                    date.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else offer.latestTimestamp
        } catch (e: Exception) {
            offer.latestTimestamp
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (offer.primaryLink.startsWith("http")) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offer.primaryLink)))
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagem miniatura
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                if (!offer.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(offer.imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = offer.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Detalhes do Produto
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                // Linha superior: Badge da loja + Desconto + Tempo relativo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = storeBadge.containerColor
                    ) {
                        Text(
                            text = storeBadge.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = storeBadge.contentColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (offer.discountPercent != null && offer.discountPercent > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD32F2F)
                        ) {
                            Text(
                                text = "-${offer.discountPercent}%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (relativeTime.isNotEmpty()) {
                        Text(
                            text = relativeTime,
                            fontSize = 10.sp,
                            color = OnSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Título
                Text(
                    text = offer.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = OnSurface,
                    maxLines = 2,
                    lineHeight = 17.sp,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Preço
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (offer.lowestPrice != null) {
                        Text(
                            text = "R$ ${"%.2f".format(offer.lowestPrice)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Primary
                        )
                        if (offer.originalPrice != null && offer.originalPrice > offer.lowestPrice) {
                            Text(
                                text = "R$ ${"%.2f".format(offer.originalPrice)}",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    } else {
                        Text(
                            text = "Ver Preço",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Ações compactas à direita
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (isFavorite) Color(0xFFE91E63) else OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val shareText = buildString {
                            append("${offer.title}\n")
                            if (offer.lowestPrice != null) append("Por R$ ${"%.2f".format(offer.lowestPrice)}\n")
                            if (!offer.coupon.isNullOrBlank()) append("Cupom: ${offer.coupon}\n")
                            append("Link: ${offer.primaryLink}")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartilhar Oferta"))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
