package com.magic.calculator.logic

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * PremiumManager
 *
 * Elite gatekeeper class for CalcMagic Pro state-management flow.
 * Handles in-memory caching, SharedPreferences mirroring, and rigid
 * time-bound subscription checks via Firebase Firestore.
 */
object PremiumManager {
    private const val TAG = "PremiumManager"
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Active in-memory volatile cache for ultimate speed and thread safety
    @Volatile
    var isPremiumUser: Boolean = false
        private set

    // Backward compatibility property for existing UI calls
    val isUserPremium: Boolean
        get() = isPremiumUser

    /**
     * Instantly checks the volatile cache before performing any critical UI or trick action.
     */
    fun enforceGatekeeper(onUnlocked: () -> Unit, onLocked: () -> Unit) {
        if (isPremiumUser) {
            onUnlocked()
        } else {
            onLocked()
        }
    }

    /**
     * Executes Step 2 time-bound verification asynchronously using Coroutines/Tasks.
     * Compares the current system time with the Firestore 'premiumExpiryDate' timestamp.
     */
    fun verifySessionSubscription(context: Context, uid: String, onComplete: (Boolean) -> Unit) {
        val userRef = db.collection("users").document(uid)
        
        userRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val purchaseType = snapshot.getString("purchaseType") ?: "free"
                    val premiumExpiryDate = snapshot.getLong("premiumExpiryDate") ?: 0L
                    val isPremium = snapshot.getBoolean("isPremium") ?: false
                    val currentTime = System.currentTimeMillis()

                    Log.d(TAG, "Checking license validity. Expiry: $premiumExpiryDate, Current: $currentTime, Type: $purchaseType")

                    if (purchaseType == "subscription" && currentTime > premiumExpiryDate) {
                        Log.w(TAG, "Subscription EXPIRED! Reverting status to free tier.")
                        val expiredData = mapOf(
                            "isPremium" to false,
                            "purchaseType" to "free",
                            "premiumExpiryDate" to 0L,
                            "paymentStatus" to "EXPIRED"
                        )
                        userRef.set(expiredData, SetOptions.merge())
                        
                        updateLocalState(context, false)
                        onComplete(false)
                    } else {
                        val valid = isPremium && (purchaseType == "lifetime" || currentTime <= premiumExpiryDate)
                        updateLocalState(context, valid)
                        onComplete(valid)
                    }
                } else {
                    Log.d(TAG, "New user registered. Initializing Firestore profile.")
                    val initialProfile = mapOf(
                        "isPremium" to false,
                        "purchaseType" to "free",
                        "activatedAt" to 0L,
                        "premiumExpiryDate" to 0L,
                        "transactionId" to "N/A",
                        "paymentStatus" to "NONE",
                        "pricePaid" to "₹0"
                    )
                    userRef.set(initialProfile, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully initialized new user profile in Firestore.")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to write initial profile to Firestore.", e)
                        }

                    updateLocalState(context, false)
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user subscription session. Falling back to cache.", e)
                val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
                val cachedState = prefs.getBoolean("is_premium", false)
                isPremiumUser = cachedState
                onComplete(cachedState)
            }
    }

    fun startListening(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            updateLocalState(context, false)
            return
        }

        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        isPremiumUser = prefs.getBoolean("is_premium", false)

        verifySessionSubscription(context, user.uid) { isPremium ->
            Log.d(TAG, "Initial session subscription verified: $isPremium")
        }
    }

    /**
     * Updates Firestore user state for simulated purchases (Sandbox Checkout).
     */
    fun updatePremiumFirestore(context: Context, isPremium: Boolean, planType: String, onComplete: (Boolean, String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            updateLocalState(context, isPremium)
            onComplete(true, "Mock Sandbox purchase completed locally!")
            return
        }

        val userRef = db.collection("users").document(user.uid)
        val transactionId = "TXN-CALC-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val activatedAt = System.currentTimeMillis()

        val data = if (planType == "YEARLY" || planType == "lifetime") {
            mapOf(
                "isPremium" to true,
                "purchaseType" to "lifetime",
                "activatedAt" to activatedAt,
                "premiumExpiryDate" to 9999999999999L,
                "transactionId" to transactionId,
                "paymentStatus" to "SUCCESS",
                "pricePaid" to "₹499"
            )
        } else {
            mapOf(
                "isPremium" to true,
                "purchaseType" to "subscription",
                "activatedAt" to activatedAt,
                "premiumExpiryDate" to (System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)),
                "transactionId" to transactionId,
                "paymentStatus" to "SUCCESS",
                "pricePaid" to "₹99"
            )
        }

        userRef.set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Firestore state updated successfully for plan: $planType")
                updateLocalState(context, true)
                onComplete(true, "Purchase successful! CalcMagic Pro features unlocked.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore sync failed. Saving status locally.", e)
                updateLocalState(context, true)
                onComplete(true, "Unlocked locally (Sync pending active connection).")
            }
    }

    /**
     * Helper to update both in-memory cache and local SharedPreferences.
     */
    private fun updateLocalState(context: Context, isPremium: Boolean) {
        isPremiumUser = isPremium
        val prefs = context.getSharedPreferences("premium_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_premium", isPremium).apply()
    }

    /**
     * Wipes session cache to false instantly on sign-out, preventing cross-account leaks.
     */
    fun clearSession(context: Context) {
        updateLocalState(context, false)
        Log.d(TAG, "Session cleared. Local cache set to FALSE.")
    }

    fun clearLocalPremium(context: Context) {
        clearSession(context)
    }

    fun stopListening() {
        // Kept for backward compatibility
    }
}
