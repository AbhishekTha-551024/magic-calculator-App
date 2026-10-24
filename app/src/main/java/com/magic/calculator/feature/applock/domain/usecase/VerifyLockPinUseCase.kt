package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import com.magic.calculator.feature.applock.domain.repository.AppLockRepository

class VerifyLockPinUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke(candidatePin: String): UnlockStatus {
        return repository.verifyPin(candidatePin)
    }
}
