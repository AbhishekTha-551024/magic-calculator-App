package com.magic.calculator.feature.auth.domain.usecase

import com.magic.calculator.feature.auth.domain.model.User
import com.magic.calculator.feature.auth.domain.repository.AuthRepository

class RegisterWithEmailUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        return repository.registerWithEmail(email, password)
    }
}
