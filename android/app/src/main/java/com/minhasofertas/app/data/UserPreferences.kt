package com.minhasofertas.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class UserPreferences(context: Context) {
    private val TAG = "UserPreferences"

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_app_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao inicializar EncryptedSharedPreferences, usando fallback", e)
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    }

    var apiId: Int
        get() = prefs.getInt("api_id", 0)
        set(value) = prefs.edit().putInt("api_id", value).apply()

    var apiHash: String
        get() = prefs.getString("api_hash", "") ?: ""
        set(value) = prefs.edit().putString("api_hash", value).apply()

    val isApiConfigured: Boolean
        get() = apiId > 0 && apiHash.isNotBlank()

    var maxPriceAlert: Double
        get() = prefs.getString("max_price_alert", "")?.toDoubleOrNull() ?: 0.0
        set(value) = prefs.edit().putString("max_price_alert", if (value > 0) value.toString() else "").apply()

    var keywordsString: String
        get() = prefs.getString("custom_keywords", "") ?: ""
        set(value) = prefs.edit().putString("custom_keywords", value).apply()

    var ignoreTermsString: String
        get() = prefs.getString("custom_ignore_terms", AppConfig.DEFAULT_IGNORE_TERMS.joinToString(",")) ?: ""
        set(value) = prefs.edit().putString("custom_ignore_terms", value).apply()

    init {
        // Migra credenciais antigas em texto puro se existirem
        val legacyPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val legacyId = legacyPrefs.getInt("api_id", 0)
        val legacyHash = legacyPrefs.getString("api_hash", "") ?: ""
        if (legacyId > 0 && legacyHash.isNotBlank() && apiId == 0) {
            apiId = legacyId
            apiHash = legacyHash
            legacyPrefs.edit().clear().apply()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
