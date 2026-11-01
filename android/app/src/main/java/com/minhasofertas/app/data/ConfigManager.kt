package com.minhasofertas.app.data

import android.content.Context
import com.minhasofertas.app.data.local.AppDatabase
import com.minhasofertas.app.data.local.ChannelEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object ConfigManager {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var database: AppDatabase? = null
    private var preferences: UserPreferences? = null

    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels = _channels.asStateFlow()

    private val _keywords = MutableStateFlow<List<String>>(emptyList())
    val keywords = _keywords.asStateFlow()

    val suggestedKeywords: List<String> = AppConfig.DEFAULT_KEYWORDS

    private val _ignoreTerms = MutableStateFlow<List<String>>(AppConfig.DEFAULT_IGNORE_TERMS)
    val ignoreTerms = _ignoreTerms.asStateFlow()

    private val _maxPriceAlert = MutableStateFlow<Double?>(null)
    val maxPriceAlert = _maxPriceAlert.asStateFlow()

    fun init(context: Context) {
        if (database != null) return
        val db = AppDatabase.getInstance(context)
        val prefs = UserPreferences(context)
        database = db
        preferences = prefs

        // Carrega palavras-chave e termos ignorados salvos
        val savedKeywords = prefs.keywordsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (savedKeywords.isNotEmpty()) {
            _keywords.value = savedKeywords
        }

        val savedIgnoreTerms = prefs.ignoreTermsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (savedIgnoreTerms.isNotEmpty()) {
            _ignoreTerms.value = savedIgnoreTerms
        }

        val savedMaxPrice = prefs.maxPriceAlert
        if (savedMaxPrice > 0.0) {
            _maxPriceAlert.value = savedMaxPrice
        }

        // Observa e popula canais padrão se banco estiver vazio
        scope.launch {
            val count = db.channelDao().getCount()
            if (count == 0) {
                val defaults = AppConfig.DEFAULT_CHANNELS.map {
                    ChannelEntity(username = it.removePrefix("@").trim(), enabled = true)
                }
                db.channelDao().insertChannels(defaults)
            }

            db.channelDao().getAllChannelsFlow().collect { list ->
                _channels.value = list
            }
        }
    }

    fun addChannel(rawUsername: String) {
        val clean = rawUsername.removePrefix("@").trim().lowercase()
        if (clean.isBlank()) return
        scope.launch {
            database?.channelDao()?.insertChannel(ChannelEntity(username = clean, enabled = true))
        }
    }

    fun toggleChannel(username: String, enabled: Boolean) {
        scope.launch {
            database?.channelDao()?.setChannelEnabled(username, enabled)
        }
    }

    fun deleteChannel(username: String) {
        scope.launch {
            database?.channelDao()?.deleteChannel(username)
        }
    }

    fun updateKeywords(newKeywords: List<String>) {
        _keywords.value = newKeywords
        preferences?.keywordsString = newKeywords.joinToString(",")
    }

    fun updateIgnoreTerms(newTerms: List<String>) {
        _ignoreTerms.value = newTerms
        preferences?.ignoreTermsString = newTerms.joinToString(",")
    }

    fun updateMaxPriceAlert(price: Double?) {
        _maxPriceAlert.value = price
        preferences?.maxPriceAlert = price ?: 0.0
    }

    fun getEnabledChannelUsernames(): Set<String> {
        return _channels.value.filter { it.enabled }.map { it.username.lowercase() }.toSet()
    }

    /**
     * Exporta configurações completas em formato JSON estruturado
     */
    fun exportConfigJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val channelsArray = JSONArray()
        for (c in _channels.value) {
            val obj = JSONObject()
            obj.put("username", c.username)
            obj.put("enabled", c.enabled)
            channelsArray.put(obj)
        }
        root.put("channels", channelsArray)

        val kwArray = JSONArray()
        _keywords.value.forEach { kwArray.put(it) }
        root.put("keywords", kwArray)

        val igArray = JSONArray()
        _ignoreTerms.value.forEach { igArray.put(it) }
        root.put("ignoreTerms", igArray)

        root.put("maxPriceAlert", _maxPriceAlert.value ?: 0.0)

        return root.toString(2)
    }

    /**
     * Importa configurações completas a partir de string JSON com validação de esquema
     */
    fun importConfigJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)

            // 1. Canais
            if (root.has("channels")) {
                val channelsArray = root.getJSONArray("channels")
                val importedChannels = mutableListOf<ChannelEntity>()
                for (i in 0 until channelsArray.length()) {
                    val obj = channelsArray.getJSONObject(i)
                    val username = obj.getString("username").removePrefix("@").trim().lowercase()
                    val enabled = obj.optBoolean("enabled", true)
                    if (username.isNotBlank()) {
                        importedChannels.add(ChannelEntity(username = username, enabled = enabled))
                    }
                }
                if (importedChannels.isNotEmpty()) {
                    scope.launch {
                        database?.channelDao()?.insertChannels(importedChannels)
                    }
                }
            }

            // 2. Palavras-chave
            if (root.has("keywords")) {
                val kwArray = root.getJSONArray("keywords")
                val importedKw = mutableListOf<String>()
                for (i in 0 until kwArray.length()) {
                    importedKw.add(kwArray.getString(i).trim())
                }
                if (importedKw.isNotEmpty()) {
                    updateKeywords(importedKw)
                }
            }

            // 3. Termos ignorados
            if (root.has("ignoreTerms")) {
                val igArray = root.getJSONArray("ignoreTerms")
                val importedIg = mutableListOf<String>()
                for (i in 0 until igArray.length()) {
                    importedIg.add(igArray.getString(i).trim())
                }
                if (importedIg.isNotEmpty()) {
                    updateIgnoreTerms(importedIg)
                }
            }

            // 4. Teto de preço
            if (root.has("maxPriceAlert")) {
                val maxP = root.getDouble("maxPriceAlert")
                updateMaxPriceAlert(if (maxP > 0.0) maxP else null)
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
