package com.magic.calculator.feature.calculator.domain.usecase

import com.magic.calculator.feature.calculator.domain.repository.CalculatorRepository
import com.magic.calculator.core.logic.MagicEngine

class PerformMagicTrickUseCase(private val repository: CalculatorRepository) {
    operator fun invoke(
        input: String,
        type: MagicEngine.TrickType,
        forcedPhone: String = "8815114272",
        forcedLucky: String = "777",
        flipResult: String = "699"
    ): MagicEngine.TrickResult {
        return repository.performMagic(input, type, forcedPhone, forcedLucky, flipResult)
    }
}
