package com.minhasofertas.app.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.minhasofertas.app.data.ConfigManager
import com.minhasofertas.app.data.GroupedOffer
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.data.PriceTrend
import com.minhasofertas.app.ui.components.CompactOfferCard
import com.minhasofertas.app.ui.components.ShimmerCard
import com.minhasofertas.app.ui.components.SparklineChart
import com.minhasofertas.app.ui.navigation.MainTab
import com.minhasofertas.app.ui.navigation.StoreBadgeResolver
import com.minhasofertas.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class OfferFilter(val label: String) {
    ALL("Todas"),
    WITH_COUPON("Com Cupom 🎟"),
    BIG_DISCOUNTS("Maiores Descontos 🔥"),
    ALL_TIME_LOW("Mínima Histórica 📉")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    offers: List<Offer>,
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
    onSyncTelegramChannels: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var currentTab by remember { mutableStateOf(MainTab.OFFERS) }
    var isCompactView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(OfferFilter.ALL) }
    var selectedOfferForHistory by remember { mutableStateOf<GroupedOffer?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val channels by ConfigManager.channels.collectAsState()
    val activeChannelsCount = channels.count { it.enabled }
    val watchlist by OfferRepository.watchlist.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(ConfigManager.exportConfigJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Configurações exportadas com sucesso!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao exportar arquivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.reader(Charsets.UTF_8).readText()
                }
                if (content != null && ConfigManager.importConfigJson(content)) {
                    onSyncTelegramChannels()
                    Toast.makeText(context, "Configurações importadas com sucesso!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Arquivo JSON inválido", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Falha ao ler arquivo selecionado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Agrupa ofertas idênticas de múltiplos canais
    val groupedOffers = remember(offers) {
        OfferRepository.groupOffers(offers)
    }

    // Filtragem para o Feed Principal
    val filteredOffers = remember(groupedOffers, searchQuery, selectedFilter) {
        groupedOffers.filter { offer ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                offer.title.contains(searchQuery, ignoreCase = true) ||
                offer.message.contains(searchQuery, ignoreCase = true) ||
                (offer.coupon?.contains(searchQuery, ignoreCase = true) == true) ||
                offer.sources.any { it.channel.contains(searchQuery, ignoreCase = true) }
            }

            val matchesCategory = when (selectedFilter) {
                OfferFilter.ALL -> true
                OfferFilter.WITH_COUPON -> !offer.coupon.isNullOrBlank()
                OfferFilter.BIG_DISCOUNTS -> (offer.discountPercent ?: 0) >= 15
                OfferFilter.ALL_TIME_LOW -> offer.priceTrend == PriceTrend.ALL_TIME_LOW || offer.priceTrend == PriceTrend.PRICE_DROP
            }

            matchesSearch && matchesCategory
        }
    }

    // Ofertas da Lista de Desejos
    val watchlistOffers = remember(groupedOffers, watchlist) {
        groupedOffers.filter { watchlist.containsKey(it.groupKey) }
    }

    // Ofertas em Destaque (Mínimas & Quedas)
    val dealsOffers = remember(groupedOffers) {
        groupedOffers.filter {
            it.priceTrend == PriceTrend.ALL_TIME_LOW ||
            it.priceTrend == PriceTrend.PRICE_DROP ||
            (it.discountPercent ?: 0) >= 20
        }.sortedByDescending { it.discountPercent ?: 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (currentTab) {
                                MainTab.OFFERS -> "Minhas Ofertas"
                                MainTab.WATCHLIST -> "Lista de Desejos"
                                MainTab.DEALS -> "Mínimas & Quedas"
                                MainTab.SETTINGS -> "Canais & Configurações"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = when (currentTab) {
                                MainTab.OFFERS -> if (isMonitoring) "Ao vivo • $activeChannelsCount canais ativos" else "Monitoramento pausado"
                                MainTab.WATCHLIST -> "${watchlistOffers.size} produtos favoritados"
                                MainTab.DEALS -> "${dealsOffers.size} oportunidades detectadas"
                                MainTab.SETTINGS -> "$activeChannelsCount canais monitorados"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isMonitoring) Color(0xFF2E7D32) else OnSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Botão para alternar entre Cards Detalhados e Lista Compacta
                    if (currentTab != MainTab.SETTINGS) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCompactView = !isCompactView
                            }
                        ) {
                            Icon(
                                imageVector = if (isCompactView) Icons.Default.ViewAgenda else Icons.Default.ViewHeadline,
                                contentDescription = "Alternar layout de visualização",
                                tint = OnSurfaceVariant
                            )
                        }
                    }

                    // Botão Pausar / Retomar Monitoramento
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMonitoring()
                        },
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
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceContainerLowest,
                tonalElevation = 8.dp
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val badgeCount = when (tab) {
                        MainTab.WATCHLIST -> watchlistOffers.size
                        MainTab.DEALS -> dealsOffers.size
                        else -> 0
                    }

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentTab = tab
                        },
                        icon = {
                            if (badgeCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = if (tab == MainTab.DEALS) Color(0xFFD32F2F) else Primary,
                                            contentColor = Color.White
                                        ) {
                                            Text("$badgeCount")
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            indicatorColor = PrimaryContainer.copy(alpha = 0.5f),
                            unselectedIconColor = OnSurfaceVariant,
                            unselectedTextColor = OnSurfaceVariant
                        )
                    )
                }
            }
        },
        containerColor = Surface
    ) { innerPadding ->

        // Modal de Histórico de Preços
        selectedOfferForHistory?.let { offer ->
            PriceHistoryBottomSheet(
                offer = offer,
                onDismiss = { selectedOfferForHistory = null }
            )
        }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (currentTab) {
                MainTab.OFFERS -> {
                    // Aba 1: Feed Principal
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Barra de Busca
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
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedFilter = filter
                                    },
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

                        // Header com Contador
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

                        // Feed com Pull-to-refresh
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    onSyncTelegramChannels()
                                    delay(1200)
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isRefreshing && filteredOffers.isEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    repeat(3) { ShimmerCard() }
                                }
                            } else if (filteredOffers.isEmpty()) {
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
                                    verticalArrangement = Arrangement.spacedBy(if (isCompactView) 8.dp else 16.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(filteredOffers, key = { it.groupKey }) { offer ->
                                        val isFav = watchlist.containsKey(offer.groupKey)
                                        if (isCompactView) {
                                            CompactOfferCard(
                                                offer = offer,
                                                isFavorite = isFav,
                                                onToggleFavorite = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                                },
                                                onOpenHistory = { selectedOfferForHistory = offer }
                                            )
                                        } else {
                                            ModernGroupedOfferCard(
                                                offer = offer,
                                                isFavorite = isFav,
                                                onToggleFavorite = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                                },
                                                onOpenHistory = { selectedOfferForHistory = offer }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                MainTab.WATCHLIST -> {
                    // Aba 2: Lista de Desejos
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Itens Monitorados",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = OnSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "${watchlistOffers.size} itens",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant
                            )
                        }

                        if (watchlistOffers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE91E63).copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFE91E63),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Sua Lista de Desejos está vazia",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Toque no ícone de coração nas ofertas para monitorar preços e definir alertas de preço alvo.",
                                        color = OnSurfaceVariant,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactView) 8.dp else 16.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(watchlistOffers, key = { it.groupKey }) { offer ->
                                    if (isCompactView) {
                                        CompactOfferCard(
                                            offer = offer,
                                            isFavorite = true,
                                            onToggleFavorite = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                            },
                                            onOpenHistory = { selectedOfferForHistory = offer }
                                        )
                                    } else {
                                        ModernGroupedOfferCard(
                                            offer = offer,
                                            isFavorite = true,
                                            onToggleFavorite = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                            },
                                            onOpenHistory = { selectedOfferForHistory = offer }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MainTab.DEALS -> {
                    // Aba 3: Mínimas Históricas & Quedas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Radar de Quedas",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = OnSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "${dealsOffers.size} em queda",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                        }

                        if (dealsOffers.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Nenhuma queda registrada ainda",
                                        color = OnSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactView) 8.dp else 16.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(dealsOffers, key = { it.groupKey }) { offer ->
                                    val isFav = watchlist.containsKey(offer.groupKey)
                                    if (isCompactView) {
                                        CompactOfferCard(
                                            offer = offer,
                                            isFavorite = isFav,
                                            onToggleFavorite = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                            },
                                            onOpenHistory = { selectedOfferForHistory = offer }
                                        )
                                    } else {
                                        ModernGroupedOfferCard(
                                            offer = offer,
                                            isFavorite = isFav,
                                            onToggleFavorite = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                OfferRepository.toggleFavorite(offer.groupKey, offer.title)
                                            },
                                            onOpenHistory = { selectedOfferForHistory = offer }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                MainTab.SETTINGS -> {
                    // Aba 4: Canais & Configurações embutidas
                    Box(modifier = Modifier.fillMaxSize()) {
                        SettingsContent(
                            onExportJson = { exportLauncher.launch("minhas_ofertas_config.json") },
                            onImportJson = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                            onSyncTelegramChannels = onSyncTelegramChannels,
                            onClose = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernGroupedOfferCard(
    offer: GroupedOffer,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var isCouponCopied by remember { mutableStateOf(false) }

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

    val sourcePrices = remember(offer) { offer.sources.mapNotNull { it.price } }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Imagem do Produto com Badge de Desconto, Loja e Fontes
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

                // Badges Superiores (Loja, Fontes, Desconto, Favorito e Compartilhar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Badge da Loja
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = storeBadge.containerColor,
                            tonalElevation = 4.dp
                        ) {
                            Text(
                                text = storeBadge.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = storeBadge.contentColor,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }

                        if (offer.sources.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF1E293B).copy(alpha = 0.9f),
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = Primary, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${offer.sources.size} FONTES",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF1E293B).copy(alpha = 0.85f)
                            ) {
                                Text(
                                    text = offer.sources.firstOrNull()?.channel?.uppercase() ?: "CANAL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Ações Rápidas (Desconto, Favorito, Compartilhar)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        // Botão Favorito / Lista de Desejos
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E293B).copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleFavorite()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favoritar oferta",
                                    tint = if (isFavorite) Color(0xFFE91E63) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Botão Compartilhar
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E293B).copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(34.dp)
                                .clickable {
                                    val shareText = buildString {
                                        append("${offer.title}\n")
                                        if (offer.lowestPrice != null) append("🔥 Por R$ ${"%.2f".format(offer.lowestPrice)}\n")
                                        if (!offer.coupon.isNullOrBlank()) append("🎟 Cupom: ${offer.coupon}\n")
                                        append("🔗 ${offer.primaryLink}")
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar Oferta"))
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Compartilhar oferta",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Conteúdo do Card
            Column(modifier = Modifier.padding(18.dp)) {

                // Linha de tempo relativo
                if (relativeTime.isNotEmpty()) {
                    Text(
                        text = "Postado $relativeTime",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

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

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

                // Área de Preço, Sparkline e Botão de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        if (offer.lowestPrice != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (offer.priceTrend) {
                                    PriceTrend.ALL_TIME_LOW -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFD32F2F).copy(alpha = 0.16f),
                                            modifier = Modifier.clickable { onOpenHistory() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "🔥 MÍNIMA HISTÓRICA",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFFD32F2F)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = "Histórico de Preços",
                                                    tint = Color(0xFFD32F2F),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                    PriceTrend.PRICE_DROP -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF2E7D32).copy(alpha = 0.16f),
                                            modifier = Modifier.clickable { onOpenHistory() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "📉 QUEDA DE PREÇO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF2E7D32)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = "Histórico de Preços",
                                                    tint = Color(0xFF2E7D32),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Primary.copy(alpha = 0.15f),
                                            modifier = Modifier.clickable { onOpenHistory() }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (offer.sources.size > 1) "★ MENOR PREÇO" else "MELHOR PREÇO",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Primary
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Timeline,
                                                    contentDescription = "Histórico de Preços",
                                                    tint = Primary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Minigráfico Sparkline no próprio card
                                if (sourcePrices.size >= 2) {
                                    SparklineChart(
                                        prices = sourcePrices,
                                        lineColor = if (offer.priceTrend == PriceTrend.PRICE_DROP || offer.priceTrend == PriceTrend.ALL_TIME_LOW) Color(0xFF2E7D32) else Primary,
                                        modifier = Modifier.padding(start = 6.dp)
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
