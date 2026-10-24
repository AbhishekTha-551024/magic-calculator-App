package com.magic.calculator.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magic.calculator.feature.auth.domain.model.User
import com.magic.calculator.feature.auth.domain.repository.AuthRepository
import com.magic.calculator.feature.auth.presentation.state.AuthUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing authentication state and actions.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val currentUser: User?
        get() = authRepository.currentUser

    val isLoggedIn: Boolean
        get() = authRepository.isLoggedIn

    val currentUid: String?
        get() = authRepository.currentUid

    fun loginWithGoogle(idToken: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.loginWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Authentication failed")
                }
            )
        }
    }

    fun loginWithEmail(email: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.loginWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Login failed")
                }
            )
        }
    }

    fun registerWithEmail(email: String, password: String) {
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = authRepository.registerWithEmail(email, password)
            result.fold(
                onSuccess = { user ->
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Registration failed")
                }
            )
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                return AuthViewModel(authRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
