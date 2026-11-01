package com.minhasofertas.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.minhasofertas.app.MainActivity
import com.minhasofertas.app.data.Offer
import com.minhasofertas.app.data.OfferRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.util.Locale

class TelegramMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_channel"
        const val OFFERS_CHANNEL_ID = "offers_channel"
        private const val FOREGROUND_NOTIF_ID = 1001

        fun notifyOffer(context: Context, offer: Offer, isTargetPriceAlert: Boolean = false) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Validação de permissão para Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            // Garante que o canal de ofertas esteja criado
            createChannels(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                (offer.id % Int.MAX_VALUE).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val priceText = offer.price?.let { " por R$ %.2f".format(Locale.GERMAN, it) } ?: ""
            val discountText = offer.discountPercent?.let { " (-$it% OFF)" } ?: ""
            val couponText = offer.coupon?.let { " • Cupom: $it" } ?: ""
            val productTitle = offer.product ?: "Oferta detectada"
            val titlePrefix = if (isTargetPriceAlert) "🎯 PREÇO ALVO ALCANÇADO: " else "🔥 "

            val notifBuilder = NotificationCompat.Builder(context, OFFERS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$titlePrefix$productTitle$priceText$discountText")
                .setContentText("${offer.channel}$couponText")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${offer.message}\n$couponText")
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            // Ação 1: Abrir Oferta na Loja / Canal
            val targetUrl = offer.storeLink ?: offer.link
            if (targetUrl.startsWith("http")) {
                val openIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    ((offer.id + 1) % Int.MAX_VALUE).toInt(),
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                notifBuilder.addAction(
                    android.R.drawable.ic_menu_view,
                    "Abrir Loja",
                    openPendingIntent
                )
            }

            // Ação 2: Copiar Cupom com um clique na notificação
            if (!offer.coupon.isNullOrBlank()) {
                val copyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("coupon", offer.coupon)
                }
                val copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    ((offer.id + 2) % Int.MAX_VALUE).toInt(),
                    copyIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                notifBuilder.addAction(
                    android.R.drawable.ic_menu_save,
                    "Copiar Cupom",
                    copyPendingIntent
                )
            }

            manager.notify((offer.id % 100000).toInt(), notifBuilder.build())
        }

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

                // Canal do serviço persistente em background (silencioso e sem interrupções)
                val monitorChannel = NotificationChannel(
                    MONITOR_CHANNEL_ID,
                    "Serviço de Monitoramento",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Mantém a escuta de novos posts no Telegram em segundo plano"
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                }

                // Canal de alertas de novas ofertas com alta prioridade
                val offersChannel = NotificationChannel(
                    OFFERS_CHANNEL_ID,
                    "Alertas de Novas Ofertas",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificações instantâneas de novas promoções e cupons encontrados"
                    enableVibration(true)
                    setShowBadge(true)
                }

                manager.createNotificationChannel(monitorChannel)
                manager.createNotificationChannel(offersChannel)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification("Monitorando canais de ofertas em tempo real...")
        startForeground(FOREGROUND_NOTIF_ID, notification)
        OfferRepository.setMonitoringStatus(true)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        OfferRepository.setMonitoringStatus(false)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Minhas Ofertas")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }
}
