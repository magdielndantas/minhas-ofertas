package com.minhasofertas.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minhasofertas.app.data.ConfigManager
import com.minhasofertas.app.ui.theme.*
import com.minhasofertas.app.util.StorageManager
import com.minhasofertas.app.util.StorageStats
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onSyncTelegramChannels: () -> Unit
) {
    val context = LocalContext.current
    val channels by ConfigManager.channels.collectAsState()
    val keywords by ConfigManager.keywords.collectAsState()
    val ignoreTerms by ConfigManager.ignoreTerms.collectAsState()
    val maxPriceAlert by ConfigManager.maxPriceAlert.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var newChannelInput by remember { mutableStateOf("") }
    var newKeywordInput by remember { mutableStateOf("") }
    var newIgnoreTermInput by remember { mutableStateOf("") }
    var maxPriceInput by remember { mutableStateOf(maxPriceAlert?.toString() ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceContainerLowest,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Cabeçalho
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Configurações",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = OnSurfaceVariant)
                    }
                }

                // Abas
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceContainerLowest,
                    contentColor = Primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Canais", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Alertas", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Backup & Disco", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Conteúdo da Aba
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Aba 1: Canais Telegram
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Campo Adicionar Canal
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newChannelInput,
                                        onValueChange = { newChannelInput = it },
                                        placeholder = { Text("ex: promobit ou @promobit", fontSize = 13.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        leadingIcon = {
                                            Text("@", fontWeight = FontWeight.Black, color = Primary, modifier = Modifier.padding(start = 12.dp))
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            if (newChannelInput.isNotBlank()) {
                                                ConfigManager.addChannel(newChannelInput)
                                                newChannelInput = ""
                                                onSyncTelegramChannels()
                                                Toast.makeText(context, "Canal adicionado!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Adicionar")
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "${channels.count { it.enabled }} de ${channels.size} canais ativos",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(channels, key = { it.username }) { channel ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "@${channel.username}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = if (channel.enabled) OnSurface else OnSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }

                                                Switch(
                                                    checked = channel.enabled,
                                                    onCheckedChange = { checked ->
                                                        ConfigManager.toggleChannel(channel.username, checked)
                                                        onSyncTelegramChannels()
                                                    }
                                                )

                                                IconButton(
                                                    onClick = {
                                                        ConfigManager.deleteChannel(channel.username)
                                                        onSyncTelegramChannels()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Excluir canal",
                                                        tint = Color(0xFFD32F2F).copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Aba 2: Filtros & Alertas
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Teto Máximo para Alertas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Notificar apenas ofertas com preço menor ou igual a este valor (0 = desativado)",
                                        fontSize = 11.sp,
                                        color = OnSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = maxPriceInput,
                                            onValueChange = { input ->
                                                maxPriceInput = input
                                                val parsed = input.toDoubleOrNull()
                                                ConfigManager.updateMaxPriceAlert(if (parsed != null && parsed > 0) parsed else null)
                                            },
                                            placeholder = { Text("ex: 500.00") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            leadingIcon = { Text("R$", fontWeight = FontWeight.Bold, color = Primary, modifier = Modifier.padding(start = 12.dp)) }
                                        )
                                    }
                                }

                                item {
                                    Text(
                                        text = "Termos Monitorados (Filtro Ativo)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (keywords.isEmpty())
                                            "Monitorando tudo: todas as promoções dos canais são exibidas no feed. Adicione termos para restringir ou clique nas sugestões abaixo."
                                        else
                                            "Apenas ofertas contendo algum dos termos abaixo serão capturadas no feed.",
                                        fontSize = 11.sp,
                                        color = OnSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = newKeywordInput,
                                            onValueChange = { newKeywordInput = it },
                                            placeholder = { Text("Adicionar termo personalizado...", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                if (newKeywordInput.isNotBlank()) {
                                                    val clean = newKeywordInput.trim().lowercase()
                                                    if (!keywords.contains(clean)) {
                                                        ConfigManager.updateKeywords(keywords + clean)
                                                    }
                                                    newKeywordInput = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        }
                                    }

                                    if (keywords.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            keywords.forEach { kw ->
                                                InputChip(
                                                    selected = true,
                                                    onClick = {
                                                        ConfigManager.updateKeywords(keywords.filter { it != kw })
                                                    },
                                                    label = { Text(kw, fontSize = 11.sp) },
                                                    trailingIcon = {
                                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Seção de Sugestões de Termos
                                    val availableSuggestions = ConfigManager.suggestedKeywords.filter { it !in keywords }
                                    if (availableSuggestions.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "💡 Sugestões de termos populares (toque para adicionar):",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Primary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            availableSuggestions.forEach { suggestion ->
                                                SuggestionChip(
                                                    onClick = {
                                                        ConfigManager.updateKeywords(keywords + suggestion)
                                                    },
                                                    label = { Text(suggestion, fontSize = 11.sp) },
                                                    icon = {
                                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "Termos Ignorados (Filtro Antispam)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = newIgnoreTermInput,
                                            onValueChange = { newIgnoreTermInput = it },
                                            placeholder = { Text("Ignorar ofertas com...", fontSize = 12.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = {
                                                if (newIgnoreTermInput.isNotBlank()) {
                                                    val updated = ignoreTerms.toMutableList().apply { add(newIgnoreTermInput.trim().lowercase()) }
                                                    ConfigManager.updateIgnoreTerms(updated)
                                                    newIgnoreTermInput = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ignoreTerms.forEach { term ->
                                            InputChip(
                                                selected = true,
                                                onClick = {
                                                    val updated = ignoreTerms.filter { it != term }
                                                    ConfigManager.updateIgnoreTerms(updated)
                                                },
                                                label = { Text(term, fontSize = 11.sp) },
                                                trailingIcon = {
                                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Aba 3: Backup & Armazenamento em Disco
                            val coroutineScope = rememberCoroutineScope()
                            var storageStats by remember { mutableStateOf<StorageStats?>(null) }
                            var isClearingCache by remember { mutableStateOf(false) }

                            LaunchedEffect(Unit) {
                                storageStats = StorageManager.getStorageStats(context)
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                item {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Backup On-Device de Configurações",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = OnSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Exporte ou importe a lista completa de canais, termos e palavras-chave em formato JSON.",
                                        fontSize = 11.sp,
                                        color = OnSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = onExportJson,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                        ) {
                                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Exportar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = onImportJson,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Importar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                item {
                                    Divider(color = OutlineVariant.copy(alpha = 0.3f))
                                }

                                item {
                                    // Gestão de Armazenamento TDLib e Cache
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = SurfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Storage,
                                                    contentDescription = null,
                                                    tint = Primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Uso de Armazenamento Local",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = OnSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))

                                            val stats = storageStats
                                            if (stats != null) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Imagens TDLib:", fontSize = 12.sp, color = OnSurfaceVariant)
                                                    Text("${stats.mediaCacheSizeMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Cache do Aplicativo:", fontSize = 12.sp, color = OnSurfaceVariant)
                                                    Text("${stats.appCacheSizeMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("Banco de Dados Room:", fontSize = 12.sp, color = OnSurfaceVariant)
                                                    Text("${stats.databaseSizeMb} MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OnSurface)
                                                }
                                            } else {
                                                Text("Calculando espaço em disco...", fontSize = 11.sp, color = OnSurfaceVariant)
                                            }

                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        isClearingCache = true
                                                        val freedBytes = StorageManager.clearMediaCache(context)
                                                        val freedMb = String.format("%.2f", freedBytes / (1024.0 * 1024.0))
                                                        storageStats = StorageManager.getStorageStats(context)
                                                        isClearingCache = false
                                                        Toast.makeText(context, "$freedMb MB liberados!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isClearingCache) "Limpando..." else "Limpar Cache de Imagens",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
