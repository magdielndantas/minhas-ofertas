package com.minhasofertas.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minhasofertas.app.telegram.AuthState
import com.minhasofertas.app.ui.theme.*

@Composable
fun AuthDialog(
    authState: AuthState,
    onSendPhoneNumber: (String) -> Unit,
    onSendCode: (String) -> Unit,
    onSendPassword: (String) -> Unit
) {
    if (authState == AuthState.Ready) return

    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        containerColor = SurfaceContainerLowest,
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = "Conectar Telegram",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Primary
                )
                Text(
                    text = "Autenticação local para monitoramento dos canais",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (authState) {
                    is AuthState.Initial, is AuthState.WaitParameters -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Inicializando motor TDLib...", fontSize = 14.sp, color = OnSurface)
                        }
                    }

                    is AuthState.WaitPhoneNumber -> {
                        Text(
                            text = "Digite seu número com DDI e DDD (ex: +5511999999999):",
                            fontSize = 13.sp,
                            color = OnSurface
                        )
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Primary) },
                            placeholder = { Text("+55...") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { if (phoneNumber.isNotBlank()) onSendPhoneNumber(phoneNumber.trim()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Avançar", fontWeight = FontWeight.Bold)
                        }
                    }

                    is AuthState.WaitCode -> {
                        Text(
                            text = "Digite o código de verificação recebido no app oficial do Telegram:",
                            fontSize = 13.sp,
                            color = OnSurface
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = Primary) },
                            placeholder = { Text("Código de 5 dígitos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { if (code.isNotBlank()) onSendCode(code.trim()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verificar Código", fontWeight = FontWeight.Bold)
                        }
                    }

                    is AuthState.WaitPassword -> {
                        Text(
                            text = "Digite a senha de verificação em duas etapas (2FA):",
                            fontSize = 13.sp,
                            color = OnSurface
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Primary) },
                            placeholder = { Text("Senha da nuvem") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { if (password.isNotBlank()) onSendPassword(password) },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar Senha", fontWeight = FontWeight.Bold)
                        }
                    }

                    is AuthState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Erro: ${authState.message}",
                                color = Color(0xFFC62828),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    )
}
