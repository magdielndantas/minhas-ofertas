package com.minhasofertas.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhasofertas.app.data.GroupedOffer
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.data.local.PriceHistoryEntity
import com.minhasofertas.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryBottomSheet(
    offer: GroupedOffer,
    onDismiss: () -> Unit
) {
    val historyList by OfferRepository.getPriceHistoryFlow(offer.groupKey).collectAsState(initial = emptyList())
    val watchlistItem = OfferRepository.getWatchlistItem(offer.groupKey)
    var targetPriceInput by remember { mutableStateOf(watchlistItem?.targetPrice?.toString() ?: "") }
    var isSavingTargetPrice by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Histórico de Preços",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = OnSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            Text(
                text = offer.title,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                maxLines = 2,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Resumo dos Preços (Atual, Mínima e Máxima)
            val allPrices = (historyList.map { it.price } + listOfNotNull(offer.lowestPrice)).distinct()
            val minPrice = allPrices.minOrNull()
            val maxPrice = allPrices.maxOrNull()
            val currentPrice = offer.lowestPrice

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PriceMetricCard(
                    title = "PREÇO ATUAL",
                    value = currentPrice?.let { "R$ %.2f".format(it) } ?: "N/D",
                    color = Primary,
                    modifier = Modifier.weight(1f)
                )
                PriceMetricCard(
                    title = "MENOR JÁ VISTO",
                    value = minPrice?.let { "R$ %.2f".format(it) } ?: "N/D",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                PriceMetricCard(
                    title = "MAIOR VALOR",
                    value = maxPrice?.let { "R$ %.2f".format(it) } ?: "N/D",
                    color = Color(0xFFD32F2F),
                    modifier = Modifier.weight(1f)
                )
            }

            // Gráfico Canvas de Linha de Preços (se houver pelo menos 2 pontos)
            if (historyList.size >= 2) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Oscilação no Tempo",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                PriceTrendCanvas(
                    prices = historyList.map { it.price },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(SurfaceContainerLow, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                )
            }

            // Configuração de Preço Alvo (Watchlist)
            Spacer(modifier = Modifier.height(18.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alerta de Preço Alvo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Receba notificação com som e vibração se o preço cair para este valor.",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = targetPriceInput,
                            onValueChange = { targetPriceInput = it },
                            placeholder = { Text("ex: 299.90", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Text("R$", fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.padding(start = 12.dp)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsed = targetPriceInput.toDoubleOrNull()
                                OfferRepository.setTargetPrice(offer.groupKey, parsed)
                                isSavingTargetPrice = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Text(if (isSavingTargetPrice) "Salvo!" else "Salvar")
                        }
                    }
                }
            }

            // Linha do Tempo das Ocorrências
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Registros Anteriores (${historyList.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (historyList.isEmpty()) {
                Text(
                    text = "Primeiro registro deste produto no banco local.",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList.reversed()) { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceContainerLow, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = record.channel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                                Text(
                                    text = dateFormat.format(Date(record.recordedAt)),
                                    fontSize = 10.sp,
                                    color = OnSurfaceVariant
                                )
                            }
                            Text(
                                text = "R$ %.2f".format(record.price),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (record.price == minPrice) Color(0xFF2E7D32) else OnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PriceMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
fun PriceTrendCanvas(
    prices: List<Double>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas

        val max = prices.maxOrNull() ?: 1.0
        val min = prices.minOrNull() ?: 0.0
        val range = if (max > min) max - min else 1.0

        val width = size.width
        val height = size.height
        val stepX = width / (prices.size - 1)

        val path = Path()
        val points = mutableListOf<Offset>()

        prices.forEachIndexed { index, price ->
            val x = index * stepX
            val normalizedY = (price - min) / range
            val y = (height - (normalizedY * (height - 20f)) - 10f).toFloat()
            val point = Offset(x, y)
            points.add(point)
            if (index == 0) {
                path.moveTo(point.x, point.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }

        // Desenha a linha curva/suave
        drawPath(
            path = path,
            color = Color(0xFF00B0FF),
            style = Stroke(width = 3.dp.toPx())
        )

        // Desenha os pontos
        points.forEach { pt ->
            drawCircle(
                color = Color.White,
                radius = 3.5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = Color(0xFF00B0FF),
                radius = 2.dp.toPx(),
                center = pt
            )
        }
    }
}
