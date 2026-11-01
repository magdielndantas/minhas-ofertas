package com.minhasofertas.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    // 0: Apresentação, 1: Credenciais my.telegram.org, 2: Telefone, 3: Código OTP, 4: Senha 2FA
    var onboardingStep by remember { mutableStateOf(if (authState is AuthState.NeedApiCredentials) 0 else 2) }
    var apiIdInput by remember { mutableStateOf("") }
    var apiHashInput by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.WaitPhoneNumber -> if (onboardingStep < 2) onboardingStep = 2
            is AuthState.WaitCode -> if (onboardingStep < 3) onboardingStep = 3
            is AuthState.WaitPassword -> if (onboardingStep < 4) onboardingStep = 4
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
            // Header Minimalista
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEAL.EDIT",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Primary,
                    letterSpacing = (-1).sp
                )
                // Indicador de Passos
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .width(if (onboardingStep == index) 24.dp else 8.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (onboardingStep >= index) Primary else OutlineVariant.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }

            // Conteúdo Central
            AnimatedContent(
                targetState = onboardingStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "OnboardingContent"
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    when (step) {
                        0 -> {
                            // Passo 0: Apresentação
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Monitoramento\n100% no Aparelho.",
                                fontWeight = FontWeight.Black,
                                fontSize = 34.sp,
                                lineHeight = 40.sp,
                                color = OnSurface,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Zero intermediários. Você se conecta diretamente aos canais do Telegram com suas credenciais seguras.",
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = OnSurfaceVariant
                            )
                        }

                        1 -> {
                            // Passo 1: Inserção de API_ID e API_HASH próprios
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Credenciais\nda API Telegram",
                                fontWeight = FontWeight.Black,
                                fontSize = 32.sp,
                                lineHeight = 38.sp,
                                color = OnSurface,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gere gratuitamente em my.telegram.org > API development tools.",
                                fontSize = 13.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = apiIdInput,
                                onValueChange = { apiIdInput = it },
                                placeholder = { Text("API ID (ex: 1234567)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = apiHashInput,
                                onValueChange = { apiHashInput = it },
                                placeholder = { Text("API HASH (ex: abcdef1234...)") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        2 -> {
                            // Passo 2: Número de Telefone
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Qual o seu\nTelegram?",
                                fontWeight = FontWeight.Black,
                                fontSize = 34.sp,
                                lineHeight = 40.sp,
                                color = OnSurface,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Informe seu número internacional para receber o código oficial de verificação.",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                placeholder = { Text("+55 (DDD) 99999-9999") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        3 -> {
                            // Passo 3: Código OTP
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Código de\nConfirmação",
                                fontWeight = FontWeight.Black,
                                fontSize = 34.sp,
                                lineHeight = 40.sp,
                                color = OnSurface,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Insira o código de 5 dígitos recebido no seu app oficial do Telegram.",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
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

                        4 -> {
                            // Passo 4: Senha 2FA
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Verificação\nem 2 Etapas",
                                fontWeight = FontWeight.Black,
                                fontSize = 34.sp,
                                lineHeight = 40.sp,
                                color = OnSurface,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Digite sua senha na nuvem para autorizar a nova sessão.",
                                fontSize = 14.sp,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                placeholder = { Text("Sua senha 2FA") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Mensagem de Erro
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

            // Botão de Ação
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Button(
                    onClick = {
                        when (onboardingStep) {
                            0 -> onboardingStep = 1
                            1 -> {
                                val parsedId = apiIdInput.trim().toIntOrNull()
                                if (parsedId != null && apiHashInput.isNotBlank()) {
                                    onConfigureApi(parsedId, apiHashInput.trim())
                                    onboardingStep = 2
                                }
                            }
                            2 -> {
                                if (phoneNumber.isNotBlank()) {
                                    onSendPhoneNumber(phoneNumber.trim())
                                }
                            }
                            3 -> {
                                if (code.isNotBlank()) {
                                    onSendCode(code.trim())
                                }
                            }
                            4 -> {
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
                            1 -> "Salvar Credenciais"
                            2 -> "Enviar Código"
                            3 -> "Validar Código"
                            4 -> "Concluir Conexão"
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
