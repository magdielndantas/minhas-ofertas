package com.minhasofertas.app.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import com.minhasofertas.app.data.AppConfig
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.data.PriceParser
import com.minhasofertas.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramManager(private val context: Context) : Client.ResultHandler {

    private val TAG = "TelegramManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: Client? = null
    val userPreferences = UserPreferences(context)

    private val _authState = MutableStateFlow<AuthState>(
        if (userPreferences.isApiConfigured) AuthState.Initial else AuthState.NeedApiCredentials
    )
    val authState = _authState.asStateFlow()

    private val chatTitles = mutableMapOf<Long, String>()
    private val chatUsernames = mutableMapOf<Long, String>()

    init {
        if (userPreferences.isApiConfigured) {
            initClient()
        }
    }

    fun configureApi(apiId: Int, apiHash: String) {
        userPreferences.apiId = apiId
        userPreferences.apiHash = apiHash
        _authState.value = AuthState.Initial
        initClient()
    }

    private fun initClient() {
        if (client != null) {
            try {
                client?.send(TdApi.Close(), null)
            } catch (e: Exception) {
                Log.w(TAG, "Aviso ao encerrar cliente anterior", e)
            }
            client = null
        }
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            client = Client.create(this, { e ->
                Log.e(TAG, "Exceção no TDLib: ${e.message}")
                _authState.value = AuthState.Error(translateTdlibError(e.message ?: "Erro no cliente TDLib"))
            }, { t ->
                Log.e(TAG, "Erro crítico no TDLib", t)
                _authState.value = AuthState.Error(translateTdlibError(t.message ?: "Erro crítico no TDLib"))
            })
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao criar cliente TDLib", e)
            _authState.value = AuthState.Error("Falha na inicialização nativa: ${e.message}")
        }
    }

    override fun onResult(`object`: TdApi.Object?) {
        if (`object` == null) return
        when (`object`) {
            is TdApi.UpdateAuthorizationState -> {
                Log.d(TAG, "UpdateAuthorizationState: ${`object`.authorizationState::class.java.simpleName}")
                handleAuthState(`object`.authorizationState)
            }
            is TdApi.UpdateNewMessage -> {
                handleNewMessage(`object`.message)
            }
            is TdApi.UpdateNewChat -> {
                val chat = `object`.chat
                chatTitles[chat.id] = chat.title
            }
            is TdApi.Error -> {
                Log.e(TAG, "TDLib Error (${`object`.code}): ${`object`.message}")
                _authState.value = AuthState.Error(`object`.message)
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val tdDir = File(context.filesDir, "tdlib")
                if (!tdDir.exists()) tdDir.mkdirs()

                val filesDir = File(context.filesDir, "tdlib_files")
                if (!filesDir.exists()) filesDir.mkdirs()

                val deviceModel = if (!Build.MODEL.isNullOrBlank()) Build.MODEL else "Android Device"
                val systemVersion = "Android " + (Build.VERSION.RELEASE ?: "10")

                val parameters = TdApi.SetTdlibParameters(
                    false,                          // useTestDc
                    tdDir.absolutePath,             // databaseDirectory
                    filesDir.absolutePath,          // filesDirectory
                    ByteArray(0),                   // databaseEncryptionKey
                    true,                           // useFileDatabase
                    true,                           // useChatInfoDatabase
                    true,                           // useMessageDatabase
                    false,                          // useSecretChats
                    userPreferences.apiId,          // apiId configurado pelo usuário
                    userPreferences.apiHash,        // apiHash configurado pelo usuário
                    "pt-br",                        // systemLanguageCode
                    deviceModel,                    // deviceModel
                    systemVersion,                  // systemVersion
                    "1.0.9"                         // applicationVersion
                )

                client?.send(parameters) { result ->
                    if (result is TdApi.Error) {
                        Log.e(TAG, "Erro ao enviar SetTdlibParameters: ${result.message}")
                        _authState.value = AuthState.Error("Erro nos parâmetros: " + translateTdlibError(result.message))
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                val deliveryDescription = when (info.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> "Enviado no chat oficial do aplicativo Telegram"
                    is TdApi.AuthenticationCodeTypeSms -> "Enviado por SMS para o seu celular"
                    is TdApi.AuthenticationCodeTypeCall -> "O Telegram fará uma ligação telefônica"
                    is TdApi.AuthenticationCodeTypeFlashCall -> "O Telegram fará uma chamada rápida"
                    is TdApi.AuthenticationCodeTypeMissedCall -> "Verificação por chamada perdida"
                    else -> "Verifique seu app do Telegram ou caixa de SMS"
                }
                Log.d(TAG, "WaitCode delivery: $deliveryDescription, timeout: ${info.timeout}")
                _authState.value = AuthState.WaitCode(deliveryDescription, info.timeout)
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitPassword
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Ready
                searchAndJoinConfiguredChannels()
            }
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.Initial
            }
            else -> {
                Log.d(TAG, "Estado de autorização: $state")
            }
        }
    }

    private fun sanitizePhoneNumber(raw: String): String {
        val trimmed = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+55") -> "+$digits"
            digits.startsWith("55") && (digits.length == 12 || digits.length == 13) -> "+$digits"
            digits.length == 10 || digits.length == 11 -> "+55$digits"
            trimmed.startsWith("+") -> "+$digits"
            else -> "+$digits"
        }
    }

    private fun translateTdlibError(error: String): String {
        return when {
            error.contains("PHONE_NUMBER_INVALID", ignoreCase = true) -> "Número de telefone inválido. Verifique o DDD e o código do país (+55)."
            error.contains("PHONE_CODE_INVALID", ignoreCase = true) -> "Código de verificação incorreto."
            error.contains("PHONE_CODE_EXPIRED", ignoreCase = true) -> "O código expirou. Solicite um novo código."
            error.contains("PASSWORD_HASH_INVALID", ignoreCase = true) -> "Senha de verificação em duas etapas incorreta."
            error.contains("FLOOD_WAIT", ignoreCase = true) -> "Muitas tentativas em pouco tempo. Aguarde alguns minutos antes de tentar novamente."
            error.contains("API_ID_INVALID", ignoreCase = true) -> "API ID ou API HASH inválidos. Confira em my.telegram.org."
            error.contains("PHONE_NUMBER_BANNED", ignoreCase = true) -> "Este número foi bloqueado pelo Telegram."
            error.contains("Unexpected client request", ignoreCase = true) -> "Aguardando conexão com o Telegram. Tente novamente em instantes."
            else -> error
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        val cleanPhone = sanitizePhoneNumber(phoneNumber)
        Log.d(TAG, "Enviando telefone formatado: $cleanPhone (original: $phoneNumber)")
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(cleanPhone, settings)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Erro ao enviar telefone: ${result.message}")
                _authState.value = AuthState.Error(translateTdlibError(result.message))
            }
        }
    }

    fun resendAuthCode() {
        client?.send(TdApi.ResendAuthenticationCode()) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Erro ao reenviar código: ${result.message}")
                _authState.value = AuthState.Error(translateTdlibError(result.message))
            }
        }
    }

    fun checkAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code.trim())) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Erro ao validar código: ${result.message}")
                _authState.value = AuthState.Error(translateTdlibError(result.message))
            }
        }
    }

    fun checkPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Erro na senha 2FA: ${result.message}")
                _authState.value = AuthState.Error(translateTdlibError(result.message))
            }
        }
    }

    private fun searchAndJoinConfiguredChannels() {
        for (username in AppConfig.DEFAULT_CHANNELS) {
            client?.send(TdApi.SearchPublicChat(username)) { result ->
                if (result is TdApi.Chat) {
                    chatTitles[result.id] = result.title
                    chatUsernames[result.id] = username
                    client?.send(TdApi.OpenChat(result.id), null)
                }
            }
        }
    }

    private fun handleNewMessage(message: TdApi.Message) {
        val text = when (val content = message.content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> content.caption.text
            else -> null
        } ?: return

        if (!PriceParser.isOfferMatching(text)) return

        val price = PriceParser.extractPrice(text)
        val coupon = PriceParser.extractCoupon(text)
        val channelTitle = chatTitles[message.chatId] ?: "Canal Telegram"
        val channelUsername = chatUsernames[message.chatId]

        val link = if (channelUsername != null) {
            "https://t.me/$channelUsername/${message.id}"
        } else {
            "#"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val timestamp = dateFormat.format(Date(message.date * 1000L))

        var imagePath: String? = null
        if (message.content is TdApi.MessagePhoto) {
            val photo = (message.content as TdApi.MessagePhoto).photo
            val largestSize = photo.sizes.maxByOrNull { it.photo.size }
            if (largestSize != null) {
                val file = largestSize.photo
                if (file.local.isDownloadingCompleted) {
                    imagePath = file.local.path
                } else {
                    client?.send(TdApi.DownloadFile(file.id, 1, 0, 0, true), null)
                }
            }
        }

        val offer = Offer(
            id = message.id,
            channel = channelTitle,
            price = price,
            link = link,
            message = text,
            imagePath = imagePath,
            timestamp = timestamp,
            coupon = coupon
        )

        OfferRepository.addOffer(offer)
    }

    fun close() {
        client?.send(TdApi.Close(), null)
    }
}
