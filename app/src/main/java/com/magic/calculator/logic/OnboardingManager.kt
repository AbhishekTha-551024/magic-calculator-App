package com.magic.calculator.logic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * OnboardingManager
 *
 * Coordinates interactive onboarding progression for:
 * 1. Free Tier (New Magicians) — Disguise, Backdoor PIN (741=), simple tricks & sensor triggers.
 * 2. Pro Tier (VIP Subscribers) — Toxic Time, Ghost Typing, P2P Link, Stealth Gallery, MoiLock.
 *
 * Persists status both locally in SharedPreferences and in Firebase Firestore under users/{userId}:
 * - isNewUser: Boolean
 * - hasCompletedFreeOnboarding: Boolean
 * - hasCompletedBasicTour: Boolean
 * - hasCompletedProOnboarding: Boolean
 */
object OnboardingManager {

    private const val TAG = "OnboardingManager"
    private const val PREF_NAME = "magic_onboarding_prefs"
    private const val KEY_FREE_ONBOARDING_DONE = "free_onboarding_completed"
    private const val KEY_PRO_ONBOARDING_DONE = "pro_onboarding_completed"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Clears all local onboarding preferences on account switch / logout.
     */
    fun clearLocalState(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    /**
     * Determines whether to show the initial Free onboarding tour to a new user.
     * Returns true ONLY if the user has not yet completed or skipped the basic tour.
     */
    fun shouldShowFreeOnboarding(context: Context): Boolean {
        val prefs = getPrefs(context)
        return !prefs.getBoolean(KEY_FREE_ONBOARDING_DONE, false)
    }

    /**
     * Determines whether to show the advanced Pro onboarding masterclass to a subscribed user.
     */
    fun shouldShowProOnboarding(context: Context): Boolean {
        val isPremium = PremiumManager.isUserPremium || SubscriptionManager.isPremiumUser(context)
        if (!isPremium) return false
        val prefs = getPrefs(context)
        return !prefs.getBoolean(KEY_PRO_ONBOARDING_DONE, false)
    }

    /**
     * Marks Free Tier onboarding as completed locally and syncs to Firestore.
     */
    fun markFreeOnboardingCompleted(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_FREE_ONBOARDING_DONE, true).apply()

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val data = mapOf(
            "hasCompletedFreeOnboarding" to true,
            "hasCompletedBasicTour" to true,
            "isNewUser" to false,
            "freeOnboardingCompletedAt" to System.currentTimeMillis(),
            "lastUpdatedOnboarding" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Successfully synced Free Onboarding completion to Firestore for ${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync Free Onboarding completion to Firestore", e)
            }
    }

    /**
     * Marks Pro Tier onboarding as completed locally and syncs to Firestore.
     */
    fun markProOnboardingCompleted(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_PRO_ONBOARDING_DONE, true).apply()

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val data = mapOf(
            "hasCompletedProOnboarding" to true,
            "proOnboardingCompletedAt" to System.currentTimeMillis(),
            "lastUpdatedOnboarding" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Successfully synced Pro Onboarding completion to Firestore for ${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync Pro Onboarding completion to Firestore", e)
            }
    }

    /**
     * Resets onboarding state to allow replaying tutorials on demand from Settings or Profile.
     */
    fun resetOnboarding(context: Context, isPro: Boolean) {
        val key = if (isPro) KEY_PRO_ONBOARDING_DONE else KEY_FREE_ONBOARDING_DONE
        getPrefs(context).edit().putBoolean(key, false).apply()

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val updates = if (isPro) {
            mapOf("hasCompletedProOnboarding" to false)
        } else {
            mapOf(
                "hasCompletedFreeOnboarding" to false,
                "hasCompletedBasicTour" to false
            )
        }
        FirebaseFirestore.getInstance().collection("users")
            .document(user.uid)
            .set(updates, SetOptions.merge())
    }

    /**
     * Pulls onboarding completion state from Firestore.
     * Accurately restores whether a user is new or has completed the tour.
     */
    fun syncFromFirestore(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete?.invoke(false)
            return
        }

        FirebaseFirestore.getInstance().collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val freeDone = document.getBoolean("hasCompletedFreeOnboarding")
                        ?: document.getBoolean("hasCompletedBasicTour")
                        ?: false
                    val proDone = document.getBoolean("hasCompletedProOnboarding") ?: false

                    getPrefs(context).edit()
                        .putBoolean(KEY_FREE_ONBOARDING_DONE, freeDone)
                        .putBoolean(KEY_PRO_ONBOARDING_DONE, proDone)
                        .apply()
                    Log.d(TAG, "Synced from Firestore for ${user.uid}: freeDone=$freeDone, proDone=$proDone")
                    onComplete?.invoke(true)
                } else {
                    // New user document in Firestore
                    val initialUserData = mapOf(
                        "isNewUser" to true,
                        "hasCompletedFreeOnboarding" to false,
                        "hasCompletedBasicTour" to false,
                        "hasCompletedProOnboarding" to false,
                        "email" to (user.email ?: ""),
                        "createdAt" to System.currentTimeMillis(),
                        "lastSeen" to System.currentTimeMillis()
                    )
                    getPrefs(context).edit()
                        .putBoolean(KEY_FREE_ONBOARDING_DONE, false)
                        .putBoolean(KEY_PRO_ONBOARDING_DONE, false)
                        .apply()

                    FirebaseFirestore.getInstance().collection("users")
                        .document(user.uid)
                        .set(initialUserData, SetOptions.merge())
                    onComplete?.invoke(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error syncing onboarding from Firestore", e)
                onComplete?.invoke(false)
            }
    }
}
