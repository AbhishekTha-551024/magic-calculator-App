package com.magic.calculator.core.audio

/**
 * MicErrorPolicy
 *
 * How hard to retry when the microphone refuses to open (`SpeechRecognizer.ERROR_AUDIO`, which the
 * speech service reports to the user as "cannot record").
 *
 * The listener used to hammer `scheduleRestart(1000)` forever on every unknown error: an endless
 * 1 Hz loop that keeps the mic contested and never tells the magician anything. This policy backs
 * off geometrically, gives up after [maxConsecutiveErrors] failures, and additionally caps the
 * restart rate at [maxRestartsPerWindow] per [windowMs] so a device whose mic is permanently busy
 * cannot spin in circles.
 *
 * Pure Kotlin with an injectable [clock]: unit-testable on the JVM with no Android dependencies.
 */
class MicErrorPolicy(
    private val baseBackoffMs: Long = 800L,
    private val maxBackoffMs: Long = 8_000L,
    private val maxConsecutiveErrors: Int = 3,
    private val windowMs: Long = 60_000L,
    private val maxRestartsPerWindow: Int = 12,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {

    sealed interface Decision {
        /** Wait [delayMs], then try to open the microphone again. */
        data class RetryIn(val delayMs: Long) : Decision

        /** Stop trying, release the microphone, and tell the user. */
        data object GiveUp : Decision
    }

    var consecutiveErrors: Int = 0
        private set

    private val restarts = ArrayDeque<Long>()

    /** Called for every microphone-open failure. */
    fun onMicError(): Decision {
        val now = clock()
        restarts.removeAll { now - it > windowMs }

        if (restarts.size >= maxRestartsPerWindow) return Decision.GiveUp

        consecutiveErrors++
        if (consecutiveErrors > maxConsecutiveErrors) return Decision.GiveUp

        val delayMs = (baseBackoffMs shl (consecutiveErrors - 1)).coerceAtMost(maxBackoffMs)
        restarts.addLast(now)
        return Decision.RetryIn(delayMs)
    }

    /** Called when the microphone actually opened (`onReadyForSpeech`). */
    fun onMicRecovered() {
        consecutiveErrors = 0
    }

    fun reset() {
        consecutiveErrors = 0
        restarts.clear()
    }
}
