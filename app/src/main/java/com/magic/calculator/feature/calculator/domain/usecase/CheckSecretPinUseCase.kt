package com.magic.calculator.feature.calculator.domain.usecase

import com.magic.calculator.feature.calculator.domain.repository.CalculatorRepository

class CheckSecretPinUseCase(private val repository: CalculatorRepository) {
    operator fun invoke(inputSequence: String, currentInput: String, customPin: String = ""): Boolean {
        return repository.checkSecretUnlock(inputSequence, currentInput, customPin)
    }
}
