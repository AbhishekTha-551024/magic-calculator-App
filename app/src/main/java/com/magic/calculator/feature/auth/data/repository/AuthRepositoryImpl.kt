package com.magic.calculator.feature.auth.data.repository

import com.google.firebase.auth.FirebaseUser
import com.magic.calculator.feature.auth.data.datasource.AuthRemoteDataSource
import com.magic.calculator.feature.auth.data.datasource.FirebaseAuthRemoteDataSource
import com.magic.calculator.feature.auth.domain.model.User
import com.magic.calculator.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete implementation of AuthRepository.
 * Maps data-layer FirebaseUser models to pure domain User models.
 */
class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource = FirebaseAuthRemoteDataSource()
) : AuthRepository {

    override val currentUser: User?
        get() = remoteDataSource.currentUser?.toDomain()

    override val isLoggedIn: Boolean
        get() = remoteDataSource.currentUser != null

    override val currentUid: String?
        get() = remoteDataSource.currentUser?.uid

    override val authState: Flow<User?>
        get() = remoteDataSource.authState.map { it?.toDomain() }

    override suspend fun loginWithEmail(email: String, password: String): Result<User> {
        return try {
            val firebaseUser = remoteDataSource.loginWithEmail(email, password)
            Result.success(firebaseUser.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<User> {
        return try {
            val firebaseUser = remoteDataSource.registerWithEmail(email, password)
            Result.success(firebaseUser.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<User> {
        return try {
            val firebaseUser = remoteDataSource.loginWithGoogle(idToken)
            Result.success(firebaseUser.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            remoteDataSource.sendPasswordReset(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun logout() {
        remoteDataSource.logout()
    }

    private fun FirebaseUser.toDomain(): User {
        return User(
            uid = uid,
            email = email,
            displayName = displayName,
            phoneNumber = phoneNumber,
            photoUrl = photoUrl?.toString()
        )
    }
}
