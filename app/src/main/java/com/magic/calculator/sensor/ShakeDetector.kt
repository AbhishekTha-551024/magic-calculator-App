package com.magic.calculator.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects shake gestures via accelerometer.
 *
 * Rules:
 *  - Shake threshold: configurable (default 15 m/s²)
 *  - Min time between shakes: 500ms (debounce)
 *  - Auto-reset after 5 seconds of inactivity
 *  - Fires callback with current shake count (1, 2, or 3)
 *  - Resets internally after triggering at count 3
 */
class ShakeDetector(
    private val onShake: (shakeCount: Int) -> Unit
) : SensorEventListener {

    companion object {
        const val DEFAULT_THRESHOLD = 15.0f   // m/s²
        const val MIN_TIME_BETWEEN_SHAKES = 500L  // ms
        const val RESET_TIMEOUT = 5000L           // ms
        const val MAX_SHAKES = 3
    }

    var threshold: Float = DEFAULT_THRESHOLD

    private var shakeCount = 0
    private var lastShakeTime: Long = 0

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Remove gravity component and compute net acceleration magnitude
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (acceleration > threshold) {
            val now = System.currentTimeMillis()

            // Auto-reset if no shake happened within RESET_TIMEOUT
            if (now - lastShakeTime > RESET_TIMEOUT && shakeCount > 0) {
                shakeCount = 0
            }

            // Debounce: only register a new shake after MIN_TIME_BETWEEN_SHAKES
            if (now - lastShakeTime > MIN_TIME_BETWEEN_SHAKES) {
                shakeCount++
                lastShakeTime = now
                onShake(shakeCount)

                // After 3rd shake triggers the magic, reset for next sequence
                if (shakeCount >= MAX_SHAKES) {
                    shakeCount = 0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /** Manually resets shake progress (e.g., on clear button) */
    fun reset() {
        shakeCount = 0
        lastShakeTime = 0
    }
}