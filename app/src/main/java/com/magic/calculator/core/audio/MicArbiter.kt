package com.magic.calculator.core.audio

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * MicArbiter
 *
 * Process-wide, single-owner gate for microphone **capture** hardware.
 *
 * Android only allows one capture client at a time across apps. This app had three of them
 * competing with each other and with Google's speech service (which runs in a different UID):
 * the Sensor Lab meter in `SecretActivity`, the clap loop in `SensorCoordinator`, and
 * `SpeechRecognizer` in `StealthVoiceListener`. The loser got
 * `SpeechRecognizer.ERROR_AUDIO` ("cannot record"), which the previous retry logic re-attempted
 * at 1 Hz forever.
 *
 * Rules:
 *  - A caller must hold a [Token] before it opens `AudioRecord` or `SpeechRecognizer`.
 *  - There is no forced preemption. A higher-priority request sets a yield flag that the current
 *    holder polls via [shouldYield] and acts on, or it waits on [ownerFlow].
 *  - [tryAcquire] is idempotent per [Owner], so a double-tap on a trigger can never leak a
 *    second token for the same role.
 *  - [release] applies a short grace window so the audio HAL has finished tearing down the
 *    previous input stream before the next owner opens one.
 *
 * Deliberately holds no `Context` and no `CoroutineScope`, so the state machine is testable
 * with plain JUnit (see `MicArbiterTest`).
 */
object MicArbiter {

    /** Roles that can own the microphone, highest [priority] first. */
    enum class Owner(val priority: Int) {
        /** Speech recognition — the most fragile client, it lives in another process. */
        ASR(30),

        /** Continuous clap / finger-snap amplitude loop. */
        CLAP_LOOP(20),

        /** SecretActivity "Sensor Lab" mic meter. */
        DIAG_METER(10);
    }

    /** Opaque proof of ownership. Only [tryAcquire] / [awaitAcquire] can mint one. */
    class Token internal constructor(val id: Long, val owner: Owner) {
        /** Set when a higher-priority role is queueing up; the holder should release soon. */
        @Volatile
        internal var yieldWanted: Boolean = false

        override fun toString(): String = "Token($owner #$id)"
    }

    /** Default time the HAL is given to release an input stream before the next acquire. */
    const val DEFAULT_GRACE_MS = 250L

    private const val CONTENTION_RETRY_MS = 50L

    @Volatile
    private var active: Token? = null

    @Volatile
    private var releasedAtNs: Long = 0L

    @Volatile
    private var graceNs: Long = 0L

    private var nextId: Long = 1L

    private val _ownerFlow = MutableStateFlow<Owner?>(null)

    /** Current owner, or `null` when the microphone is free (possibly still in grace). */
    val ownerFlow: StateFlow<Owner?> = _ownerFlow.asStateFlow()

    /** Monotonic clock, swappable in tests. */
    @VisibleForTesting
    internal var timeProvider: () -> Long = { System.nanoTime() }

    /** The role currently holding the mic, or `null`. */
    @Synchronized
    fun peekOwner(): Owner? = active?.owner

    /** True only when nobody owns the mic *and* the release grace window has elapsed. */
    fun isFree(): Boolean = active == null && remainingGraceMs() == 0L

    /** Milliseconds left before the mic becomes acquirable again after a release. */
    @Synchronized
    fun remainingGraceMs(): Long = remainingGraceMsLocked()

    /**
     * Attempts to take ownership for [owner].
     *
     * @return the token to pass to [release], or `null` when another role owns the mic or the
     *   previous owner's grace window has not elapsed. When the same [owner] already holds the
     *   mic, its existing token is returned again instead of failing.
     */
    @Synchronized
    fun tryAcquire(owner: Owner): Token? {
        active?.let { current ->
            if (current.owner == owner) return current
            // Flag a lower-priority holder so it hands the mic over on its next poll. Nothing is
            // seized here — forcibly tearing down someone else's live AudioRecord is exactly the
            // kind of race that produced the original "cannot record" errors.
            if (owner.priority > current.owner.priority) current.yieldWanted = true
            return null
        }
        if (remainingGraceMsLocked() > 0L) return null

        val token = Token(nextId++, owner)
        active = token
        graceNs = 0L
        _ownerFlow.value = owner
        return token
    }

    /**
     * True when [owner] holds the mic but a higher-priority role is queueing for it.
     *
     * Holders poll this inside their read loop and call [release] themselves — that is how the
     * clap loop and the mic meter get out of the way of speech recognition within ~100ms.
     */
    @Synchronized
    fun shouldYield(owner: Owner): Boolean =
        active?.let { it.owner == owner && it.yieldWanted } ?: false

    /**
     * Hands ownership back.
     *
     * @param token the token returned by [tryAcquire]; a stale or foreign token is rejected so a
     *   late `onPause` cannot release a mic that a newer session already took.
     * @return `true` when this call actually freed the mic.
     */
    @Synchronized
    fun release(token: Token?, graceMs: Long = DEFAULT_GRACE_MS): Boolean {
        val current = active ?: return false
        if (token == null || token.id != current.id) return false

        active = null
        releasedAtNs = timeProvider()
        graceNs = TimeUnit.MILLISECONDS.toNanos(graceMs.coerceAtLeast(0L))
        _ownerFlow.value = null
        return true
    }

    /**
     * Suspends until [owner] can be acquired or [timeoutMs] elapses.
     *
     * Suspend-friendly replacement for retry loops: callers pass their own scope, so this object
     * stays scope-free.
     */
    suspend fun awaitAcquire(owner: Owner, timeoutMs: Long): Token? =
        withTimeoutOrNull(timeoutMs) {
            var token: Token? = null
            while (token == null) {
                if (ownerFlow.value != null) {
                    ownerFlow.first { it == null }
                }
                val grace = remainingGraceMs()
                if (grace > 0) delay(grace)
                token = tryAcquire(owner)
                // Another claimant beat us to it: back off a little so we do not spin.
                if (token == null && ownerFlow.value == null) delay(CONTENTION_RETRY_MS)
            }
            token
        }

    /** One-line description for logcat / MagicToast diagnostics. */
    fun describe(): String =
        active?.let { "held by ${it.owner} (#${it.id})" } ?: "free (${remainingGraceMs()}ms grace left)"

    @VisibleForTesting
    fun resetForTests() {
        active = null
        graceNs = 0L
        releasedAtNs = 0L
        _ownerFlow.value = null
    }

    @VisibleForTesting
    fun setTimeProviderForTests(provider: (() -> Long)?) {
        timeProvider = provider ?: { System.nanoTime() }
    }

    private fun remainingGraceMsLocked(): Long {
        val elapsed = timeProvider() - releasedAtNs
        val remaining = graceNs - elapsed
        return if (remaining > 0) TimeUnit.NANOSECONDS.toMillis(remaining) else 0L
    }
}
