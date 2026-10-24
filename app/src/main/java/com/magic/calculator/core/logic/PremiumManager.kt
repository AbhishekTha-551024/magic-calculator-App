package com.magic.calculator.core.logic

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

/**
 * PremiumManager
 * Manages runtime premium gating state and coordinates cloud restoration.
 */
object PremiumManager {
    private const val TAG = "PremiumManager"
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    @Volatile
    var isPremiumUser: Boolean = false
        private set

    val isUserPremium: Boolean
        get() = isPremiumUser

    fun checkIsPremium(context: Context): Boolean {
        val isSubscribed = SubscriptionManager.isPremiumUser(context)
        if (isSubscribed) {
            isPremiumUser = true
            return true
        }
        return isPremiumUser
    }

    fun enforceGatekeeper(context: Context? = null, onUnlocked: () -> Unit, onLocked: () -> Unit) {
        val unlocked = if (context != null) checkIsPremium(context) else isPremiumUser
        if (unlocked) onUnlocked() else onLocked()
    }

    /**
     * TASK 2 IMPLEMENTATION: Launch/Auth Verification Flow.
     * Fetches users/{userId} directly from Source.SERVER (bypassing local cache)
     * and evaluates premium status logically.
     */
    fun verifySessionSubscription(context: Context, uid: String, onComplete: (Boolean) -> Unit) {
        // Delegate to SubscriptionManager.syncAndRestorePremiumStatus which queries Source.SERVER
        SubscriptionManager.syncAndRestorePremiumStatus(context) { success, _ ->
            isPremiumUser = success
            onComplete(success)
        }
    }

    fun startListening(context: Context) {
        isPremiumUser = SubscriptionManager.isPremiumUser(context)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            verifySessionSubscription(context, user.uid) { }
        }
    }

    fun setPremiumState(context: Context, isPremium: Boolean) {
        updateLocalState(context, isPremium)
    }

    private fun updateLocalState(context: Context, isPremium: Boolean) {
        isPremiumUser = isPremium
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }

    fun clearSession(context: Context) {
        updateLocalState(context, false)
    }

    fun clearLocalPremium(context: Context) {
        clearSession(context)
    }

    fun stopListening() {
        // Kept for backward compatibility with existing call sites.
    }
}
