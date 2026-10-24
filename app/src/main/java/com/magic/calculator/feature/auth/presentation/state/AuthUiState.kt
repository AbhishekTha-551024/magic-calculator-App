package com.magic.calculator.feature.auth.presentation.state

import com.magic.calculator.feature.auth.domain.model.User

/**
 * UI State representing the state of Authentication operations.
 */
sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class Success(val user: User) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
