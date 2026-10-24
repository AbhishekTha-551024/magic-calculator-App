package com.magic.calculator.feature.voice_wallpaper.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * StealthVoiceListener
 *
 * Implements a rock-solid, debounce-protected continuous speech recognition loop.
 * - [mode] can be:
 *   "CARD": ONLY extracts 52 playing cards. Never triggers number capture on stray digits!
 *   "NUMBER": ONLY extracts numbers. Never triggers card capture!
 *   "AUTO": Checks cards first, then numbers.
 */
class StealthVoiceListener(
    private val context: Context,
    val mode: String = "AUTO", // "CARD", "NUMBER", "AUTO"
    private val onStateChanged: ((String) -> Unit)? = null,
    private val onRmsChanged: ((Float) -> Unit)? = null,
    private val onRawTranscript: ((String) -> Unit)? = null,
    private val onErrorDetail: ((String, Int) -> Unit)? = null,
    private val onCardCaptured: ((VoiceCardExtractor.PlayingCard) -> Unit)? = null,
    private val onNumberCaptured: ((String) -> Unit)? = null
) : RecognitionListener {

    constructor(
        context: Context,
        onNumberCaptured: (String) -> Unit
    ) : this(
        context = context,
        mode = "NUMBER",
        onStateChanged = null,
        onRmsChanged = null,
        onRawTranscript = null,
        onErrorDetail = null,
        onCardCaptured = null,
        onNumberCaptured = onNumberCaptured
    )

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isProcessingCapture = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var originalNotificationVolume = -1
    private var originalSystemVolume = -1
    private var isRecognizing = false
    private val hapticManager = com.magic.calculator.core.haptic.HapticManager(context)
    private var hasVibratedForThisSession = false

    companion object {
        private const val TAG = "StealthVoiceListener"

        fun getErrorName(errorCode: Int): String {
            return when (errorCode) {
                SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO (Audio recording error)"
                SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT (Client side error)"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS (No Mic Permission)"
                SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK (Network error)"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT (Network timeout)"
                SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH (No speech recognized in audio)"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY (Speech service busy)"
                SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER (Server error)"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT (No speech input heard)"
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED (Server disconnected)"
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS (Too many requests)"
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
                15 -> "ERROR_CANNOT_LISTEN_TO_DOWNLOAD (15)"
                else -> "ERROR_UNKNOWN ($errorCode)"
            }
        }
    }

    /**
     * Starts continuous listening.
     */
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val err = "RECORD_AUDIO permission is NOT granted"
            Log.e(TAG, "❌ $err")
            onErrorDetail?.invoke(err, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val err = "Speech recognition service not available"
            Log.w(TAG, "❌ $err")
            onErrorDetail?.invoke(err, -1)
            return
        }

        isListening = true
        isProcessingCapture = false
        hasVibratedForThisSession = false

        muteBeepSounds()
        onStateChanged?.invoke("🎙️ Connecting Microphone (Mode: $mode)...")

        mainHandler.post {
            ensureRecognizerAndListen()
        }
    }

    private fun muteBeepSounds() {
        try {
            audioManager?.let { am ->
                if (originalNotificationVolume == -1) {
                    originalNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    originalSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
                }
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                am.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust stream volume: ${e.message}")
        }
    }

    private fun restoreBeepSounds() {
        try {
            audioManager?.let { am ->
                if (originalNotificationVolume != -1) {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
                    am.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                    originalNotificationVolume = -1
                    originalSystemVolume = -1
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore stream volume: ${e.message}")
        }
    }

    private fun ensureRecognizerAndListen() {
        if (!isListening || isProcessingCapture) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ RECORD_AUDIO permission missing in ensureRecognizerAndListen")
            isListening = false
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@StealthVoiceListener)
                }
            }

            val intent = createSpeechIntent()
            isRecognizing = true
            speechRecognizer?.startListening(intent)
            onStateChanged?.invoke("🟢 Listening for ${if (mode == "CARD") "Card (e.g. Queen of Hearts, पान की बेगम)" else "Number (e.g. 63, 74)"}...")
        } catch (e: Exception) {
            isRecognizing = false
            val msg = e.message ?: "Unknown init error"
            Log.e(TAG, "Error starting speech recognition: $msg", e)
            onErrorDetail?.invoke(msg, -1)
            scheduleRestart(1000)
        }
    }

    private fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // DO NOT force EXTRA_PREFER_OFFLINE = true! It triggers ERROR_LANGUAGE_UNAVAILABLE (code 13) on devices without offline packs.
            val currentLocale = java.util.Locale.getDefault().toLanguageTag()
            if (currentLocale.isNotBlank() && (currentLocale.startsWith("en") || currentLocale.startsWith("hi"))) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
            } else {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            }
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US", "hi"))
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!isListening || isProcessingCapture) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isListening && !isProcessingCapture) {
                try {
                    val intent = createSpeechIntent()
                    isRecognizing = true
                    speechRecognizer?.startListening(intent)
                    onStateChanged?.invoke("🟢 Listening... (Speak now)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed startListening in scheduleRestart, recreating: ${e.message}")
                    recreateRecognizer(1000)
                }
            }
        }, delayMs)
    }

    private fun recreateRecognizer(delayBeforeRestart: Long = 800) {
        if (!isListening || isProcessingCapture) return
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Throwable) {}

            speechRecognizer = null
            isRecognizing = false
            scheduleRestart(delayBeforeRestart)
        }
    }

    fun stopListening(shouldVibrate: Boolean = false) {
        val wasListening = isListening
        isListening = false
        isProcessingCapture = false
        isRecognizing = false
        hasVibratedForThisSession = false
        mainHandler.removeCallbacksAndMessages(null)
        restoreBeepSounds()
        onStateChanged?.invoke("⚪ Stopped")
        if (wasListening && shouldVibrate) {
            hapticManager.micStopFeedback()
        }
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
                Log.d(TAG, "Stopped stealth voice listener & cleaned up recognizer")
            } catch (e: Exception) {
                Log.w(TAG, "Exception during stopListening cleanup: ${e.message}")
            }
        }
    }

    // ── RecognitionListener Callbacks ──────────────────────────────────────────

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech: Microphone open and listening (Mode: $mode)")
        isRecognizing = true
        onStateChanged?.invoke("🟢 Microphone Active — Speak ${if (mode == "CARD") "Card" else "Number"} now!")
        if (!hasVibratedForThisSession) {
            hasVibratedForThisSession = true
            hapticManager.micStartFeedback()
        }
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech: User speaking...")
        onStateChanged?.invoke("🗣️ Receiving Speech Audio...")
    }

    override fun onRmsChanged(rmsdB: Float) {
        val level = if (rmsdB < 0) 0f else rmsdB
        onRmsChanged?.invoke(level)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech: Speech ended, decoding audio...")
        isRecognizing = false
        onStateChanged?.invoke("⚡ Decoding Speech...")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        Log.d(TAG, "onPartialResults (mode=$mode): matches = $matches")
        if (!matches.isNullOrEmpty()) {
            val transcript = matches[0]
            onRawTranscript?.invoke(transcript)
        }
        processMatches(matches, isFinal = false)
    }

    override fun onResults(results: Bundle?) {
        isRecognizing = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        Log.d(TAG, "onResults (mode=$mode): matches = $matches")
        if (!matches.isNullOrEmpty()) {
            val transcript = matches[0]
            onRawTranscript?.invoke(transcript)
        }
        val captured = processMatches(matches, isFinal = true)

        if (!captured && isListening && !isProcessingCapture) {
            scheduleRestart(450)
        }
    }

    override fun onError(error: Int) {
        isRecognizing = false
        val errorName = getErrorName(error)
        Log.d(TAG, "onError: $errorName (code $error)")
        onErrorDetail?.invoke(errorName, error)

        if (!isListening || isProcessingCapture) return

        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                onStateChanged?.invoke("⏳ Listening... (Speak clearly)")
                scheduleRestart(500)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                onStateChanged?.invoke("❌ Permission Denied")
                stopListening()
            }
            13, 14 -> { // ERROR_LANGUAGE_UNAVAILABLE, ERROR_LANGUAGE_NOT_SUPPORTED
                Log.w(TAG, "Language pack not available, restarting recognizer with system defaults...")
                onStateChanged?.invoke("🔄 Switching to device default speech engine...")
                recreateRecognizer(1200)
            }
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> {
                onStateChanged?.invoke("🔄 Resetting Speech Recognizer...")
                recreateRecognizer(1200)
            }
            else -> {
                scheduleRestart(1000)
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        Log.d(TAG, "onEvent: eventType=$eventType")
    }

    private fun processMatches(matches: ArrayList<String>?, isFinal: Boolean): Boolean {
        if (!isListening || matches.isNullOrEmpty() || isProcessingCapture) return false

        for (phrase in matches) {
            Log.d(TAG, "Analyzing speech phrase: '$phrase' in mode: $mode")

            // 1. If mode is "CARD" or "AUTO", try Card extraction:
            if (mode != "NUMBER" && onCardCaptured != null) {
                val card = VoiceCardExtractor.extractCard(phrase)
                if (card != null) {
                    Log.d(TAG, "🃏 CARD RECOGNIZED: '${card.englishName}' (${card.code}) from phrase: '$phrase' (isFinal=$isFinal)")
                    isProcessingCapture = true
                    hapticManager.micCapturedFeedback()
                    stopListening(shouldVibrate = false)
                    onCardCaptured.invoke(card)
                    return true
                }
            }

            // 2. If mode is "NUMBER" or "AUTO", try Number extraction:
            // CRITICAL: If mode == "CARD", NEVER extract numbers! Ignore partial stray digits!
            if (mode != "CARD" && onNumberCaptured != null) {
                val number = VoiceNumberExtractor.extractNumber(phrase)
                if (number != null && number.isNotEmpty()) {
                    Log.d(TAG, "🎉 NUMBER RECOGNIZED: '$number' from phrase: '$phrase' (isFinal=$isFinal)")
                    isProcessingCapture = true
                    hapticManager.micCapturedFeedback()
                    stopListening(shouldVibrate = false)
                    onNumberCaptured.invoke(number)
                    return true
                }
            }
        }
        return false
    }
}
