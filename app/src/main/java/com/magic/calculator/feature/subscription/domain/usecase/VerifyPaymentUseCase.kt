package com.magic.calculator.feature.subscription.domain.usecase

import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository

class VerifyPaymentUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        userId: String?,
        userEmail: String,
        planName: String,
        durationDays: Int
    ): Result<String> {
        return repository.verifyAndUnlockPayment(
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            planType = planType,
            userId = userId,
            userEmail = userEmail,
            planName = planName,
            durationDays = durationDays
        )
    }
}
