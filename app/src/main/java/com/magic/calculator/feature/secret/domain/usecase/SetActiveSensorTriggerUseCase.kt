package com.magic.calculator.feature.secret.domain.usecase

import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class SetActiveSensorTriggerUseCase(
    private val repository: SecretRepository
) {
    operator fun invoke(triggerKey: String) {
        repository.setSensorTrigger(triggerKey)
    }
}
