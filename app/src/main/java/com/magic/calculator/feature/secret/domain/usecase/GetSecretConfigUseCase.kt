package com.magic.calculator.feature.secret.domain.usecase

import com.magic.calculator.feature.secret.domain.model.SwitchboardConfig
import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class GetSecretConfigUseCase(
    private val repository: SecretRepository
) {
    operator fun invoke(): SwitchboardConfig {
        return repository.getSwitchboardConfig()
    }
}
