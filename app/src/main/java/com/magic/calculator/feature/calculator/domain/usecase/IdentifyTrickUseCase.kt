package com.magic.calculator.feature.calculator.domain.usecase

import com.magic.calculator.feature.calculator.domain.repository.CalculatorRepository
import com.magic.calculator.core.logic.MagicEngine

class IdentifyTrickUseCase(private val repository: CalculatorRepository) {
    operator fun invoke(input: String, customTrigger: String = "66"): MagicEngine.TrickType {
        return repository.identifyTrick(input, customTrigger)
    }
}
