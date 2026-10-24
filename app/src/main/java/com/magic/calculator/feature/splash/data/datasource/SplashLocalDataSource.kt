package com.magic.calculator.feature.splash.data.datasource

import com.google.firebase.auth.FirebaseAuth
import com.magic.calculator.core.logic.AuthManager

interface SplashLocalDataSource {
    fun isUserLoggedIn(): Boolean
}

class FirebaseAuthSplashLocalDataSource : SplashLocalDataSource {
    override fun isUserLoggedIn(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return AuthManager.isLoggedIn && currentUser != null
    }
}
