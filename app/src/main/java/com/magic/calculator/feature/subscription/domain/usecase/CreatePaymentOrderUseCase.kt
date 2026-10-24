package com.magic.calculator.feature.subscription.domain.usecase

import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository

class CreatePaymentOrderUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(amount: Int, planType: String, userId: String?): Result<String> {
        return repository.createPaymentOrder(amount, planType, userId)
    }
}
