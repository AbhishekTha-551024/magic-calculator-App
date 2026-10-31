package com.magic.calculator.feature.voice_wallpaper.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.magic.calculator.core.audio.MicArbiter
import com.magic.calculator.core.audio.MicErrorPolicy
import com.magic.calculator.core.audio.MicPermission
import com.magic.calculator.core.haptic.HapticManager
import kotlinx.coroutines.launch

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
    private val hapticManager: HapticManager,
    val mode: String = "AUTO", // "CARD", "NUMBER", "AUTO"
    private val onStateChanged: ((String) -> Unit)? = null,
    private val onRmsChanged: ((Float) -> Unit)? = null,
    private val onRawTranscript: ((String) -> Unit)? = null,
    private val onErrorDetail: ((String, Int) -> Unit)? = null,
    private val onCardCaptured: ((VoiceCardExtractor.PlayingCard) -> Unit)? = null,
    private val onNumberCaptured: ((String) -> Unit)? = null,
    /**
     * Fired when the listener stops **without** capturing anything — permission loss, mic busy
     * after retries, or an explicit stop. Not fired on the capture path, so a session owner can
     * use it to detect "listening ended on its own" without racing the result callbacks.
     */
    private val onStopped: (() -> Unit)? = null
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var isProcessingCapture = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var originalNotificationVolume = -1
    private var originalSystemVolume = -1
    private var isRecognizing = false
    private var hasVibratedForThisSession = false

    companion object {
        private const val TAG = "StealthVoiceListener"

        /**
         * Wall-clock (elapsedRealtime) timestamp until which any observed volume *decrease*
         * was caused by our own [muteBeepSounds], not by the magician pressing volume down.
         *
         * Without this, StealthVoiceService's volume detector reads the duck as a trigger,
         * starts a second capture session, which ducks again — a self-sustaining loop.
         */
        @Volatile
        var selfDuckUntilMs: Long = 0L

        /** How long a duck is attributed to us. Covers the async system volume broadcast. */
        const val SELF_DUCK_WINDOW_MS = 900L

        /** True while a volume change should be treated as self-inflicted. */
        fun isInsideSelfDuckWindow(): Boolean = SystemClock.elapsedRealtime() < selfDuckUntilMs

        /** What the magician is told once retries are exhausted. */
        const val MIC_BUSY_MESSAGE = "❌ Mic busy — another app holds the microphone. Close it and tap again."

        /** How long ASR waits for a lower-priority in-app holder to hand the mic over. */
        private const val MIC_ACQUIRE_TIMEOUT_MS = 2500L

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

    private var micToken: MicArbiter.Token? = null

    /** Backoff + give-up rules for microphone-open failures. Shared, so it is not a field. */
    private val micErrorPolicy = MicErrorPolicy(clock = { SystemClock.elapsedRealtime() })

    /** Main-thread scope used only for waiting on [MicArbiter]; never holds the mic itself. */
    private val listenerScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )

    /**
     * Starts continuous listening.
     */
    fun startListening() {
        if (!MicPermission.has(context)) {
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
        micErrorPolicy.reset()

        muteBeepSounds()
        onStateChanged?.invoke("🎙️ Connecting Microphone (Mode: $mode)...")

        // Take the microphone before touching SpeechRecognizer. Google's recognizer runs in a
        // different UID and Android 12+ refuses a second concurrent capture client; that
        // collision is the ERROR_AUDIO / "cannot record" the magician was seeing.
        val token = micToken ?: MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        if (token != null) {
            micToken = token
            mainHandler.post { ensureRecognizerAndListen() }
            return
        }

        // Denied. tryAcquire has already flagged any lower-priority in-app holder (clap loop,
        // mic meter) to yield, so waiting usually succeeds within a few hundred milliseconds.
        val holder = MicArbiter.peekOwner()
        onStateChanged?.invoke(
            if (holder != null) "⏳ Asking $holder to release the microphone..."
            else "⏳ Waiting for the microphone to settle..."
        )
        listenerScope.launch {
            val waited = MicArbiter.awaitAcquire(MicArbiter.Owner.ASR, MIC_ACQUIRE_TIMEOUT_MS)
            if (!isListening) {
                MicArbiter.release(waited)
                return@launch
            }
            if (waited == null) {
                val err = "Mic still owned by ${MicArbiter.peekOwner()} after ${MIC_ACQUIRE_TIMEOUT_MS}ms"
                Log.e(TAG, "❌ $err")
                onStateChanged?.invoke(
                    "❌ Mic busy — another app holds the microphone. Close it and tap again."
                )
                onErrorDetail?.invoke(err, SpeechRecognizer.ERROR_AUDIO)
                stopListening()
                return@launch
            }
            micToken = waited
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
                // Announce the duck as self-inflicted BEFORE making it, so StealthVoiceService's
                // volume-down detector does not mistake it for the magician's trigger.
                selfDuckUntilMs = SystemClock.elapsedRealtime() + SELF_DUCK_WINDOW_MS
                am.setStreamVolume(
                    AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
                am.setStreamVolume(
                    AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not adjust stream volume: ${e.message}")
        }
    }

    private fun restoreBeepSounds() {
        try {
            audioManager?.let { am ->
                if (originalNotificationVolume != -1) {
                    selfDuckUntilMs = SystemClock.elapsedRealtime() + SELF_DUCK_WINDOW_MS
                    am.setStreamVolume(
                        AudioManager.STREAM_NOTIFICATION, originalNotificationVolume,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
                    am.setStreamVolume(
                        AudioManager.STREAM_SYSTEM, originalSystemVolume,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
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

        if (!MicPermission.has(context)) {
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
            backOffOrGiveUp(
                retrying = "⚠️ Retrying speech recognition",
                giveUp = "❌ Speech recognition would not start — tap again.",
                recreate = false
            )
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
            // Through ensureRecognizerAndListen, not a direct startListening(): after
            // recreateRecognizer() the field is null, and `speechRecognizer?.startListening()` on a
            // null recognizer silently does nothing — the session would sit open until its deadline.
            if (isListening && !isProcessingCapture) ensureRecognizerAndListen()
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
        val stoppedByCapture = isProcessingCapture
        isListening = false
        isProcessingCapture = false
        isRecognizing = false
        hasVibratedForThisSession = false
        micErrorPolicy.reset()
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
            } finally {
                // Hand the mic back only after the recognizer is destroyed, so the next owner
                // (clap loop or another session) never opens a stream on top of this one.
                val token = micToken
                micToken = null
                if (token != null && MicArbiter.release(token)) {
                    Log.d(TAG, "Released microphone ownership")
                }
            }
            if (wasListening && !stoppedByCapture) {
                onStopped?.invoke()
            }
        }
    }

    // ── RecognitionListener Callbacks ──────────────────────────────────────────

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech: Microphone open and listening (Mode: $mode)")
        isRecognizing = true
        micErrorPolicy.onMicRecovered()
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
            SpeechRecognizer.ERROR_AUDIO -> backOffOrGiveUp(
                retrying = "⚠️ Mic busy — retrying",
                giveUp = MIC_BUSY_MESSAGE,
                recreate = false
            )

            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Normal silence, not a failure: the backoff ladder stays untouched.
                onStateChanged?.invoke("⏳ Listening... (Speak clearly)")
                scheduleRestart(500)
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                onStateChanged?.invoke("❌ Permission Denied")
                stopListening()
            }

            13, 14 -> { // ERROR_LANGUAGE_UNAVAILABLE, ERROR_LANGUAGE_NOT_SUPPORTED
                Log.w(TAG, "Language pack not available (code $error)")
                backOffOrGiveUp(
                    retrying = "🔄 Switching to device default speech engine",
                    giveUp = "❌ No speech pack for this language — enable one in Google settings.",
                    recreate = true
                )
            }

            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> backOffOrGiveUp(
                retrying = "🔄 Resetting Speech Recognizer",
                giveUp = "❌ Google's speech service is busy — close it and tap again.",
                recreate = true
            )

            else -> backOffOrGiveUp(
                retrying = "⚠️ $errorName — retrying",
                giveUp = "❌ Speech recognition keeps failing on this phone.",
                recreate = false
            )
        }
    }

    /**
     * Counts one failure and then either backs off or stops with a message the magician can act on.
     *
     * Every recoverable error path runs through here, so no branch can restart the recognizer at a
     * fixed interval forever — that unbounded hammer is what kept the microphone contested.
     *
     * @param recreate true destroys and recreates the recognizer first, which is what the busy and
     *   language errors need; a plain retry would just hit the same state again.
     */
    private fun backOffOrGiveUp(retrying: String, giveUp: String, recreate: Boolean) {
        when (val decision = micErrorPolicy.onMicError()) {
            is MicErrorPolicy.Decision.GiveUp -> {
                Log.e(TAG, "Stopping after repeated failures: $giveUp")
                onStateChanged?.invoke(giveUp)
                stopListening()
            }
            is MicErrorPolicy.Decision.RetryIn -> {
                onStateChanged?.invoke(
                    "$retrying in ${decision.delayMs}ms (attempt ${micErrorPolicy.consecutiveErrors})"
                )
                if (recreate) recreateRecognizer(decision.delayMs) else scheduleRestart(decision.delayMs)
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
