package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.repository.AppLockRepository

class SaveLockConfigUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke(secretDigit: String, fakeDate: String) {
        repository.saveLockConfig(secretDigit, fakeDate)
    }
}
