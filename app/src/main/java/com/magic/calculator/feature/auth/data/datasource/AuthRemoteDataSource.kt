package com.magic.calculator.feature.auth.data.datasource

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Interface defining low-level remote authentication calls.
 */
interface AuthRemoteDataSource {
    val currentUser: FirebaseUser?
    val authState: Flow<FirebaseUser?>

    suspend fun loginWithEmail(email: String, password: String): FirebaseUser
    suspend fun registerWithEmail(email: String, password: String): FirebaseUser
    suspend fun loginWithGoogle(idToken: String): FirebaseUser
    suspend fun sendPasswordReset(email: String)
    fun logout()
}

/**
 * Firebase implementation of AuthRemoteDataSource.
 */
class FirebaseAuthRemoteDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRemoteDataSource {

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    override val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            trySend(currentAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun loginWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: throw IllegalStateException("FirebaseUser is null after sign in")
    }

    override suspend fun registerWithEmail(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: throw IllegalStateException("FirebaseUser is null after sign up")
    }

    override suspend fun loginWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw IllegalStateException("FirebaseUser is null after credential sign in")
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override fun logout() {
        auth.signOut()
    }
}
