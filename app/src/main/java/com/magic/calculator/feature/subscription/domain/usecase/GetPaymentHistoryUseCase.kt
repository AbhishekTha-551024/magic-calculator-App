package com.magic.calculator.feature.subscription.domain.usecase

import com.magic.calculator.feature.subscription.domain.model.PaymentRecord
import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository

class GetPaymentHistoryUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(): Result<List<PaymentRecord>> {
        return repository.fetchPaymentHistory()
    }
}
