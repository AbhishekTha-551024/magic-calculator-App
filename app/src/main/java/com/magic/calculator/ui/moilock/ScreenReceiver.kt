package com.magic.calculator.ui.moilock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.magic.calculator.logic.SettingsManager

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsManager = SettingsManager(context)
            if (settingsManager.appMode == 4) {
                val lockIntent = Intent(context, MoiLockActivity::class.java)
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(lockIntent)
            }
        }
    }
}
