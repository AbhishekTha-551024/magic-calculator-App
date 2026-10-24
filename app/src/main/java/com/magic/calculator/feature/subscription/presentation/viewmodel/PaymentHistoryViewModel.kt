package com.magic.calculator.feature.subscription.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository
import com.magic.calculator.feature.subscription.presentation.state.PaymentHistoryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PaymentHistoryViewModel(
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaymentHistoryUiState>(PaymentHistoryUiState.Loading)
    val uiState: StateFlow<PaymentHistoryUiState> = _uiState.asStateFlow()

    fun loadHistory() {
        _uiState.value = PaymentHistoryUiState.Loading
        viewModelScope.launch {
            val result = subscriptionRepository.fetchPaymentHistory()
            result.fold(
                onSuccess = { list ->
                    _uiState.value = PaymentHistoryUiState.Success(list)
                },
                onFailure = { error ->
                    _uiState.value = PaymentHistoryUiState.Error(error.localizedMessage ?: "Failed to load payment history")
                }
            )
        }
    }

    class Factory(private val repository: SubscriptionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PaymentHistoryViewModel::class.java)) {
                return PaymentHistoryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
