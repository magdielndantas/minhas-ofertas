package com.minhasofertas.app

import android.app.Application
import com.minhasofertas.app.data.ConfigManager
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.service.TelegramMonitorService
import com.minhasofertas.app.telegram.TelegramManager

class MinhasOfertasApp : Application() {

    companion object {
        lateinit var instance: MinhasOfertasApp
            private set
    }

    lateinit var telegramManager: TelegramManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Inicializa persistência local Room e Configurações
        OfferRepository.init(applicationContext)
        ConfigManager.init(applicationContext)

        // 2. Registra canais de notificação no sistema
        TelegramMonitorService.createChannels(applicationContext)

        // 3. Inicializa cliente Telegram desacoplado do ciclo da Activity
        telegramManager = TelegramManager(applicationContext)
    }
}
