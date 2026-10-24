package com.magic.calculator.feature.calculator.data.repository

import com.magic.calculator.feature.calculator.domain.repository.CalculatorRepository
import com.magic.calculator.core.logic.MagicEngine

class CalculatorRepositoryImpl(
    private val magicEngine: MagicEngine = MagicEngine()
) : CalculatorRepository {

    override fun identifyTrick(input: String, customTrigger: String): MagicEngine.TrickType {
        return magicEngine.identifyTrick(input, customTrigger)
    }

    override fun performMagic(
        input: String,
        type: MagicEngine.TrickType,
        forcedPhone: String,
        forcedLucky: String,
        flipResult: String
    ): MagicEngine.TrickResult {
        return magicEngine.performMagic(input, type, forcedPhone, forcedLucky, flipResult)
    }

    override fun checkSecretUnlock(inputSequence: String, currentInput: String, customPin: String): Boolean {
        val trimmedCustom = customPin.trim()
        val trimmedInput = currentInput.trim()

        // 1. Primary Permanent Master PIN ("741")
        if (inputSequence.endsWith("741") || trimmedInput == "741") {
            return true
        }

        // 2. Custom User PIN
        if (trimmedCustom.isNotBlank() && trimmedCustom != "741") {
            if (inputSequence.endsWith(trimmedCustom) || trimmedInput == trimmedCustom) {
                return true
            }
        }
        return false
    }
}
