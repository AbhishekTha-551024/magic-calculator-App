package com.magic.calculator.feature.calculator.domain.repository

import com.magic.calculator.core.logic.MagicEngine

/**
 * Domain repository contract for calculator operations and magic engine evaluation.
 */
interface CalculatorRepository {
    fun identifyTrick(input: String, customTrigger: String = "66"): MagicEngine.TrickType
    fun performMagic(
        input: String,
        type: MagicEngine.TrickType,
        forcedPhone: String = "8815114272",
        forcedLucky: String = "777",
        flipResult: String = "699"
    ): MagicEngine.TrickResult
    fun checkSecretUnlock(inputSequence: String, currentInput: String, customPin: String = ""): Boolean
}
