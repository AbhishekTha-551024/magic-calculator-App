package com.magic.calculator.feature.applock.presentation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.applock.presentation.MoiLockActivity

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_SCREEN_OFF || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (context.appContainer.appLockRepository.isAppLockActive()) {
                val lockIntent = Intent(context, MoiLockActivity::class.java)
                lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                context.startActivity(lockIntent)
            }
        }
    }
}
