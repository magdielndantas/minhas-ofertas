package com.minhasofertas.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhasofertas.app.telegram.AuthState
import com.minhasofertas.app.ui.theme.*

@Composable
fun OnboardingScreen(
    authState: AuthState,
    onConfigureApi: (Int, String) -> Unit,
    onSendPhoneNumber: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit
) {
    val context = LocalContext.current

    // 0: Apresentação, 1: Tutorial my.telegram.org, 2: Inserção de API Keys, 3: Telefone, 4: Código OTP, 5: Senha 2FA
    var onboardingStep by remember { mutableStateOf(if (authState is AuthState.NeedApiCredentials) 0 else 3) }
    var apiIdInput by remember { mutableStateOf("") }
    var apiHashInput by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.WaitPhoneNumber -> if (onboardingStep < 3) onboardingStep = 3
            is AuthState.WaitCode -> if (onboardingStep < 4) onboardingStep = 4
            is AuthState.WaitPassword -> if (onboardingStep < 5) onboardingStep = 5
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Minimalista com Indicador de Passos
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Minhas Ofertas",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Primary,
                    letterSpacing = (-0.5).sp
                )
                // Indicador de Passos
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (onboardingStep == index) 22.dp else 8.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (onboardingStep >= index) Primary else OutlineVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }

            // Conteúdo Central Rolável
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(vertical = 16.dp)
            ) {
                AnimatedContent(
                    targetState = onboardingStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OnboardingSteps"
                ) { step ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        when (step) {
                            0 -> {
                                // Apresentação
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Monitoramento\n100% no Aparelho.",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 32.sp,
                                    lineHeight = 38.sp,
                                    color = OnSurface,
                                    letterSpacing = (-1).sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "O app conecta direto no Telegram pelo protocolo oficial, sem nenhum servidor intermediário lendo seus dados.",
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = OnSurfaceVariant
                                )
                            }

                            1 -> {
                                // Tutorial: Como pegar as credenciais
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Como obter suas\nCredenciais da API",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp,
                                    lineHeight = 34.sp,
                                    color = OnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                // Guia Passo a Passo em Cards Minimalistas
                                TutorialStepItem(
                                    number = "1",
                                    title = "Acesse o portal do Telegram",
                                    description = "Abra o navegador no site oficial my.telegram.org e entre com seu número."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                TutorialStepItem(
                                    number = "2",
                                    title = "Acesse API development tools",
                                    description = "Após fazer login com o código que chegar no seu app, clique em 'API development tools'."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                TutorialStepItem(
                                    number = "3",
                                    title = "Crie uma aplicação simples",
                                    description = "Preencha App title (ex: Minhas Ofertas) e Short name (ex: ofertasapp). Pode deixar o restante em branco."
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                TutorialStepItem(
                                    number = "4",
                                    title = "Copie o API ID e API HASH",
                                    description = "Guarde os dois valores exibidos na tela para preenchê-los a seguir."
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org"))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Abrir my.telegram.org", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }

                            2 -> {
                                // Formulário de Entrada das Chaves
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Inserir Credenciais",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 30.sp,
                                    color = OnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cole os dados gerados no portal. Eles ficarão salvos apenas na memória do seu celular.",
                                    fontSize = 14.sp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                OutlinedTextField(
                                    value = apiIdInput,
                                    onValueChange = { apiIdInput = it },
                                    label = { Text("API ID (Apenas números)") },
                                    placeholder = { Text("Ex: 12345678") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = apiHashInput,
                                    onValueChange = { apiHashInput = it },
                                    label = { Text("API HASH (32 caracteres hexadecimais)") },
                                    placeholder = { Text("Ex: 0123456789abcdef0123456789abcdef") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            3 -> {
                                // Telefone
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Seu Número\ndo Telegram",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    color = OnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Formato com código do país e DDD (Exemplo: +5511999999999).",
                                    fontSize = 14.sp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    placeholder = { Text("+55...") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            4 -> {
                                // Código OTP
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Código de\nVerificação",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    color = OnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Digite o código que você acabou de receber no app oficial do Telegram.",
                                    fontSize = 14.sp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedTextField(
                                    value = code,
                                    onValueChange = { code = it },
                                    placeholder = { Text("Código de 5 dígitos") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            5 -> {
                                // Senha 2FA
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryContainer.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Senha em\nDuas Etapas",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 30.sp,
                                    lineHeight = 36.sp,
                                    color = OnSurface,
                                    letterSpacing = (-0.5).sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Sua conta tem verificação em duas etapas. Digite sua senha da nuvem.",
                                    fontSize = 14.sp,
                                    color = OnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    placeholder = { Text("Senha 2FA") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Alerta de Erro
                        if (authState is AuthState.Error) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = authState.message,
                                    color = Color(0xFFC62828),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Barra Inferior de Ação
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Button(
                    onClick = {
                        when (onboardingStep) {
                            0 -> onboardingStep = 1
                            1 -> onboardingStep = 2
                            2 -> {
                                val parsedId = apiIdInput.trim().toIntOrNull()
                                if (parsedId != null && apiHashInput.isNotBlank()) {
                                    onConfigureApi(parsedId, apiHashInput.trim())
                                    onboardingStep = 3
                                }
                            }
                            3 -> {
                                if (phoneNumber.isNotBlank()) {
                                    onSendPhoneNumber(phoneNumber.trim())
                                }
                            }
                            4 -> {
                                if (code.isNotBlank()) {
                                    onSendCode(code.trim())
                                }
                            }
                            5 -> {
                                if (password.isNotBlank()) {
                                    onSendPassword(password)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary
                    )
                ) {
                    Text(
                        text = when (onboardingStep) {
                            0 -> "Começar Agora"
                            1 -> "Entendi, Inserir Credenciais"
                            2 -> "Salvar e Continuar"
                            3 -> "Enviar Código"
                            4 -> "Validar Código"
                            5 -> "Concluir Conexão"
                            else -> "Avançar"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialStepItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLowest)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                color = OnTertiaryContainer
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = OnSurfaceVariant
            )
        }
    }
}
