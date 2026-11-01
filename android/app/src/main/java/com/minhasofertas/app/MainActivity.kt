package com.minhasofertas.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.minhasofertas.app.data.OfferRepository
import com.minhasofertas.app.service.TelegramMonitorService
import com.minhasofertas.app.ui.MainScreen
import com.minhasofertas.app.ui.theme.AppColorScheme
import com.minhasofertas.app.ui.theme.AppTypography

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkNotificationPermission()

        setContent {
            MaterialTheme(
                colorScheme = AppColorScheme,
                typography = AppTypography
            ) {
                val offers by OfferRepository.offers.collectAsState()
                val isMonitoring by OfferRepository.monitoringActive.collectAsState()

                MainScreen(
                    offers = offers,
                    isMonitoring = isMonitoring,
                    onToggleMonitoring = {
                        toggleMonitoringService(!isMonitoring)
                    }
                )
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun toggleMonitoringService(start: Boolean) {
        val intent = Intent(this, TelegramMonitorService::class.java)
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            stopService(intent)
        }
    }
}
