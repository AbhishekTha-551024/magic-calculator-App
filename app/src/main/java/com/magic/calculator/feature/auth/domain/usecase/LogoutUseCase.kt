package com.magic.calculator.feature.auth.domain.usecase

import com.magic.calculator.feature.auth.domain.repository.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke() {
        repository.logout()
    }
}
