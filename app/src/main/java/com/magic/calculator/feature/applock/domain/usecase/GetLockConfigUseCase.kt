package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.model.LockConfig
import com.magic.calculator.feature.applock.domain.repository.AppLockRepository

class GetLockConfigUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke(): LockConfig {
        return repository.getLockConfig()
    }
}
