package com.magic.calculator.ui.moilock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.magic.calculator.R
import com.magic.calculator.logic.SettingsManager

class MoiLockService : Service() {

    private lateinit var screenReceiver: ScreenReceiver

    override fun onCreate() {
        super.onCreate()
        
        // Register the Screen Receiver dynamically
        screenReceiver = ScreenReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenReceiver, filter)
        
        // Start Foreground to ensure it stays alive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "moilock_service_channel"
            val channel = NotificationChannel(
                channelId,
                "MoiLock Background Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("MoiLock")
                .setContentText("Lock screen identity active")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            startForeground(1984, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settingsManager = SettingsManager(this)
        // If the user turned off MoiLock mode, stop the service
        if (settingsManager.appMode != 4) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
