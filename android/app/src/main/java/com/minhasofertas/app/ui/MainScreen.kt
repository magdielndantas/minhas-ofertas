package com.minhasofertas.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minhasofertas.app.data.GroupedOffer
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.data.OfferSource
import com.minhasofertas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class OfferFilter(val label: String) {
    ALL("Todas"),
    WITH_COUPON("Com Cupom 🎟"),
    BIG_DISCOUNTS("Maiores Descontos 🔥")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    offers: List<Offer>,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(OfferFilter.ALL) }

    // Agrupa ofertas idênticas de múltiplos canais
    val groupedOffers = remember(offers) {
        OfferRepository.groupOffers(offers)
    }

    val filteredOffers = remember(groupedOffers, searchQuery, selectedFilter) {
        groupedOffers.filter { offer ->
            // Filtro por texto de busca
            val matchesSearch = if (searchQuery.isBlank()) true else {
                offer.title.contains(searchQuery, ignoreCase = true) ||
                offer.message.contains(searchQuery, ignoreCase = true) ||
                (offer.coupon?.contains(searchQuery, ignoreCase = true) == true) ||
                offer.sources.any { it.channel.contains(searchQuery, ignoreCase = true) }
            }

            // Filtro por categoria rápida
            val matchesCategory = when (selectedFilter) {
                OfferFilter.ALL -> true
                OfferFilter.WITH_COUPON -> !offer.coupon.isNullOrBlank()
                OfferFilter.BIG_DISCOUNTS -> (offer.discountPercent ?: 0) >= 15
            }

            matchesSearch && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Minhas Ofertas",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = if (isMonitoring) "Ao vivo • 14 canais integrados" else "Monitoramento pausado",
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
                            .background(if (isMonitoring) Primary.copy(alpha = 0.12f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Status do Monitor",
                            tint = if (isMonitoring) Primary else Color.Gray,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
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
            // Barra de Busca Minimalista com botão de limpar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar periférico, hardware, loja...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca", tint = OnSurfaceVariant)
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OutlineVariant.copy(alpha = 0.5f),
                    focusedContainerColor = SurfaceContainerLowest,
                    unfocusedContainerColor = SurfaceContainerLowest
                ),
                singleLine = true
            )

            // Chips de Filtro Rápido
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OfferFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = OnPrimary,
                            containerColor = SurfaceContainerLowest,
                            labelColor = OnSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) Primary else OutlineVariant.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            // Header com Contador e Resumo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feed de Ofertas",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = OnSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${filteredOffers.size} produtos únicos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant
                )
            }

            // Lista de Ofertas Agrupadas
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
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isMonitoring) "Aguardando novas ofertas dos canais..." else "Monitoramento pausado.",
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
                    items(filteredOffers, key = { it.groupKey }) { offer ->
                        ModernGroupedOfferCard(offer = offer)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernGroupedOfferCard(offer: GroupedOffer) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCouponCopied by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Imagem do Produto com Badge de Desconto e Múltiplas Fontes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(SurfaceContainerLow)
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
                    // Fallback visual moderno com ícone temático
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = Primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Imagem disponível no canal",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }

                // Sombra suave inferior na imagem para legibilidade
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )

                // Badges Superiores (Multi-fontes e Desconto)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (offer.sources.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E293B).copy(alpha = 0.9f),
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${offer.sources.size} FONTES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Badge do canal único
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF1E293B).copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = offer.sources.firstOrNull()?.channel?.uppercase() ?: "CANAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Badge de Porcentagem de Desconto
                    if (offer.discountPercent != null && offer.discountPercent > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFD32F2F)
                        ) {
                            Text(
                                text = "-${offer.discountPercent}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Conteúdo do Card
            Column(modifier = Modifier.padding(18.dp)) {

                // Título do Produto
                Text(
                    text = offer.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = OnSurface,
                    maxLines = 2,
                    lineHeight = 22.sp,
                    overflow = TextOverflow.Ellipsis
                )

                // Fontes Comparadas (se houver mais de uma fonte para o mesmo item)
                if (offer.sources.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Comparar canais:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        offer.sources.forEach { source ->
                            val isLowest = source.price != null && source.price == offer.lowestPrice
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isLowest) PrimaryContainer.copy(alpha = 0.35f) else SurfaceContainerLow,
                                modifier = Modifier.clickable {
                                    if (source.link.startsWith("http")) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.link)))
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = source.channel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OnSurface
                                    )
                                    if (source.price != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "R$ ${"%.2f".format(source.price)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLowest) Primary else OnSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Cupom de Desconto com Botão Copiar
                if (!offer.coupon.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryContainer.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalOffer,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "CUPOM DE DESCONTO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceVariant
                                    )
                                    Text(
                                        text = offer.coupon,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp,
                                        color = Primary
                                    )
                                }
                            }

                            // Botão Copiar Cupom
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(offer.coupon))
                                    isCouponCopied = true
                                    coroutineScope.launch {
                                        delay(2000)
                                        isCouponCopied = false
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isCouponCopied) Color(0xFF2E7D32) else Primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = if (isCouponCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isCouponCopied) "Copiado!" else "Copiar",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Área de Preço e Botão de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (offer.lowestPrice != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (offer.sources.size > 1) "★ MENOR PREÇO" else "MELHOR PREÇO",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (offer.originalPrice != null && offer.originalPrice > offer.lowestPrice) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "R$ ${"%.2f".format(offer.originalPrice)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OnSurfaceVariant,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "R$ ${"%.2f".format(offer.lowestPrice)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp,
                                color = OnSurface,
                                letterSpacing = (-0.5).sp
                            )
                        } else {
                            Text(
                                text = "Preço no Canal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    // Botão Ir para a Oferta
                    Button(
                        onClick = {
                            if (offer.primaryLink.startsWith("http")) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(offer.primaryLink)))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
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
