package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.repository.AppLockRepository

class SetAppLockActiveUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke(active: Boolean) {
        repository.setAppLockActive(active)
    }
}
