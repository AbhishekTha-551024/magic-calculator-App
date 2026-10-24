package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.repository.AppLockRepository

class ResetLockTrickUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke() {
        repository.resetLockTrick()
    }
}
