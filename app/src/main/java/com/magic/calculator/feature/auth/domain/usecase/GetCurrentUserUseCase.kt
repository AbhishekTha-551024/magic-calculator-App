package com.magic.calculator.feature.auth.domain.usecase

import com.magic.calculator.feature.auth.domain.model.User
import com.magic.calculator.feature.auth.domain.repository.AuthRepository

class GetCurrentUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): User? {
        return repository.currentUser
    }
}
