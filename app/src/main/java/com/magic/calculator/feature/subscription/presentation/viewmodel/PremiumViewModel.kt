package com.magic.calculator.feature.subscription.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magic.calculator.feature.subscription.domain.model.PlanTier
import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository
import com.magic.calculator.feature.subscription.presentation.state.PremiumUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PremiumViewModel(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PremiumUiState>(PremiumUiState.Idle)
    val uiState: StateFlow<PremiumUiState> = _uiState.asStateFlow()

    var selectedPlan: PlanTier = PlanTier.MONTHLY
        private set

    val isPremium: StateFlow<Boolean> = subscriptionRepository.isPremium

    fun selectPlan(plan: PlanTier) {
        selectedPlan = plan
        _uiState.value = PremiumUiState.PlanSelected(plan)
    }

    fun startPurchaseFlow(userId: String?) {
        _uiState.value = PremiumUiState.CreatingOrder
        viewModelScope.launch {
            val result = subscriptionRepository.createPaymentOrder(
                amount = selectedPlan.amountInINR,
                planType = selectedPlan.planCode,
                userId = userId
            )
            result.fold(
                onSuccess = { orderId ->
                    _uiState.value = PremiumUiState.OrderCreated(
                        orderId = orderId,
                        amountInINR = selectedPlan.amountInINR,
                        plan = selectedPlan
                    )
                },
                onFailure = { error ->
                    _uiState.value = PremiumUiState.Error(error.localizedMessage ?: "Could not create payment order")
                }
            )
        }
    }

    fun verifyPayment(
        paymentId: String,
        orderId: String,
        signature: String,
        userId: String?,
        userEmail: String
    ) {
        _uiState.value = PremiumUiState.VerifyingPayment
        viewModelScope.launch {
            val result = subscriptionRepository.verifyAndUnlockPayment(
                orderId = orderId,
                paymentId = paymentId,
                signature = signature,
                planType = selectedPlan.planCode,
                userId = userId,
                userEmail = userEmail,
                planName = selectedPlan.displayName,
                durationDays = selectedPlan.durationDays
            )
            result.fold(
                onSuccess = { message ->
                    val details = subscriptionRepository.getSubscriptionDetails()
                    _uiState.value = PremiumUiState.PaymentSuccess(message, details)
                },
                onFailure = { error ->
                    _uiState.value = PremiumUiState.VerificationPending(
                        paymentId = paymentId,
                        orderId = orderId,
                        signature = signature,
                        plan = selectedPlan,
                        reason = error.localizedMessage ?: "Server signature verification failed"
                    )
                }
            )
        }
    }

    fun restorePurchases() {
        _uiState.value = PremiumUiState.Restoring
        viewModelScope.launch {
            val result = subscriptionRepository.syncAndRestorePremiumStatus()
            result.fold(
                onSuccess = { msg ->
                    _uiState.value = PremiumUiState.RestoreCompleted(true, msg)
                },
                onFailure = { error ->
                    _uiState.value = PremiumUiState.RestoreCompleted(false, error.localizedMessage ?: "Failed to restore purchase")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = PremiumUiState.Idle
    }

    class Factory(private val repository: SubscriptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PremiumViewModel::class.java)) {
                return PremiumViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
