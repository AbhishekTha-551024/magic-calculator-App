package com.magic.calculator.feature.secret.domain.usecase

import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class ActivateMagicUseCase(
    private val repository: SecretRepository
) {
    operator fun invoke(magicKey: String) {
        repository.setActiveMagic(magicKey)
    }
}
