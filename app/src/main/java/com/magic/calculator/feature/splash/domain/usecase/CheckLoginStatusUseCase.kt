package com.magic.calculator.feature.splash.domain.usecase

import com.magic.calculator.feature.splash.domain.repository.SplashRepository

class CheckLoginStatusUseCase(
    private val repository: SplashRepository
) {
    operator fun invoke(): Boolean = repository.isUserLoggedIn()
}
