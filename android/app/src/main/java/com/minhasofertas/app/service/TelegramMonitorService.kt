package com.minhasofertas.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.minhasofertas.app.MainActivity
import com.minhasofertas.app.data.OfferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TelegramMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "monitor_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification("Monitorando canais de ofertas em tempo real...")
        startForeground(NOTIF_ID, notification)
        OfferRepository.setMonitoringStatus(true)

        serviceScope.launch {
            // Monitoramento e integração com TDLib
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        OfferRepository.setMonitoringStatus(false)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Ofertas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serviço em segundo plano para escuta de canais do Telegram"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Minhas Ofertas (Ativo)")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun notifyNewOffer(title: String, price: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔥 Nova Oferta Encontrada: $price")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }
}
