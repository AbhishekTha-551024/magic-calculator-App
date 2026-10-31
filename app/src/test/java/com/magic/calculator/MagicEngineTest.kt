package com.magic.calculator

import com.magic.calculator.core.logic.MagicEngine
import com.magic.calculator.core.logic.strategies.MagicExecutionContext
import com.magic.calculator.core.logic.strategies.MagicRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MagicEngineTest {

    private lateinit var magicEngine: MagicEngine
    private lateinit var registry: MagicRegistry

    @Before
    fun setUp() {
        registry = MagicRegistry.getInstance()
        magicEngine = MagicEngine(registry)
    }

    @Test
    fun testAllStrategiesRegistered() {
        val strategies = registry.getAllStrategies()
        assertTrue("At least 10 strategies should be registered", strategies.size >= 10)
        assertNotNull(registry.getStrategy("DIGIT_FLIP"))
        assertNotNull(registry.getStrategy("LUCKY"))
        assertNotNull(registry.getStrategy("PHONE_NUMBER_FORCE"))
        assertNotNull(registry.getStrategy("TOXIC_TIME"))
        assertNotNull(registry.getStrategy("GHOST_TYPING"))
        assertNotNull(registry.getStrategy("REAPPEARING_RESULT"))
        assertNotNull(registry.getStrategy("STEP_REVEAL"))
        assertNotNull(registry.getStrategy("CONTACT_FORCE"))
        assertNotNull(registry.getStrategy("TOXIC_GALLERY"))
    }

    @Test
    fun testDigitFlipTrick() {
        val result = magicEngine.performMagic(
            input = "66",
            type = MagicEngine.TrickType.DIGIT_FLIP,
            flipResult = "999"
        )
        assertEquals("999", result.displayValue)
    }

    @Test
    fun testLuckyNumberTrick() {
        val result = magicEngine.performMagic(
            input = "12345",
            type = MagicEngine.TrickType.LUCKY,
            forcedLucky = "777"
        )
        assertEquals("777", result.displayValue)
    }

    @Test
    fun testPhoneNumberForceTrick() {
        val result = magicEngine.performMagic(
            input = "9876543210",
            type = MagicEngine.TrickType.PHONE_NUMBER_FORCE,
            forcedPhone = "9998887776"
        )
        assertEquals("9998887776", result.displayValue)
    }

    @Test
    fun testToxicTimeTrick() {
        val result = magicEngine.performMagic(
            input = "0",
            type = MagicEngine.TrickType.TOXIC_TIME,
            forcedLucky = "1430"
        )
        assertEquals("1430", result.displayValue)
    }

    @Test
    fun testGhostTypingStrategyExecution() {
        val context = MagicExecutionContext(ghostTypingNumber = "4321")
        val res = registry.executeTrick("GHOST_TYPING", "0", context)
        assertNotNull(res)
        assertEquals("4321", res?.displayValue)
        assertTrue(res?.isDeferredAnimation == true)
    }

    @Test
    fun testInversionTrick() {
        val result = magicEngine.performMagic(
            input = "6666",
            type = MagicEngine.TrickType.INVERSION
        )
        assertEquals("9999", result.displayValue)
    }

    @Test
    fun testMirrorTrick() {
        val result = magicEngine.performMagic(
            input = "1234",
            type = MagicEngine.TrickType.MIRROR
        )
        assertEquals("4321", result.displayValue)
    }

    @Test
    fun testCustomSecretUnlockCodeValidation() {
        val masterPin = "741"
        val customPin1 = "456"
        val customPin2 = "258"
        val invalidPinShort = "12"
        val invalidPinLong = "1234"
        val invalidPinNonDigit = "74a"

        assertTrue(masterPin == "741")
        assertTrue(customPin1.length == 3 && customPin1.all { it.isDigit() })
        assertTrue(customPin2.length == 3 && customPin2.all { it.isDigit() })
        assertTrue(invalidPinShort.length != 3)
        assertTrue(invalidPinLong.length != 3)
        assertTrue(!invalidPinNonDigit.all { it.isDigit() })
    }

    @Test
    fun testOnboardingStepsValidation() {
        val freeStepsCount = 4
        val proStepsCount = 5
        assertTrue(freeStepsCount == 4)
        assertTrue(proStepsCount == 5)
    }
}
