package com.minhasofertas.app.telegram

sealed class AuthState {
    object Initial : AuthState()
    object NeedApiCredentials : AuthState()
    object WaitParameters : AuthState()
    object WaitPhoneNumber : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState()
    object Ready : AuthState()
    data class Error(val message: String) : AuthState()
}
