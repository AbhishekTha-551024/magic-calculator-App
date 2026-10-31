package com.magic.calculator.feature.voice_wallpaper.data.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.magic.calculator.R
import com.magic.calculator.core.haptic.HapticManager
import com.magic.calculator.core.logic.SettingsManager
import com.magic.calculator.core.logic.VoiceFeaturePolicy
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.voice_wallpaper.data.wallpaper.WallpaperMagicManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * StealthVoiceService
 *
 * Keeps a MediaSession and VolumeObserver in standby mode when the app is backgrounded / screen is locked.
 * The microphone is OFF by default.
 * When the magician presses Volume Down while the phone is locked:
 *  1. Double-pulse haptic (BUZZ-BUZZ) confirms mic is recording.
 *  2. Listens for speech.
 *  3. Once captured, triple-wave haptic confirms (BUZZ-BUZZ-BUUUZZZZZ), wallpaper is set to lock screen, and mic turns off.
 *  4. Magician can press Volume Down again to repeat!
 */
class StealthVoiceService : Service() {

    /**
     * Voice capture is delegated to the process-wide [VoiceCaptureManager] so the locked-screen
     * path, the calculator's own volume-down path, and the Wallpaper & Voice Force test dialog can
     * never hold two `SpeechRecognizer` clients at once.
     */
    private val captureManager by lazy { appContainer.voiceCaptureManager }
    private var lockedSession: VoiceCaptureManager.SessionHandle? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Shared with the rest of the process — a service-local copy would drift from [SettingsBus]. */
    private val settingsManager: SettingsManager by lazy { appContainer.settingsManager }

    /** Same instance the Activity uses, so haptic settings changes apply here too. */
    private val hapticManager: HapticManager by lazy { appContainer.hapticManager }
    private var mediaSession: MediaSession? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "StealthVoiceService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "stealth_voice_service_channel"

        /**
         * Collapse window for duplicate broadcasts of one physical key press.
         *
         * Deliberately short: the ContentObserver that used to double-report is gone, and a magician
         * who taps volume down twice in quick succession (start capture, then stop it) must have the
         * second tap honoured.
         */
        private const val TRIGGER_DEBOUNCE_MS = 350L

        const val ACTION_START = "com.magic.calculator.voice.ACTION_START"
        const val ACTION_STOP = "com.magic.calculator.voice.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, StealthVoiceService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start StealthVoiceService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StealthVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop StealthVoiceService: ${e.message}", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action=$action")

        if (action == ACTION_STOP) {
            cleanupVolumeTriggers()
            stopVoiceListening()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Guard: only run if wallpaper magic is enabled (single predicate, see VoiceFeaturePolicy)
        if (!VoiceFeaturePolicy.shouldRun(settingsManager)) {
            Log.d(TAG, "Stopping StealthVoiceService: not enabled or different magic active")
            cleanupVolumeTriggers()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }

        // Setup Volume Down listener while in background / locked (mic is OFF until pressed!)
        startSilentAudio()
        setupMediaSession()
        setupVolumeBroadcastReceiver()

        return START_NOT_STICKY
    }

    private var silentAudioTrack: AudioTrack? = null
    private var isPlayingSilence = false
    private var silenceThread: Thread? = null

    /**
     * Plays digital zero silence in the background.
     * This grants our MediaSession active media playback status across Android OS,
     * routing hardware volume keys directly to STREAM_MUSIC even when the screen is locked/off!
     */
    private fun startSilentAudio() {
        if (isPlayingSilence) return
        isPlayingSilence = true
        silenceThread = Thread {
            try {
                val sampleRate = 8000
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(1024)

                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                silentAudioTrack = audioTrack
                audioTrack.play()
                val silenceBuffer = ByteArray(bufferSize) // Pure digital silence

                while (isPlayingSilence) {
                    audioTrack.write(silenceBuffer, 0, silenceBuffer.size)
                    Thread.sleep(200)
                }
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                Log.w(TAG, "Silent audio thread exception: ${e.message}")
            }
        }.apply {
            name = "SilentMediaAudioThread"
            isDaemon = true
            start()
        }
    }

    private fun stopSilentAudio() {
        isPlayingSilence = false
        silenceThread?.interrupt()
        silenceThread = null
        try {
            silentAudioTrack?.stop()
            silentAudioTrack?.release()
        } catch (_: Exception) {}
        silentAudioTrack = null
    }

    private fun setupMediaSession() {
        try {
            mediaSession?.release()
            mediaSession = MediaSession(this, "MagicVoiceSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                        .build()
                )
                // Set playback to LOCAL so Android routes physical volume keys to STREAM_MUSIC!
                setPlaybackToLocal(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isActive = true
                Log.d(TAG, "MediaSession active for locked volume button listening")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup MediaSession: ${e.message}", e)
        }
    }

    private var volumeChangeReceiver: BroadcastReceiver? = null

    private fun setupVolumeBroadcastReceiver() {
        try {
            if (volumeChangeReceiver == null) {
                volumeChangeReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                            val stream = intent.getIntExtra(
                                "android.media.EXTRA_VOLUME_STREAM_TYPE", -1
                            )
                            val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                            val prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
                            Log.d(TAG, "VOLUME_CHANGED_ACTION: stream=$stream prev=$prevVol new=$newVol")

                            // Only the media stream counts as the magician's cue. Our own
                            // beep-muting touches STREAM_NOTIFICATION / STREAM_SYSTEM, and
                            // accepting those used to start a second capture session from
                            // inside the first one — a self-sustaining trigger loop.
                            if (stream != AudioManager.STREAM_MUSIC) return

                            if (captureManager.isInsideSelfDuckWindow()) {
                                Log.d(TAG, "Ignoring volume change inside self-duck window")
                                return
                            }

                            if (newVol != -1 && prevVol != -1 && newVol < prevVol) {
                                Log.d(TAG, "Volume Down detected via VOLUME_CHANGED_ACTION broadcast")

                                // Put media volume back where the magician found it: the cue is the key
                                // press, not the level, and without this the stream walks to zero over a
                                // set. The restore is an *increase*, so it never re-enters this branch.
                                try {
                                    (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, prevVol,
                                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                                    )
                                } catch (_: Exception) {}

                                mainHandler.post {
                                    onVolumeDownLockedTrigger()
                                }
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // VOLUME_CHANGED_ACTION is a protected system broadcast: nothing else can send
                    // it, so an exported receiver only widens the app's attack surface.
                    registerReceiver(
                        volumeChangeReceiver,
                        IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    registerReceiver(
                        volumeChangeReceiver,
                        IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                    )
                }
                Log.d(TAG, "VOLUME_CHANGED_ACTION broadcast receiver registered with RECEIVER_NOT_EXPORTED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register volume broadcast receiver: ${e.message}", e)
        }
    }

    private fun cleanupVolumeTriggers() {
        stopSilentAudio()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaSession: ${e.message}")
        }
        try {
            volumeChangeReceiver?.let { unregisterReceiver(it) }
            volumeChangeReceiver = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering volume broadcast receiver: ${e.message}")
        }
    }

    private var lastTriggerTime = 0L

    private fun onVolumeDownLockedTrigger() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerTime < TRIGGER_DEBOUNCE_MS) {
            return // Debounce duplicate events from multiple triggers
        }
        lastTriggerTime = now

        val session = lockedSession
        if (session != null) {
            // Already recording -> Magician clicked Volume Down again to CANCEL / STOP!
            Log.d(TAG, "Volume Down pressed while recording -> Cancelling")
            lockedSession = null
            hapticManager.micStopFeedback() // Single buzz: STOPPED
            captureManager.release(session)
            return
        }

        // Not recording -> Magician clicked Volume Down to START recording session!
        startVoiceListeningSession()
    }

    private fun startVoiceListeningSession() {
        val activeMode = settingsManager.wallpaperContentType
        Log.d(TAG, "Volume Down pressed while locked -> STARTING voice capture session")
        hapticManager.micStartFeedback() // Double pulse: CHALU HUA (BUZZ - BUZZ)

        val handle = captureManager.requestSession(
            caller = VoiceCaptureManager.Caller.LOCKED_VOLUME,
            mode = activeMode,
            timeoutMs = VoiceCaptureManager.DEFAULT_TIMEOUT_MS,
            onStateChanged = { state ->
                Log.d(TAG, "Locked voice state ($activeMode): $state")
            },
            onCardCaptured = { card ->
                mainHandler.post {
                    Log.d(TAG, "🃏 Captured card while locked: ${card.englishName}")

                    settingsManager.forcedWallpaperCard = card.code
                    if (settingsManager.isWallpaperMagicEnabled) {
                        serviceScope.launch {
                            val target = when (settingsManager.wallpaperMagicTarget) {
                                1 -> WallpaperMagicManager.Target.HOME
                                2 -> WallpaperMagicManager.Target.LOCK
                                else -> WallpaperMagicManager.Target.BOTH
                            }
                            WallpaperMagicManager.applyCardWallpaper(
                                this@StealthVoiceService,
                                card.code,
                                settingsManager.wallpaperStyleIndex,
                                target
                            )
                        }
                    }
                }
            },
            onNumberCaptured = { capturedNumber ->
                mainHandler.post {
                    Log.d(TAG, "🎯 Captured number while locked: $capturedNumber")

                    settingsManager.wallpaperContentType = "NUMBER"
                    settingsManager.forcedWallpaperNumber = capturedNumber

                    if (settingsManager.isWallpaperMagicEnabled) {
                        serviceScope.launch {
                            val target = when (settingsManager.wallpaperMagicTarget) {
                                1 -> WallpaperMagicManager.Target.HOME
                                2 -> WallpaperMagicManager.Target.LOCK
                                else -> WallpaperMagicManager.Target.BOTH
                            }
                            WallpaperMagicManager.applyNumberWallpaper(
                                this@StealthVoiceService,
                                capturedNumber,
                                settingsManager.wallpaperStyleIndex,
                                target
                            )
                        }
                    }
                }
            },
            onEnded = { reason ->
                // The manager already hands the mic back and buzzes for terminal errors, so this
                // only resets the service's own state. ERROR_NO_MATCH never reaches here any more:
                // silence keeps listening until the timeout, exactly as the magician expects.
                // Cleared for every reason, REPLACED included: a replaced handle is dead, and
                // keeping it turned the magician's next volume-down into a no-op "stop" press.
                lockedSession = null
                Log.d(TAG, "Locked voice session ended: $reason")
            }
        )

        if (handle == null) {
            lockedSession = null
            hapticManager.micStopFeedback()
        } else {
            lockedSession = handle
        }
    }

    private fun stopVoiceListening() {
        lockedSession?.let { captureManager.release(it) }
        lockedSession = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Calculator System Services",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background calculation services"
                setShowBadge(false)
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_icon_calculator)
            .setContentTitle("Calculator Active")
            .setContentText("Background memory running")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupVolumeTriggers()
        stopVoiceListening()
        serviceScope.cancel()
        Log.d(TAG, "StealthVoiceService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
