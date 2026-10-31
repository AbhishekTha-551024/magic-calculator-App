package com.magic.calculator

import com.magic.calculator.core.audio.MicErrorPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MicErrorPolicyTest
 *
 * Locks in the behaviour that replaced the old endless 1 Hz restart hammer: back off geometrically,
 * give up after three consecutive failures, and cap the restart rate per minute.
 */
class MicErrorPolicyTest {

    @Test
    fun testBackoffGrowsGeometrically() {
        val policy = MicErrorPolicy()
        assertEquals(800L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(1_600L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(3_200L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(3, policy.consecutiveErrors)
    }

    @Test
    fun testGivesUpAfterMaxConsecutiveErrors() {
        val policy = MicErrorPolicy(maxConsecutiveErrors = 3)
        repeat(3) { assertTrue(policy.onMicError() is MicErrorPolicy.Decision.RetryIn) }
        assertEquals(
            "A fourth failure must stop the loop and surface the mic-busy message",
            MicErrorPolicy.Decision.GiveUp,
            policy.onMicError()
        )
    }

    @Test
    fun testBackoffIsCappedAtMax() {
        val policy = MicErrorPolicy(baseBackoffMs = 6_000L, maxBackoffMs = 8_000L, maxConsecutiveErrors = 10)
        assertEquals(6_000L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(8_000L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(8_000L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
    }

    @Test
    fun testRecoveryRestartsTheLadder() {
        val policy = MicErrorPolicy()
        policy.onMicError()
        policy.onMicError()
        assertEquals(2, policy.consecutiveErrors)

        policy.onMicRecovered()
        assertEquals(0, policy.consecutiveErrors)
        assertEquals(
            "After a successful capture the next failure starts at the base delay again",
            800L,
            (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs
        )
    }

    @Test
    fun testResetClearsEverything() {
        val policy = MicErrorPolicy()
        policy.onMicError()
        policy.onMicError()
        policy.reset()
        assertEquals(0, policy.consecutiveErrors)
        assertEquals(800L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
    }

    @Test
    fun testRestartRateCapStopsASlowBurn() {
        // A mic that keeps failing but "recovers" between attempts would never trip the
        // consecutive-error limit — the per-window cap is what stops that.
        var now = 0L
        val policy = MicErrorPolicy(
            maxConsecutiveErrors = 100,
            maxRestartsPerWindow = 3,
            windowMs = 60_000L,
            clock = { now }
        )

        repeat(3) {
            assertTrue(policy.onMicError() is MicErrorPolicy.Decision.RetryIn)
            policy.onMicRecovered()
            now += 1_000L
        }

        assertEquals(MicErrorPolicy.Decision.GiveUp, policy.onMicError())
    }

    @Test
    fun testRateCapFreesItselfOnceTheWindowPasses() {
        var now = 0L
        val policy = MicErrorPolicy(
            maxConsecutiveErrors = 100,
            maxRestartsPerWindow = 2,
            windowMs = 10_000L,
            clock = { now }
        )

        assertTrue(policy.onMicError() is MicErrorPolicy.Decision.RetryIn)
        assertTrue(policy.onMicError() is MicErrorPolicy.Decision.RetryIn)
        assertEquals(MicErrorPolicy.Decision.GiveUp, policy.onMicError())

        // Older restarts fall out of the window, so a later session gets its budget back.
        now += 11_000L
        policy.onMicRecovered()
        assertTrue(policy.onMicError() is MicErrorPolicy.Decision.RetryIn)
    }

    @Test
    fun testCustomBaseDelayIsHonoured() {
        val policy = MicErrorPolicy(baseBackoffMs = 250L)
        assertEquals(250L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
        assertEquals(500L, (policy.onMicError() as MicErrorPolicy.Decision.RetryIn).delayMs)
    }
}
