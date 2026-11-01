package com.minhasofertas.app.telegram

import android.content.Context
import com.minhasofertas.app.data.AppConfig
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.data.PriceParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramManager(private val context: Context) : Client.ResultHandler {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var client: Client? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState = _authState.asStateFlow()

    private val chatTitles = mutableMapOf<Long, String>()
    private val chatUsernames = mutableMapOf<Long, String>()

    init {
        initClient()
    }

    private fun initClient() {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(this, { e ->
            _authState.value = AuthState.Error(e.message ?: "Erro no cliente TDLib")
        }, { t ->
            _authState.value = AuthState.Error(t.message ?: "Erro crítico no TDLib")
        })
    }

    override fun onResult(`object`: TdApi.Object?) {
        when (`object`) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthState(`object`.authorizationState)
            }
            is TdApi.UpdateNewMessage -> {
                handleNewMessage(`object`.message)
            }
            is TdApi.UpdateNewChat -> {
                val chat = `object`.chat
                chatTitles[chat.id] = chat.title
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val tdDir = File(context.filesDir, "tdlib")
                if (!tdDir.exists()) tdDir.mkdirs()

                val parameters = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = tdDir.absolutePath
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = AppConfig.API_ID
                    apiHash = AppConfig.API_HASH
                    systemLanguageCode = "pt-br"
                    deviceModel = "Android Device"
                    systemVersion = "Android"
                    applicationVersion = "1.0"
                }

                client?.send(parameters) { result ->
                    if (result is TdApi.Error) {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitCode
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
        }
    }

    fun setPhoneNumber(phoneNumber: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    fun checkAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    fun checkPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    private fun searchAndJoinConfiguredChannels() {
        for (username in AppConfig.DEFAULT_CHANNELS) {
            client?.send(TdApi.SearchPublicChat(username)) { result ->
                if (result is TdApi.Chat) {
                    chatTitles[result.id] = result.title
                    chatUsernames[result.id] = username
                    // Abre o chat para receber atualizações de mensagens
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
