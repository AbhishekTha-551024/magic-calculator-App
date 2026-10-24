package com.magic.calculator.feature.subscription.presentation.state

import com.magic.calculator.feature.subscription.domain.model.PlanTier
import com.magic.calculator.feature.subscription.domain.model.SubscriptionDetails

sealed interface PremiumUiState {
    object Idle : PremiumUiState
    data class PlanSelected(val plan: PlanTier) : PremiumUiState
    object CreatingOrder : PremiumUiState
    data class OrderCreated(val orderId: String, val amountInINR: Int, val plan: PlanTier) : PremiumUiState
    object VerifyingPayment : PremiumUiState
    data class PaymentSuccess(val message: String, val details: SubscriptionDetails? = null) : PremiumUiState

    data class VerificationPending(
        val paymentId: String,
        val orderId: String,
        val signature: String,
        val plan: PlanTier,
        val reason: String
    ) : PremiumUiState
    object Restoring : PremiumUiState
    data class RestoreCompleted(val success: Boolean, val message: String) : PremiumUiState
    data class Error(val message: String) : PremiumUiState
}
