package com.minhasofertas.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.minhasofertas.app.telegram.AuthState
import com.minhasofertas.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    offers: List<Offer>,
    isMonitoring: Boolean,
    authState: AuthState,
    onToggleMonitoring: () -> Unit,
    onSendPhoneNumber: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredOffers = remember(offers, searchQuery) {
        if (searchQuery.isBlank()) offers
        else offers.filter {
            it.message.contains(searchQuery, ignoreCase = true) ||
                    it.channel.contains(searchQuery, ignoreCase = true)
        }
    }

    // Modal de Login/Configuração do Telegram
    AuthDialog(
        authState = authState,
        onSendPhoneNumber = onSendPhoneNumber,
        onSendCode = onSendCode,
        onSendPassword = onSendPassword
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DEAL.EDIT",
                            fontWeight = FontWeight.Black,
                            color = Primary,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "| Minhas Ofertas",
                            fontSize = 14.sp,
                            color = OnSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleMonitoring) {
                        Icon(
                            imageVector = if (isMonitoring) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = "Status do Monitor",
                            tint = if (isMonitoring) Primary else Color.Gray,
                            modifier = Modifier.size(32.dp)
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
                .padding(horizontal = 16.dp)
        ) {
            // Status Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMonitoring) SurfaceContainerLow else Color(0xFFECEFF1)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMonitoring) Icons.Default.Sensors else Icons.Default.SensorsOff,
                        contentDescription = null,
                        tint = if (isMonitoring) Primary else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMonitoring) "Monitoramento Ativo" else "Monitoramento Pausado",
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = if (authState == AuthState.Ready) {
                                if (isMonitoring) "Recebendo promoções dos canais configurados" else "Toque no ícone acima para iniciar"
                            } else {
                                "Necessário conectar conta do Telegram"
                            },
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // Barra de Busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filtrar por nome, marca ou canal...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = OnSurfaceVariant) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OutlineVariant,
                    focusedContainerColor = SurfaceContainerLowest,
                    unfocusedContainerColor = SurfaceContainerLowest
                ),
                singleLine = true
            )

            // Contador de resultados
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ofertas Detectadas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OnSurface
                )
                Text(
                    text = "${filteredOffers.size} itens",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Primary
                )
            }

            // Lista de Cards
            if (filteredOffers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isMonitoring) "Aguardando novas mensagens dos canais..." else "Nenhuma oferta capturada ainda.",
                        color = OnSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredOffers, key = { it.id }) { offer ->
                        OfferCard(offer = offer)
                    }
                }
            }
        }
    }
}

@Composable
fun OfferCard(offer: Offer) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Imagem (se disponível)
            if (!offer.imagePath.isNullOrBlank()) {
                AsyncImage(
                    model = offer.imagePath,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Header do Card: Canal e Horário
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📢 ${offer.channel}",
                        style = AppTypography.labelSmall,
                        color = Primary
                    )
                    Text(
                        text = offer.timestamp,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mensagem
                Text(
                    text = offer.message,
                    style = AppTypography.bodyMedium,
                    color = OnSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rodapé: Preço e Botão de Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (offer.price != null) {
                        Text(
                            text = "R$ ${"%.2f".format(offer.price)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = OnSurface
                        )
                    } else {
                        Text(
                            text = "Ver no Canal",
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
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Ver Oferta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
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
