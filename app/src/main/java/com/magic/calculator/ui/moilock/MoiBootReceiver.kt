package com.magic.calculator.ui.moilock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.magic.calculator.logic.SettingsManager

class MoiBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val settingsManager = SettingsManager(context)
            if (settingsManager.appMode == 4) {
                val serviceIntent = Intent(context, MoiLockService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
