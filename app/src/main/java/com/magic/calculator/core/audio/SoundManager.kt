package com.magic.calculator.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class SoundManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var _volume: Int = 80

    val volume: Int get() = _volume

    /**
     * Set while a microphone capture session is live.
     *
     * A `ToneGenerator` plays through STREAM_MUSIC, which an open microphone hears. Magic feedback
     * tones landing mid-phrase were being picked up as part of the spoken card or number, so every
     * tone is skipped for the duration of a session.
     */
    @Volatile
    var areEffectsSuppressed: Boolean = false

    init {
        initToneGenerator()
    }

    private fun initToneGenerator() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, _volume)
        } catch (e: RuntimeException) {
            toneGenerator = null
        }
    }

    fun playShakeBeep(level: Int) {
        val tone = when (level) {
            1 -> ToneGenerator.TONE_PROP_BEEP
            2 -> ToneGenerator.TONE_PROP_BEEP2
            3 -> ToneGenerator.TONE_CDMA_PIP
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        playTone(tone, 120)
    }

    fun playMagicWhoosh() {
        playTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
    }

    fun playMagicSwoosh() {
        playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    }

    fun playSuccess() {
        playTone(ToneGenerator.TONE_PROP_ACK, 200)
        handler.postDelayed({
            playTone(ToneGenerator.TONE_CDMA_PIP, 300)
        }, 250)
    }

    fun playError() {
        playTone(ToneGenerator.TONE_PROP_NACK, 150)
    }

    fun playButtonTick() {
        playTone(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun setVolume(percent: Int) {
        _volume = percent.coerceIn(0, 100)
        try {
            toneGenerator?.release()
        } catch (e: Exception) { }
        initToneGenerator()
    }

    private fun playTone(tone: Int, durationMs: Int) {
        if (_volume == 0) return
        // A ToneGenerator is audible to an open microphone. While a voice capture session is live
        // the recognizer would hear the swoosh as part of the phrase and mis-parse the card or number.
        if (areEffectsSuppressed) return
        try {
            toneGenerator?.startTone(tone, durationMs)
        } catch (e: Exception) {
            initToneGenerator()
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) { }
    }
}