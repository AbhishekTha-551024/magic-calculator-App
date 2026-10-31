package com.magic.calculator.feature.voice_wallpaper.data.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.util.Log
import com.magic.calculator.core.audio.SoundManager
import com.magic.calculator.core.haptic.HapticManager
import com.magic.calculator.core.logic.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoiceCaptureManager
 *
 * The only owner of [StealthVoiceListener] in the process.
 *
 * Before this, three places each built their own listener — the Wallpaper & Voice Force dialog
 * (`SecretDialogHelper`), the in-app volume-down capture (`MainActivity`), and the locked-screen
 * capture (`StealthVoiceService`) — and nothing stopped two of them running at once. Two
 * `SpeechRecognizer` clients in one process produce `ERROR_RECOGNIZER_BUSY` /
 * `ERROR_TOO_MANY_REQUESTS`, and each one also ducked the system volume, which the service's
 * volume detector then read as a fresh "volume down" trigger.
 *
 * Callers ask for a [SessionHandle] with a [Caller] role. A request from a lower-priority role is
 * rejected while a higher one is live; a higher-priority request preempts a lower one.
 *
 * All methods must be called from the main thread (every existing call site already is).
 */
class VoiceCaptureManager(
    context: Context,
    private val settingsManager: SettingsManager,
    private val hapticManager: HapticManager,
    private val soundManager: SoundManager
) {

    /** Who is asking. Higher [priority] wins a contention. */
    enum class Caller(val priority: Int) {
        /** Volume-down while the screen is off / locked, served by StealthVoiceService. */
        LOCKED_VOLUME(10),

        /** Volume-down inside the calculator. */
        ACTIVITY_VOLUME(20),

        /** The "Test Voice" button in the Wallpaper & Voice Force dialog. */
        DIALOG_TEST(30)
    }

    enum class State { IDLE, CONNECTING, LISTENING, DECODING }

    enum class EndReason {
        CAPTURED,
        TIMEOUT,
        CANCELLED,
        MIC_BUSY,
        PERMISSION_DENIED,
        REJECTED,
        REPLACED
    }

    class SessionHandle internal constructor(val id: Long, val caller: Caller)

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeListener: StealthVoiceListener? = null
    private var activeHandle: SessionHandle? = null
    private var activeEnded: ((EndReason) -> Unit)? = null
    private var activeDeadline: Runnable? = null
    private var lastErrorCode: Int = Int.MIN_VALUE
    private var nextHandleId: Long = 1L

    private val _state = MutableStateFlow(State.IDLE)

    /** Lifecycle of the one live session, for UI that wants to reflect it. */
    val state: StateFlow<State> = _state.asStateFlow()

    private val _rms = MutableStateFlow(0f)

    /**
     * Live microphone level from the running session.
     *
     * This is what lets clap/snap detection ride along with a voice session instead of opening a
     * competing `AudioRecord` — see `SensorCoordinator.onExternalAudioLevel`.
     */
    val rms: StateFlow<Float> = _rms.asStateFlow()

    val isBusy: Boolean get() = activeListener != null

    fun activeCaller(): Caller? = activeHandle?.caller

    /**
     * True while an observed volume decrease should be attributed to our own beep-muting instead of
     * the magician pressing volume down. Delegates to the one live duck timestamp on
     * [StealthVoiceListener]; every volume-down detector must check this before requesting a
     * session, otherwise a duck re-triggers the session that caused it.
     */
    fun isInsideSelfDuckWindow(): Boolean = StealthVoiceListener.isInsideSelfDuckWindow()

    /**
     * Starts a capture session.
     *
     * @return the handle to pass to [release], or `null` when an equal-or-higher-priority session
     *   already owns the microphone. [onEnded] reports `REJECTED` in that case.
     */
    fun requestSession(
        caller: Caller,
        mode: String = settingsManager.wallpaperContentType,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onStateChanged: (String) -> Unit = {},
        onRmsChanged: (Float) -> Unit = {},
        onRawTranscript: (String) -> Unit = {},
        onErrorDetail: (String, Int) -> Unit = { _, _ -> },
        onCardCaptured: (VoiceCardExtractor.PlayingCard) -> Unit = {},
        onNumberCaptured: (String) -> Unit = {},
        onEnded: (EndReason) -> Unit = {}
    ): SessionHandle? {
        val current = activeHandle
        var preempted = false
        if (current != null) {
            if (current.caller.priority >= caller.priority) {
                Log.d(TAG, "${caller.name} rejected — ${current.caller} still owns the session")
                onEnded(EndReason.REJECTED)
                return null
            }
            Log.d(TAG, "${caller.name} preempts ${current.caller}")
            preempted = true
            closeSession(current, EndReason.REPLACED)
        }

        val handle = SessionHandle(nextHandleId++, caller)
        activeHandle = handle
        activeEnded = onEnded
        lastErrorCode = Int.MIN_VALUE
        _rms.value = 0f
        _state.value = State.CONNECTING

        val listener = StealthVoiceListener(
            context = appContext,
            hapticManager = hapticManager,
            mode = mode,
            onStateChanged = { text ->
                _state.value = when {
                    text.contains("Decoding") -> State.DECODING
                    text.contains("Microphone Active") || text.contains("Listening") -> State.LISTENING
                    else -> _state.value
                }
                onStateChanged(text)
            },
            onRmsChanged = { level ->
                _rms.value = level
                onRmsChanged(level)
            },
            onRawTranscript = onRawTranscript,
            onErrorDetail = { msg, code ->
                lastErrorCode = code
                onErrorDetail(msg, code)
            },
            onCardCaptured = { card ->
                closeSession(handle, EndReason.CAPTURED)
                onCardCaptured(card)
            },
            onNumberCaptured = { number ->
                closeSession(handle, EndReason.CAPTURED)
                onNumberCaptured(number)
            },
            // Fires when the recognizer stopped by itself — permission loss or mic-busy after
            // retries. The capture path never reaches here, so the two never race.
            onStopped = { listenerStopped(handle) }
        )
        activeListener = listener
        // The magician is speaking now: no magic tones into the open mic until the session closes.
        soundManager.areEffectsSuppressed = true
        scheduleDeadline(handle, timeoutMs)
        if (preempted) {
            // The listener we just replaced destroys its recognizer and releases the ASR token from
            // a main-thread post, so starting immediately would re-acquire that *same* token (the
            // arbiter is idempotent per owner) and then have it released underneath us — leaving a
            // live recognizer that owns nothing. Posts are FIFO, so this runs after that cleanup.
            mainHandler.post {
                if (activeHandle?.id == handle.id) listener.startListening()
            }
        } else {
            listener.startListening()
        }
        Log.d(TAG, "Started ${caller.name} session #${handle.id} (mode=$mode)")
        return handle
    }

    /** Ends [handle]'s session if it is still the live one. */
    fun release(handle: SessionHandle?) {
        handle?.let { closeSession(it, EndReason.CANCELLED) }
    }

    /** Ends whatever session is live — used on activity/service teardown. */
    fun cancelAll() {
        activeHandle?.let { closeSession(it, EndReason.CANCELLED) }
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    /**
     * Ends [handle]'s session when it is still the live one: drops the deadline, stops the listener
     * (which hands the microphone back to [com.magic.calculator.core.audio.MicArbiter] only after
     * destroying the recognizer), then reports [reason] to the caller exactly once.
     */
    private fun closeSession(handle: SessionHandle, reason: EndReason) {
        if (activeHandle?.id != handle.id) return
        val listener = activeListener
        val ended = activeEnded
        activeListener = null
        activeHandle = null
        activeEnded = null
        cancelDeadline()
        _state.value = State.IDLE
        _rms.value = 0f
        soundManager.areEffectsSuppressed = false
        Log.d(TAG, "Session #${handle.id} closed ($reason)")
        // Synchronous stop before the callback: a caller that immediately starts the next session
        // must not find the previous recognizer still holding the mic.
        listener?.stopListening(shouldVibrate = false)
        ended?.invoke(reason)
    }

    private fun listenerStopped(handle: SessionHandle) {
        if (activeHandle?.id != handle.id) return
        val reason = when (lastErrorCode) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> EndReason.PERMISSION_DENIED
            SpeechRecognizer.ERROR_AUDIO -> EndReason.MIC_BUSY
            else -> EndReason.CANCELLED
        }
        if (reason == EndReason.MIC_BUSY || reason == EndReason.PERMISSION_DENIED) {
            hapticManager.micStopFeedback()
        }
        // closeSession forwards `reason` to the caller through the stored callback.
        closeSession(handle, reason)
    }

    private fun scheduleDeadline(handle: SessionHandle, timeoutMs: Long) {
        if (timeoutMs <= 0L) return // Hands-on session (the Test Voice dialog): Stop or capture ends it.
        val runnable = Runnable {
            if (activeHandle?.id != handle.id) return@Runnable
            Log.d(TAG, "Session #${handle.id} timed out after ${timeoutMs}ms of silence")
            hapticManager.micStopFeedback()
            closeSession(handle, EndReason.TIMEOUT)
        }
        activeDeadline = runnable
        mainHandler.postDelayed(runnable, timeoutMs)
    }

    private fun cancelDeadline() {
        activeDeadline?.let { mainHandler.removeCallbacks(it) }
        activeDeadline = null
    }

    companion object {
        private const val TAG = "VoiceCaptureManager"

        /** How long a hands-free session stays open before silence ends it. */
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
