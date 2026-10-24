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
import android.database.ContentObserver
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
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.magic.calculator.R
import com.magic.calculator.core.haptic.HapticManager
import com.magic.calculator.core.logic.SettingsManager
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

    private var voiceListener: StealthVoiceListener? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsManager: SettingsManager
    private val hapticManager by lazy { HapticManager(this) }
    private var mediaSession: MediaSession? = null
    private var volumeObserver: ContentObserver? = null

    private var isRecordingSession = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionTimeoutRunnable = Runnable {
        if (isRecordingSession) {
            Log.d(TAG, "Recording session timeout after silence while locked")
            isRecordingSession = false
            hapticManager.micStopFeedback()
            stopVoiceListening()
        }
    }

    companion object {
        private const val TAG = "StealthVoiceService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "stealth_voice_service_channel"

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
        settingsManager = SettingsManager(this)
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

        // Guard: only run if wallpaper magic is enabled
        val isWallpaperEnabled = settingsManager.isWallpaperMagicEnabled || settingsManager.isVoiceListenerEnabled
        val activeMagic = settingsManager.activeMagic
        val otherMagicActive = activeMagic != "WALLPAPER_MAGIC" && activeMagic != "NONE" && !isWallpaperEnabled
        if (!isWallpaperEnabled || otherMagicActive) {
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
        setupVolumeObserver()
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
                            val newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                            val prevVol = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
                            Log.d(TAG, "VOLUME_CHANGED_ACTION: prev=$prevVol, new=$newVol")
                            if (newVol != -1 && prevVol != -1 && newVol < prevVol) {
                                Log.d(TAG, "Volume Down detected via VOLUME_CHANGED_ACTION broadcast")
                                mainHandler.post {
                                    onVolumeDownLockedTrigger()
                                }
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        volumeChangeReceiver,
                        IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    registerReceiver(
                        volumeChangeReceiver,
                        IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                    )
                }
                Log.d(TAG, "VOLUME_CHANGED_ACTION broadcast receiver registered with RECEIVER_EXPORTED")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register volume broadcast receiver: ${e.message}", e)
        }
    }

    private fun setupVolumeObserver() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            var lastMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            var lastRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            var lastNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeObserver = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    val currentMusic = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val currentRing = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                    val currentNotif = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    if (currentMusic < lastMusic || currentRing < lastRing || currentNotif < lastNotif) {
                        Log.d(TAG, "Volume decreased via ContentObserver: music($currentMusic<$lastMusic) or ring($currentRing<$lastRing)")
                        onVolumeDownLockedTrigger()
                        // Restore music volume so it doesn't decrease to 0 over multiple performances
                        if (currentMusic < lastMusic) {
                            try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, lastMusic, 0) } catch (_: Exception) {}
                        }
                    }
                    lastMusic = currentMusic
                    lastRing = currentRing
                    lastNotif = currentNotif
                }
            }
            contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                volumeObserver!!
            )
            Log.d(TAG, "Volume ContentObserver registered for locked screen monitoring")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register volume ContentObserver: ${e.message}", e)
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
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeObserver = null
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering VolumeObserver: ${e.message}")
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
        if (now - lastTriggerTime < 1200L) {
            return // Debounce duplicate events from multiple triggers
        }
        lastTriggerTime = now

        if (isRecordingSession) {
            // Already recording -> Magician clicked Volume Down again to CANCEL / STOP!
            Log.d(TAG, "Volume Down pressed while recording -> Cancelling")
            isRecordingSession = false
            mainHandler.removeCallbacks(sessionTimeoutRunnable)
            hapticManager.micStopFeedback() // Single buzz: STOPPED
            stopVoiceListening()
            return
        }

        // Not recording -> Magician clicked Volume Down to START recording session!
        Log.d(TAG, "Volume Down pressed while locked -> STARTING voice capture session")
        isRecordingSession = true
        hapticManager.micStartFeedback() // Double pulse: CHALU HUA (BUZZ - BUZZ)

        mainHandler.removeCallbacks(sessionTimeoutRunnable)
        mainHandler.postDelayed(sessionTimeoutRunnable, 8000)

        startVoiceListeningSession()
    }

    private fun startVoiceListeningSession() {
        val activeMode = settingsManager.wallpaperContentType
        voiceListener?.stopListening(shouldVibrate = false)
        voiceListener = StealthVoiceListener(
            context = this,
            mode = activeMode,
            onErrorDetail = { err, code ->
                mainHandler.post {
                    Log.w(TAG, "Voice error while locked: $err ($code)")
                    if (isRecordingSession) {
                        isRecordingSession = false
                        mainHandler.removeCallbacks(sessionTimeoutRunnable)
                        hapticManager.micStopFeedback()
                        stopVoiceListening()
                    }
                }
            },
            onCardCaptured = { card ->
                mainHandler.post {
                    isRecordingSession = false
                    mainHandler.removeCallbacks(sessionTimeoutRunnable)
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
                    stopVoiceListening()
                }
            },
            onNumberCaptured = { capturedNumber ->
                mainHandler.post {
                    isRecordingSession = false
                    mainHandler.removeCallbacks(sessionTimeoutRunnable)
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
                    stopVoiceListening()
                }
            }
        )
        voiceListener?.startListening()
    }

    private fun stopVoiceListening() {
        voiceListener?.stopListening(shouldVibrate = false)
        voiceListener = null
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
