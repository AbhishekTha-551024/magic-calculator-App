package com.magic.calculator.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticManager(context: Context) {

    var intensityScale: Float = 1.0f

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun shakeFeedback(level: Int) {
        val baseDuration = when (level) {
            1 -> 50L
            2 -> 75L
            3 -> 100L
            else -> 50L
        }
        vibrateOnce((baseDuration * intensityScale).toLong().coerceAtLeast(10L))
    }

    fun successFeedback() {
        val timings = longArrayOf(0L, scale(200L), 80L, scale(300L))
        vibratePattern(timings)
    }

    fun magicPulse() {
        val timings = longArrayOf(0L, scale(150L), 60L, scale(150L))
        vibratePattern(timings)
    }

    fun magicVibrate() {
        val timings = longArrayOf(0L, scale(100L), 50L, scale(200L))
        vibratePattern(timings)
    }

    fun buttonTick() {
        vibrateOnce(scale(18L))
    }

    fun errorBuzz() {
        vibrateOnce(scale(80L))
    }

    private fun scale(duration: Long): Long =
        (duration * intensityScale).toLong().coerceAtLeast(10L)

    private fun vibrateOnce(duration: Long) {
        if (intensityScale == 0f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) { }
    }

    private fun vibratePattern(timings: LongArray) {
        if (intensityScale == 0f) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) { }
    }
}