package com.magic.calculator.feature.subscription.domain.usecase

import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository

class RestorePurchasesUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(): Result<String> {
        return repository.syncAndRestorePremiumStatus()
    }
}
