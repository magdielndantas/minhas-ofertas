package com.minhasofertas.app.data

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var apiId: Int
        get() = prefs.getInt("api_id", 0)
        set(value) = prefs.edit().putInt("api_id", value).apply()

    var apiHash: String
        get() = prefs.getString("api_hash", "") ?: ""
        set(value) = prefs.edit().putString("api_hash", value).apply()

    val isApiConfigured: Boolean
        get() = apiId > 0 && apiHash.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
