package com.magic.calculator

import com.magic.calculator.core.logic.VoiceFeaturePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VoiceFeaturePolicyTest
 *
 * The predicate that decides whether `StealthVoiceService` may run used to be pasted into two
 * places. These cases pin the truth table so a service can never be left holding the microphone
 * for a magic the magician has already switched away from.
 */
class VoiceFeaturePolicyTest {

    private fun shouldRun(wallpaper: Boolean, listener: Boolean, magic: String) =
        VoiceFeaturePolicy.shouldRun(wallpaper, listener, magic)

    @Test
    fun testWallpaperSwitchOnAloneRuns() {
        assertTrue(shouldRun(wallpaper = true, listener = false, magic = "NONE"))
    }

    @Test
    fun testVoiceListenerSwitchOnAloneRuns() {
        assertTrue(shouldRun(wallpaper = false, listener = true, magic = "NONE"))
    }

    @Test
    fun testWallpaperMagicActiveRuns() {
        assertTrue(shouldRun(wallpaper = true, listener = false, magic = VoiceFeaturePolicy.WALLPAPER_MAGIC))
    }

    @Test
    fun testBothSwitchesOffNeverRuns() {
        assertFalse(shouldRun(wallpaper = false, listener = false, magic = "NONE"))
        assertFalse(shouldRun(wallpaper = false, listener = false, magic = VoiceFeaturePolicy.WALLPAPER_MAGIC))
    }

    @Test
    fun testOtherMagicWithSwitchesOffIsRejected() {
        // This is the drift case: the magician moved to a different trick, so the voice service
        // has no business staying alive and keeping the mic claimed.
        assertFalse(shouldRun(wallpaper = false, listener = false, magic = "PHONE_NUMBER_FORCE"))
    }

    @Test
    fun testEnabledSwitchSurvivesAnotherActiveMagic() {
        // Preserves the pre-refactor behaviour: an explicit switch wins over the active-magic name,
        // so the hand-off from MainActivity to the service is not silently dropped.
        assertTrue(shouldRun(wallpaper = true, listener = false, magic = "DIGIT_FLIP"))
        assertTrue(shouldRun(wallpaper = false, listener = true, magic = "DIGIT_FLIP"))
    }

    @Test
    fun testEmptyActiveMagicIsTreatedAsNoMagic() {
        // "" is not a *different* magic, so it must not disqualify an enabled feature.
        assertTrue(shouldRun(wallpaper = true, listener = false, magic = ""))
        assertFalse(shouldRun(wallpaper = false, listener = false, magic = ""))
    }
}
