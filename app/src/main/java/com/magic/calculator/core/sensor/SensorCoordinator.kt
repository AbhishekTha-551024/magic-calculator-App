package com.magic.calculator.core.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.magic.calculator.core.audio.MicArbiter
import com.magic.calculator.core.audio.MicPermission
import com.magic.calculator.core.logic.PremiumManager
import com.magic.calculator.core.logic.SettingsBus
import com.magic.calculator.core.logic.SettingsManager
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SensorCoordinator
 *
 * Centralizes all hardware sensor listening, signal processing, and trigger dispatching:
 * - Gyroscope (Nanosecond dt rotational integration for 3x 180Â° wrist rotations & fast spins)
 * - Accelerometer (Tilt, Face-down, Shake detection via ShakeDetector)
 * - Proximity Sensor (Wave detection)
 * - Light Sensor (Shadow detection)
 * - Magnetometer (Magnetic ring / strong field detection)
 * - Microphone AudioRecord (Clap & finger snap monitoring)
 */
class SensorCoordinator(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope,
    private val listener: SensorEventListenerCallback
) : SensorEventListener {

    interface SensorEventListenerCallback {
        fun onMagicTrigger(triggerType: String)
        fun onStepRevealTrigger(triggerType: String)
        fun onLuckyTrigger(value: Int)
        fun onExplosionTrigger(triggerType: String)
        fun onGravityChanged(gx: Float, gy: Float)
        fun onShakeCount(count: Int)
        fun onHapticFeedback(shakeLevel: Int)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val shakeDetector = ShakeDetector { count ->
        listener.onShakeCount(count)
    }.apply {
        threshold = 7.0f
    }

    // â”€â”€ Lucky Magic State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var isLuckyActive: Boolean = false

    // â”€â”€ Digital Flip State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var flipAlreadyPerformed = false
    private var lastGyroTimeNano = 0L
    private var currentFlipAngle = 0f
    private var completedFlipCount = 0
    private var lastFlipCompletedMs = 0L
    private var lastActiveGyroMs = 0L

    companion object {
        private const val FLIP_SPEED_THRESHOLD = 2.5f   // rad/s (~143Â°/s)
        private const val HALF_ROTATION_RAD = 5.8f      // ~332Â° â‰ˆ one full wrist rotation
        private const val FLIP_SEQUENCE_TIMEOUT_MS = 8000L
        private const val FLIP_DEBOUNCE_MS = 600L
        private const val FLIP_STALL_TIMEOUT_MS = 800L
        private const val REQUIRED_FLIPS = 3

        private const val TAG = "SensorCoordinator"

        /** How long the clap loop waits for the mic to free up before giving up. */
        private const val MIC_WAIT_TIMEOUT_MS = 8_000L

        /** Back-off before re-trying after the audio hardware refused to initialise. */
        private const val MIC_RETRY_DELAY_MS = 1_500L

        /** Raw PCM amplitude (0..32767) treated as a clap or finger snap. */
        private const val CLAP_AMPLITUDE_THRESHOLD = 18000

        /**
         * `RecognitionListener.onRmsChanged` level treated as a clap when a voice session owns the
         * mic. NOT interchangeable with [CLAP_AMPLITUDE_THRESHOLD]: the speech service reports a
         * logarithmic dB-ish scale (roughly 0..10 for speech), while the PCM loop compares against
         * a 16-bit sample. Calibrate this on-device by watching the "external clap" debug line.
         */
        private const val CLAP_RMS_THRESHOLD = 7f

        /** Minimum spacing between two clap triggers, on either source. */
        private const val CLAP_DEBOUNCE_MS = 1500L
    }

    // â”€â”€ Audio Trigger State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Mic ownership is granted by MicArbiter so the clap loop can never run at the same
    // time as SpeechRecognizer in StealthVoiceListener. That collision produced
    // SpeechRecognizer.ERROR_AUDIO, which Google reports as "cannot record".
    @Volatile
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null

    /** Written from the clap loop's worker thread, cancelled from the main thread. */
    @Volatile
    private var micWaitJob: Job? = null
    /** Ownership proof; read by the loop's `finally` on its worker thread. */
    @Volatile
    private var micToken: MicArbiter.Token? = null

    /** Read on Main, cleared from the loop's worker thread — must be visible across both. */
    @Volatile
    private var isAudioMonitoring = false
    private var lastExternalClapMs = 0L

    fun resume() {
        lastGyroTimeNano = 0L
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        prox?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        light?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let   { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        startAudioMonitoring()
    }

    fun pause() {
        sensorManager.unregisterListener(this)
        stopAudioMonitoring()
        lastGyroTimeNano = 0L
    }

    fun destroy() {
        pause()
    }

    fun resetFlipState() {
        completedFlipCount = 0
        currentFlipAngle = 0f
        lastFlipCompletedMs = 0L
        lastActiveGyroMs = 0L
        lastGyroTimeNano = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        try {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                shakeDetector.onSensorChanged(event)
            }

            // Lucky Magic overrides other sensor flows
            if (settingsManager.activeMagic == "LUCKY" && isLuckyActive) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY  -> if (event.values[0] < 5.0f) listener.onLuckyTrigger(1)
                    Sensor.TYPE_LIGHT      -> if (event.values[0] < 5)    listener.onLuckyTrigger(5)
                    Sensor.TYPE_ACCELEROMETER -> {
                        shakeDetector.onSensorChanged(event)
                        if (event.values[2] < -8.5) listener.onLuckyTrigger(2)
                    }
                }
                return
            }

            val activeTrigger = settingsManager.activeSensorTrigger
            val activeMagic = settingsManager.activeMagic

            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    if (event.values[0] < 5.0f) {
                        if (settingsManager.isExplosionEnabled && settingsManager.explosionTrigger == "PROXIMITY") {
                            if (PremiumManager.isUserPremium) {
                                listener.onExplosionTrigger("PROXIMITY")
                            } else {
                                settingsManager.isExplosionEnabled = false
                            }
                            return
                        }

                        when {
                            activeMagic == "STEP_REVEAL" -> listener.onStepRevealTrigger("PROXIMITY")
                            activeTrigger == "PROXIMITY"  -> listener.onMagicTrigger("PROXIMITY")
                        }
                    }
                }

                Sensor.TYPE_LIGHT -> {
                    if (event.values[0] < 5) {
                        when {
                            activeMagic == "STEP_REVEAL" -> listener.onStepRevealTrigger("LIGHT")
                            activeTrigger == "LIGHT"      -> listener.onMagicTrigger("LIGHT")
                        }
                    }
                }

                Sensor.TYPE_MAGNETIC_FIELD -> {
                    val strength = sqrt(
                        (event.values[0] * event.values[0] +
                                event.values[1] * event.values[1] +
                                event.values[2] * event.values[2]).toDouble()
                    )
                    if (strength > 120.0) {
                        if (settingsManager.isExplosionEnabled && settingsManager.explosionTrigger == "MAGNETIC") {
                            if (PremiumManager.isUserPremium) {
                                listener.onExplosionTrigger("MAGNETIC")
                            } else {
                                settingsManager.isExplosionEnabled = false
                            }
                            return
                        }

                        when {
                            activeMagic == "STEP_REVEAL" -> listener.onStepRevealTrigger("MAGNETIC")
                            activeTrigger == "MAGNETIC"   -> listener.onMagicTrigger("MAGNETIC")
                        }
                    }
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    listener.onGravityChanged(event.values[0], event.values[1])
                    shakeDetector.onSensorChanged(event)

                if (abs(event.values[0]) > 7) {
                    when {
                        activeMagic == "STEP_REVEAL" -> listener.onStepRevealTrigger("TILT")
                        activeTrigger == "TILT"       -> listener.onMagicTrigger("TILT")
                    }
                }

                val z = event.values[2]
                if (z < -8.5) {
                    when {
                        activeMagic == "STEP_REVEAL" -> listener.onStepRevealTrigger("FACE_DOWN")
                        activeTrigger == "FACE_DOWN"  -> listener.onMagicTrigger("FACE_DOWN")
                    }
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscopeEvent(event, activeTrigger, activeMagic)
            }
        }
    } catch (e: Throwable) {
        com.magic.calculator.core.logic.CrashReportingManager.logNonFatal(
            e,
            "SensorCoordinator onSensorChanged failed on ${event.sensor.name} (type ${event.sensor.type})"
        )
    }
}

    private fun handleGyroscopeEvent(
        event: SensorEvent,
        activeTrigger: String,
        activeMagic: String
    ) {
        val rotZ = event.values[2]   // yaw (steering-wheel spin)

        // Spin trick (fast Z-axis) â€” for GYRO trigger
        if (activeTrigger == "GYRO" && abs(rotZ) > 6f) {
            listener.onMagicTrigger("GYRO")
            return
        }

        // Digital Flip 3-rotation logic
        if (activeTrigger != "ROTATION_3") return

        val currentTimeNano = System.nanoTime()
        val currentTimeMs = System.currentTimeMillis()

        if (lastGyroTimeNano == 0L) {
            lastGyroTimeNano = currentTimeNano
            return
        }

        val dt = (currentTimeNano - lastGyroTimeNano) / 1_000_000_000f
        lastGyroTimeNano = currentTimeNano

        if (dt <= 0f || dt > 0.5f) return

        if (currentTimeMs - lastFlipCompletedMs > FLIP_SEQUENCE_TIMEOUT_MS && completedFlipCount > 0) {
            resetFlipState()
        }

        val spinSpeed = abs(rotZ)
        if (spinSpeed > FLIP_SPEED_THRESHOLD) {
            lastActiveGyroMs = currentTimeMs
        } else {
            if (currentTimeMs - lastActiveGyroMs > FLIP_STALL_TIMEOUT_MS && currentFlipAngle > 0f) {
                currentFlipAngle = 0f
            }
            return
        }

        currentFlipAngle += spinSpeed * dt

        if (currentFlipAngle >= HALF_ROTATION_RAD) {
            val timeSinceLastFlip = currentTimeMs - lastFlipCompletedMs
            if (timeSinceLastFlip > FLIP_DEBOUNCE_MS || completedFlipCount == 0) {
                completedFlipCount++
                lastFlipCompletedMs = currentTimeMs
                currentFlipAngle = 0f

                listener.onHapticFeedback(3)

                if (completedFlipCount >= REQUIRED_FLIPS) {
                    scope.launch(Dispatchers.Main) {
                        delay(150)
                        listener.onMagicTrigger("ROTATION_3")
                        resetFlipState()
                    }
                }
            } else {
                currentFlipAngle = 0f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Opens the clap / snap amplitude loop.
     *
     * The microphone is only taken when CLAP is actually the selected trigger, and only through
     * [MicArbiter]. Before this, `resume()` opened an `AudioRecord` on every `onResume` regardless
     * of settings, which starved Google's speech service of the mic.
     */
    fun startAudioMonitoring() {
        if (isAudioMonitoring) return
        cancelMicWait()

        if (!MicPermission.has(context)) return

        val trigger = settingsManager.activeSensorTrigger
        if (trigger != "CLAP") {
            Log.d(TAG, "Mic not opened: activeSensorTrigger=$trigger")
            return
        }

        val token = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        if (token == null) {
            Log.d(TAG, "Mic busy (${MicArbiter.describe()}); waiting instead of fighting for it")
            micWaitJob = scope.launch(Dispatchers.Default) {
                val deferred = MicArbiter.awaitAcquire(MicArbiter.Owner.CLAP_LOOP, MIC_WAIT_TIMEOUT_MS)
                when {
                    deferred == null -> Log.d(TAG, "Gave up waiting for the mic after ${MIC_WAIT_TIMEOUT_MS}ms")
                    !isActive || settingsManager.activeSensorTrigger != "CLAP" ->
                        MicArbiter.release(deferred)
                    else -> withContext(Dispatchers.Main) { openClapLoop(deferred) }
                }
            }
            return
        }

        openClapLoop(token)
    }

    fun stopAudioMonitoring() {
        isAudioMonitoring = false
        cancelMicWait()
        audioJob?.cancel()
        audioJob = null
        // Released here, synchronously, rather than in the coroutine's finally block: the next
        // owner (usually ASR) must never race a half-dead input stream.
        releaseMicHardware()
    }

    /**
     * Clap detection fed by someone else's microphone — normally the speech recognizer inside
     * [com.magic.calculator.feature.voice_wallpaper.data.voice.VoiceCaptureManager].
     *
     * While ASR owns the mic this loop must not open an `AudioRecord`, so the recognizer's
     * `onRmsChanged` levels are handed here instead. Ignored while [startAudioMonitoring] already
     * has the mic, because that loop fires the trigger itself and the two would double-dispatch.
     *
     * @param level the raw value from `RecognitionListener.onRmsChanged` (see [CLAP_RMS_THRESHOLD]).
     */
    fun onExternalAudioLevel(level: Float) {
        if (isAudioMonitoring) return
        if (!SettingsBus.isClapTrigger()) return
        if (level < CLAP_RMS_THRESHOLD) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastExternalClapMs < CLAP_DEBOUNCE_MS) return
        lastExternalClapMs = now
        Log.d(TAG, "Clap via shared mic: level=$level")
        scope.launch(Dispatchers.Main) { listener.onMagicTrigger("CLAP") }
    }

    private fun openClapLoop(token: MicArbiter.Token) {
        if (isAudioMonitoring) {
            MicArbiter.release(token)
            return
        }
        micToken = token
        isAudioMonitoring = true
        audioJob?.cancel()
        audioJob = scope.launch(Dispatchers.Default) {
            var record: AudioRecord? = null
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (bufferSize <= 0) {
                    Log.e(TAG, "No usable AudioRecord buffer size ($bufferSize); clap trigger off")
                    return@launch
                }
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    // Android denies a second concurrent capture: something else still owns the mic.
                    // The finally block hands the token back, then we try again on a delay instead
                    // of leaving the clap trigger dead until the next onResume.
                    Log.w(TAG, "AudioRecord would not initialise — mic is held elsewhere, retrying")
                    scheduleMicRetry()
                    return@launch
                }
                audioRecord = record
                val buffer = ShortArray(bufferSize)
                record.startRecording()
                while (isAudioMonitoring && isActive && audioRecord === record) {
                    // Stop early if the magician switched to a non-mic trigger.
                    if (settingsManager.activeSensorTrigger != "CLAP") {
                        Log.d(TAG, "Trigger changed to ${settingsManager.activeSensorTrigger}; releasing mic")
                        break
                    }
                    // Stop early if speech recognition needs the mic — ASR outranks this loop.
                    if (MicArbiter.shouldYield(MicArbiter.Owner.CLAP_LOOP)) {
                        Log.d(TAG, "Yielding mic to a higher-priority owner (ASR)")
                        break
                    }
                    val read = record.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        var max = 0
                        for (i in 0 until read) {
                            val value = abs(buffer[i].toInt())
                            if (value > max) max = value
                        }
                        if (max > CLAP_AMPLITUDE_THRESHOLD) {
                            withContext(Dispatchers.Main) {
                                listener.onMagicTrigger("CLAP")
                            }
                            delay(1500)
                        }
                    }
                    delay(100)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Clap monitoring stopped: ${e.message}")
            } finally {
                // Only tear down if this loop is still the recognised owner. A cancelled loop can
                // resume late (blocking `read()`, or the clap dispatch on Main) and land here after
                // a newer loop already holds a different token and record — releasing *those* would
                // leave the mic unowned while a live AudioRecord keeps recording.
                if (micToken === token) releaseMicHardware()
            }
        }
    }

    private fun releaseMicHardware() {
        isAudioMonitoring = false
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try { record.stop() } catch (_: Exception) {}
            try { record.release() } catch (_: Exception) {}
        }
        val token = micToken
        micToken = null
        if (token != null) MicArbiter.release(token)
    }

    /**
     * Re-arms the clap loop after `AudioRecord` refused to initialise.
     *
     * Without this the trigger stays dead until the next `onResume`, which on a phone whose mic is
     * briefly held by the speech service means the magician's clap does nothing. It reuses the
     * [micWaitJob] slot and runs on Main, so [stopAudioMonitoring] always cancels it cleanly.
     */
    private fun scheduleMicRetry() {
        scope.launch(Dispatchers.Main) {
            micWaitJob = coroutineContext[Job]
            delay(MIC_RETRY_DELAY_MS)
            if (isActive && settingsManager.activeSensorTrigger == "CLAP") startAudioMonitoring()
        }
    }

    private fun cancelMicWait() {
        micWaitJob?.cancel()
        micWaitJob = null
    }
}
