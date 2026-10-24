package com.magic.calculator.feature.secret.domain.usecase

import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class MagicNeedsSensorUseCase(
    private val repository: SecretRepository
) {
    operator fun invoke(key: String): Boolean {
        return repository.magicNeedsSensor(key)
    }
}
