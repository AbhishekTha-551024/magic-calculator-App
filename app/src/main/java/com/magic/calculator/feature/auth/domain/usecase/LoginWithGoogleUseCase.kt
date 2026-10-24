package com.magic.calculator.feature.auth.domain.usecase

import com.magic.calculator.feature.auth.domain.model.User
import com.magic.calculator.feature.auth.domain.repository.AuthRepository

class LoginWithGoogleUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(idToken: String): Result<User> {
        return repository.loginWithGoogle(idToken)
    }
}
