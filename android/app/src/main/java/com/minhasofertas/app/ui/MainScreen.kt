package com.minhasofertas.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    offers: List<Offer>,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredOffers = remember(offers, searchQuery) {
        if (searchQuery.isBlank()) offers
        else offers.filter {
            it.message.contains(searchQuery, ignoreCase = true) ||
                    it.channel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "DEAL.EDIT",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Primary,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = if (isMonitoring) "Ao vivo • Monitorando 14 canais" else "Pausado",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isMonitoring) Color(0xFF2E7D32) else OnSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onToggleMonitoring,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(if (isMonitoring) Primary.copy(alpha = 0.1f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Status do Monitor",
                            tint = if (isMonitoring) Primary else Color.Gray,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface
                )
            )
        },
        containerColor = Surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Barra de Busca Minimalista
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar periférico, hardware ou marca...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = SurfaceContainerLowest,
                    unfocusedContainerColor = SurfaceContainerLowest
                ),
                singleLine = true
            )

            // Header de Estatísticas Minimalista
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feed de Promoções",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = OnSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${filteredOffers.size} itens capturados",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant
                )
            }

            // Lista de Ofertas
            if (filteredOffers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerLow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isMonitoring) "Aguardando novas postagens..." else "Monitoramento pausado.",
                            color = OnSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredOffers, key = { it.id }) { offer ->
                        ModernOfferCard(offer = offer)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernOfferCard(offer: Offer) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Imagem de Alta Fidelidade (se houver)
            if (!offer.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = offer.imagePath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                // Header: Tag do Canal e Data
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceContainerLow)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = offer.channel.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = Primary
                        )
                    }
                    Text(
                        text = offer.timestamp,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mensagem com quebra suave
                Text(
                    text = offer.message,
                    style = AppTypography.bodyMedium,
                    color = OnSurface,
                    maxLines = 3,
                    lineHeight = 22.sp,
                    overflow = TextOverflow.Ellipsis
                )

                if (!offer.coupon.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "🎟 Cupom: ${offer.coupon}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preço e Botão de Ação Minimalista
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (offer.price != null) {
                        Column {
                            Text(
                                text = "A PARTIR DE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant
                            )
                            Text(
                                text = "R$ ${"%.2f".format(offer.price)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = OnSurface,
                                letterSpacing = (-0.5).sp
                            )
                        }
                    } else {
                        Text(
                            text = "Preço no Canal",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            if (offer.link.startsWith("http")) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(offer.link))
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Ver Oferta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
