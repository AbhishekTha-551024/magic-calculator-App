package com.magic.calculator.feature.auth.domain.repository

import com.magic.calculator.feature.auth.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract defining authentication operations.
 */
interface AuthRepository {
    val currentUser: User?
    val isLoggedIn: Boolean
    val currentUid: String?
    val authState: Flow<User?>

    suspend fun loginWithEmail(email: String, password: String): Result<User>
    suspend fun registerWithEmail(email: String, password: String): Result<User>
    suspend fun loginWithGoogle(idToken: String): Result<User>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    fun logout()
}
