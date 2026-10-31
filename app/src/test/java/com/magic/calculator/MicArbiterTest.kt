package com.magic.calculator

import com.magic.calculator.core.audio.MicArbiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MicArbiterTest
 *
 * The whole point of the arbiter is that exactly one role owns the microphone at a time. If these
 * invariants break, `SpeechRecognizer.ERROR_AUDIO` ("cannot record") comes back.
 *
 * Runs on a fake monotonic clock so the grace window is tested without sleeping.
 */
class MicArbiterTest {

    /** Fake "nanoTime" so grace windows can be advanced deterministically. */
    private var nowNs = 0L

    private var asrToken: MicArbiter.Token? = null

    @Before
    fun setUp() {
        MicArbiter.resetForTests()
        nowNs = 0L
        MicArbiter.setTimeProviderForTests { nowNs }
    }

    @Test
    fun tearDownRestoresRealClock() {
        // Guards against leaking the fake clock into another test class.
        MicArbiter.setTimeProviderForTests(null)
        assertTrue(MicArbiter.isFree())
    }

    @Test
    fun testFirstAcquirerGetsToken() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.DIAG_METER)
        assertNotNull("A free mic must be acquirable", token)
        assertEquals(MicArbiter.Owner.DIAG_METER, token?.owner)
        assertEquals(MicArbiter.Owner.DIAG_METER, MicArbiter.peekOwner())
        assertTrue(MicArbiter.ownerFlow.value == MicArbiter.Owner.DIAG_METER)
    }

    @Test
    fun testSecondOwnerIsRejectedWhileFirstHolds() {
        MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        val blocked = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        assertNull("Two roles must never own the mic at once", blocked)
        assertEquals(MicArbiter.Owner.CLAP_LOOP, MicArbiter.peekOwner())
    }

    @Test
    fun testReAcquireBySameOwnerIsIdempotent() {
        val first = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        val second = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        assertSame("A double-tap must not mint a second token", first, second)
        assertEquals(1, releasedCountAfterSingleRelease())
    }

    /** A single release of an idempotently acquired mic must fully free it exactly once. */
    private fun releasedCountAfterSingleRelease(): Int {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        val released = MicArbiter.release(token, graceMs = 0L)
        return if (released && MicArbiter.isFree()) 1 else 0
    }

    @Test
    fun testHigherPriorityRequestAsksHolderToYield() {
        val clap = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        assertFalse(clap!!.yieldWanted)

        // Rejected, but it flags the lower-priority holder instead of seizing the stream.
        assertNull(MicArbiter.tryAcquire(MicArbiter.Owner.ASR))
        assertTrue("CLAP_LOOP must be told to step aside for ASR", MicArbiter.shouldYield(MicArbiter.Owner.CLAP_LOOP))

        // Yielding is per-role: an unrelated owner is never asked to give up.
        assertFalse(MicArbiter.shouldYield(MicArbiter.Owner.DIAG_METER))

        // A lower-priority request must not flag a higher-priority holder.
        MicArbiter.release(clap, graceMs = 0L)
        val asr = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        assertNull(MicArbiter.tryAcquire(MicArbiter.Owner.DIAG_METER))
        assertFalse("ASR outranks DIAG_METER and must not be preempted", MicArbiter.shouldYield(MicArbiter.Owner.ASR))
        MicArbiter.release(asr, graceMs = 0L)
    }

    @Test
    fun testReleaseFreesTheMic() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.DIAG_METER)
        assertTrue(MicArbiter.release(token, graceMs = 0L))
        assertNull(MicArbiter.peekOwner())
        assertTrue(MicArbiter.isFree())
        assertNull(MicArbiter.ownerFlow.value)
    }

    @Test
    fun testForeignTokenCannotReleaseCurrentOwner() {
        asrToken = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        val stale = MicArbiter.Token(9999L, MicArbiter.Owner.ASR)

        assertFalse("A stale token must not release a live session", MicArbiter.release(stale, graceMs = 0L))
        assertEquals(MicArbiter.Owner.ASR, MicArbiter.peekOwner())

        assertFalse("A null token must not release a live session", MicArbiter.release(null, graceMs = 0L))
        assertEquals(MicArbiter.Owner.ASR, MicArbiter.peekOwner())

        assertTrue(MicArbiter.release(asrToken, graceMs = 0L))
        assertTrue(MicArbiter.isFree())
    }

    @Test
    fun testDoubleReleaseIsHarmless() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        assertTrue(MicArbiter.release(token, graceMs = 0L))
        assertFalse("Releasing twice must not free the *next* owner", MicArbiter.release(token, graceMs = 0L))
    }

    @Test
    fun testGraceWindowBlocksTheNextOwner() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        assertTrue(MicArbiter.release(token, graceMs = MicArbiter.DEFAULT_GRACE_MS))

        // Released, but the HAL is still tearing the input stream down.
        assertNull(MicArbiter.peekOwner())
        assertFalse("Inside the grace window the mic is not acquirable", MicArbiter.isFree())
        assertEquals(MicArbiter.DEFAULT_GRACE_MS, MicArbiter.remainingGraceMs())
        assertNull(MicArbiter.tryAcquire(MicArbiter.Owner.ASR))

        nowNs += 100L * 1_000_000L
        assertEquals(150L, MicArbiter.remainingGraceMs())
        assertNull(MicArbiter.tryAcquire(MicArbiter.Owner.ASR))

        nowNs += 150L * 1_000_000L
        assertTrue(MicArbiter.isFree())
        assertNotNull("After grace the next owner must get in", MicArbiter.tryAcquire(MicArbiter.Owner.ASR))
    }

    @Test
    fun testZeroGraceAcquiresImmediately() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.DIAG_METER)
        MicArbiter.release(token, graceMs = 0L)
        assertTrue(MicArbiter.isFree())
        assertNotNull(MicArbiter.tryAcquire(MicArbiter.Owner.ASR))
    }

    @Test
    fun testYieldFlagClearedForNewOwner() {
        val clap = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        MicArbiter.tryAcquire(MicArbiter.Owner.ASR) // sets yieldWanted on the clap token
        assertTrue(MicArbiter.shouldYield(MicArbiter.Owner.CLAP_LOOP))

        // The clap loop steps aside; the fresh token must not inherit the yield flag.
        MicArbiter.release(clap, graceMs = 0L)
        assertFalse(MicArbiter.shouldYield(MicArbiter.Owner.CLAP_LOOP))
        val reacquired = MicArbiter.tryAcquire(MicArbiter.Owner.CLAP_LOOP)
        assertNotNull(reacquired)
        assertFalse("A new token starts un-flagged", reacquired!!.yieldWanted)
    }

    @Test
    fun testDescribeMentionsHolderAndFreeState() {
        val token = MicArbiter.tryAcquire(MicArbiter.Owner.ASR)
        assertTrue(MicArbiter.describe().contains("ASR"))
        MicArbiter.release(token, graceMs = 250L)
        assertTrue(MicArbiter.describe().contains("free"))
    }
}
