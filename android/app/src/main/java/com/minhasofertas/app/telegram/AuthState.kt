package com.minhasofertas.app.telegram

sealed class AuthState {
    object Initial : AuthState()
    object NeedApiCredentials : AuthState()
    object WaitParameters : AuthState()
    object WaitPhoneNumber : AuthState()
    data class WaitCode(val deliveryDescription: String = "Telegram / SMS", val timeout: Int = 60) : AuthState()
    object WaitPassword : AuthState()
    object Ready : AuthState()
    data class Error(val message: String) : AuthState()
}
