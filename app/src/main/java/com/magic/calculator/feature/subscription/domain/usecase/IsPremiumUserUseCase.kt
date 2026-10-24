package com.magic.calculator.feature.subscription.domain.usecase

import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository

class IsPremiumUserUseCase(private val repository: SubscriptionRepository) {
    operator fun invoke(): Boolean = repository.isPremiumUser()
}
