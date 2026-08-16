package com.magic.calculator.sensor

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
import androidx.core.content.ContextCompat
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.logic.SettingsManager
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * SensorCoordinator
 *
 * Centralizes all hardware sensor listening, signal processing, and trigger dispatching:
 * - Gyroscope (Nanosecond dt rotational integration for 3x 180° wrist rotations & fast spins)
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

    // ── Lucky Magic State ───────────────────────────────────────────────────
    var isLuckyActive: Boolean = false

    // ── Digital Flip State ──────────────────────────────────────────────────
    var flipAlreadyPerformed = false
    private var lastGyroTimeNano = 0L
    private var currentFlipAngle = 0f
    private var completedFlipCount = 0
    private var lastFlipCompletedMs = 0L
    private var lastActiveGyroMs = 0L

    companion object {
        private const val FLIP_SPEED_THRESHOLD = 2.5f   // rad/s (~143°/s)
        private const val HALF_ROTATION_RAD = 5.8f      // ~332° ≈ one full wrist rotation
        private const val FLIP_SEQUENCE_TIMEOUT_MS = 8000L
        private const val FLIP_DEBOUNCE_MS = 600L
        private const val FLIP_STALL_TIMEOUT_MS = 800L
        private const val REQUIRED_FLIPS = 3
    }

    // ── Audio Trigger State ─────────────────────────────────────────────────
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var isAudioMonitoring = false

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
                if (strength > 120) {
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
    }

    private fun handleGyroscopeEvent(
        event: SensorEvent,
        activeTrigger: String,
        activeMagic: String
    ) {
        val rotZ = event.values[2]   // yaw (steering-wheel spin)

        // Spin trick (fast Z-axis) — for GYRO trigger
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

    fun startAudioMonitoring() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) return
        isAudioMonitoring = true
        audioJob?.cancel()
        audioJob = scope.launch(Dispatchers.Default) {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
                audioRecord = record
                val buffer = ShortArray(bufferSize)
                record.startRecording()
                while (isAudioMonitoring && isActive) {
                    val read = record.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        var max = 0
                        for (i in 0 until read) {
                            val value = abs(buffer[i].toInt())
                            if (value > max) max = value
                        }
                        if (settingsManager.activeSensorTrigger == "CLAP" && max > 18000) {
                            withContext(Dispatchers.Main) {
                                listener.onMagicTrigger("CLAP")
                            }
                            delay(1500)
                        }
                    }
                    delay(100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                audioRecord = null
            }
        }
    }

    fun stopAudioMonitoring() {
        isAudioMonitoring = false
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
