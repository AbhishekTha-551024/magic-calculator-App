package com.magic.calculator.feature.subscription.presentation.state

import com.magic.calculator.feature.subscription.domain.model.PaymentRecord

sealed interface PaymentHistoryUiState {
    object Loading : PaymentHistoryUiState
    data class Success(val records: List<PaymentRecord>) : PaymentHistoryUiState
    data class Error(val message: String) : PaymentHistoryUiState
}
