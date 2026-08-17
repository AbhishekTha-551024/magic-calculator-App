package com.magic.calculator.logic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SubscriptionDetails
 * Data class holding subscription metadata.
 */
data class SubscriptionDetails(
    val isPremium: Boolean = false,
    val planType: String = "Free Tier",
    val startDate: String = "N/A",
    val expiryDate: String = "N/A",
    val paymentId: String = "N/A"
)

/**
 * SubscriptionManager
 * Manages persistent storage of subscription status and metadata using SharedPreferences
 * and handles cloud sync / restore from Firebase Firestore directly from Source.SERVER.
 *
 * CRITICAL FIX: All Firestore writes to users/{userId} use SetOptions.merge() to ensure
 * payment fields (isPremium, isProUser, planType, transactionId) are NEVER overwritten or wiped.
 */
object SubscriptionManager {

    private const val TAG = "SubscriptionManager"
    private const val PREF_NAME = "subscription_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_PLAN_TYPE = "plan_type"
    private const val KEY_START_DATE = "start_date"
    private const val KEY_EXPIRY_DATE = "expiry_date"
    private const val KEY_EXPIRY_MILLIS = "expiry_millis"
    private const val KEY_PAYMENT_ID = "payment_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save subscription details locally in SharedPreferences.
     */
    fun saveSubscription(
        context: Context,
        planType: String,
        paymentId: String,
        durationDays: Int = 365
    ) {
        val nowMillis = System.currentTimeMillis()
        val durationMillis = durationDays.toLong() * 24 * 60 * 60 * 1000
        val expiryMillis = nowMillis + durationMillis

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val startDateStr = dateFormat.format(Date(nowMillis))
        val expiryDateStr = dateFormat.format(Date(expiryMillis))

        getPrefs(context).edit().apply {
            putBoolean(KEY_IS_PREMIUM, true)
            putString(KEY_PLAN_TYPE, planType)
            putString(KEY_START_DATE, startDateStr)
            putString(KEY_EXPIRY_DATE, expiryDateStr)
            putLong(KEY_EXPIRY_MILLIS, expiryMillis)
            putString(KEY_PAYMENT_ID, paymentId)
            apply()
        }

        // Keep PremiumManager state in sync
        PremiumManager.setPremiumState(context, true)
    }

    /**
     * TASK 1 FIX:
     * Save subscription locally and MERGE (SetOptions.merge()) isPremium=true, isProUser=true,
     * planType, and transactionId to users/{userId} in Firestore.
     */
    fun syncSubscriptionToFirestore(
        context: Context,
        planType: String,
        paymentId: String,
        durationDays: Int = 365
    ) {
        saveSubscription(context, planType, paymentId, durationDays)

        val user = FirebaseAuth.getInstance().currentUser ?: return

        val paymentData = hashMapOf(
            "isPremium"      to true,
            "isProUser"      to true,
            "planType"       to planType,
            "transactionId"  to paymentId,
            "paymentId"      to paymentId,
            "activatedAt"    to System.currentTimeMillis(),
            "lastSyncedAt"   to System.currentTimeMillis(),
            "lastSeen"       to System.currentTimeMillis()
        )

        // SetOptions.merge() GUARANTEES existing document fields are preserved and merged
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(paymentData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Payment & Premium status merged into Firestore for user: ${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to write subscription metadata to Firestore", e)
            }
    }

    /**
     * Ensures every user document users/{userId} exists with email and lastSeen initialized.
     * Uses SetOptions.merge() so it NEVER overwrites existing premium/payment fields.
     */
    fun ensureUserDocumentCreated(user: FirebaseAuth? = null) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userData = hashMapOf<String, Any>(
            "email" to (currentUser.email ?: ""),
            "lastSeen" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "User document initialized/merged safely for: ${currentUser.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to initialize user document", e)
            }
    }

    /**
     * Safely update metadata (e.g., lastSeen) using SetOptions.merge() to ensure
     * payment fields are NEVER overwritten or wiped.
     */
    fun updateUserMetadata(context: Context, metadata: Map<String, Any>) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        ensureUserDocumentCreated()
        val mergedData = HashMap(metadata).apply {
            put("lastSeen", System.currentTimeMillis())
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(mergedData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Metadata safely merged for user: ${user.uid}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update user metadata", e)
            }
    }

    /**
     * TASK 2 & 3 IMPLEMENTATION: Reusable Restore & Sync function.
     * Queries users/{userId} directly from Source.SERVER (bypassing Firestore local cache)
     * to ensure live database status is checked upon app launch, login, or manual restore.
     *
     * Premium Evaluation Rule:
     * User is Premium IF:
     *   - isPremium == true OR isProUser == true
     *   - OR planType != null & non-empty & != "Free Tier"
     *   - OR transactionId / paymentId != null & non-empty & != "N/A"
     */
    fun syncAndRestorePremiumStatus(
        context: Context,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onComplete(false, "User not signed in. Please log in to restore purchases.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userDocRef = db.collection("users").document(user.uid)

        // Update lastSeen with SetOptions.merge() during sync
        updateUserMetadata(context, emptyMap())

        // Force fetch directly from Source.SERVER (bypasses local cache)
        userDocRef.get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val isPremiumBool = snapshot.getBoolean("isPremium") == true
                    val isProUserBool = snapshot.getBoolean("isProUser") == true
                    val planType = snapshot.getString("planType") ?: ""
                    val transactionId = snapshot.getString("transactionId")
                        ?: snapshot.getString("paymentId")
                        ?: ""

                    val hasValidPlan = planType.isNotEmpty() && !planType.equals("Free Tier", ignoreCase = true)
                    val hasValidTransaction = transactionId.isNotEmpty() && !transactionId.equals("N/A", ignoreCase = true)
                    val expiryMillisFromDoc = snapshot.getLong("expiryMillis")
                        ?: snapshot.getLong("premiumExpiryDate")
                        ?: 0L

                    val isLifetime = planType.contains("Lifetime", ignoreCase = true)
                    val isExpired = !isLifetime && expiryMillisFromDoc > 0L && System.currentTimeMillis() > expiryMillisFromDoc

                    // Determine Premium Status logically
                    val isUserPro = (isPremiumBool || isProUserBool || hasValidPlan || hasValidTransaction) && !isExpired

                    if (isUserPro) {
                        val activePlan = if (hasValidPlan) planType else "CalcMagic Pro"
                        val activeTx = if (hasValidTransaction) transactionId else "RESTORED_PAYMENT"

                        val durationDays = when {
                            activePlan.contains("Lifetime", ignoreCase = true) -> 36500
                            activePlan.contains("Monthly", ignoreCase = true)  -> 30
                            activePlan.contains("Weekly", ignoreCase = true)   -> 7
                            else -> 30
                        }

                        saveSubscription(context, activePlan, activeTx, durationDays)
                        PremiumManager.setPremiumState(context, true)

                        Log.d(TAG, "Premium status RESTORED from SERVER: plan=$activePlan, tx=$activeTx")
                        onComplete(true, "Purchase restored successfully! Active plan: $activePlan")
                    } else {
                        // User has no valid or active premium entitlement on server (or subscription expired)
                        clearSubscription(context)
                        PremiumManager.setPremiumState(context, false)
                        val msg = if (isExpired) "Your previous subscription has expired." else "No active subscription found for account: ${user.email}"
                        Log.d(TAG, "$msg for user: ${user.uid}")
                        onComplete(false, msg)
                    }
                } else {
                    clearSubscription(context)
                    PremiumManager.setPremiumState(context, false)
                    onComplete(false, "No purchase history found for account: ${user.email}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Source.SERVER fetch failed. Trying cache fallback...", e)
                // Fallback attempt in case of network issue
                userDocRef.get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot != null && snapshot.exists()) {
                            val isPremiumBool = snapshot.getBoolean("isPremium") == true
                            val isProUserBool = snapshot.getBoolean("isProUser") == true
                            val planType = snapshot.getString("planType") ?: ""
                            val transactionId = snapshot.getString("transactionId") ?: ""
                            val isUserPro = isPremiumBool || isProUserBool || planType.isNotEmpty() || transactionId.isNotEmpty()

                            if (isUserPro) {
                                val activePlan = if (planType.isNotEmpty()) planType else "CalcMagic Pro"
                                saveSubscription(context, activePlan, transactionId, 365)
                                PremiumManager.setPremiumState(context, true)
                                onComplete(true, "Restored from cached session: $activePlan")
                                return@addOnSuccessListener
                            }
                        }
                        val localPro = isPremiumUser(context)
                        onComplete(localPro, if (localPro) "Using cached premium status." else "Could not connect to server.")
                    }
                    .addOnFailureListener {
                        val localPro = isPremiumUser(context)
                        onComplete(localPro, if (localPro) "Using offline premium status." else "Failed to restore purchase: ${e.message}")
                    }
            }
    }

    /**
     * Backward-compatible restore alias.
     */
    fun restoreUserSubscriptionFromFirestore(
        context: Context,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        syncAndRestorePremiumStatus(context, onComplete)
    }

    /**
     * Check if the user is currently a Premium user and subscription is active.
     */
    fun isPremiumUser(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isPremium = prefs.getBoolean(KEY_IS_PREMIUM, false)
        if (!isPremium) return false

        val expiryMillis = prefs.getLong(KEY_EXPIRY_MILLIS, 0L)
        if (expiryMillis > 0 && System.currentTimeMillis() > expiryMillis) {
            // Subscription expired, reset state
            clearSubscription(context)
            return false
        }

        return true
    }

    /**
     * Get structured subscription details for Profile UI.
     */
    fun getSubscriptionDetails(context: Context): SubscriptionDetails {
        if (!isPremiumUser(context)) {
            return SubscriptionDetails(
                isPremium = false,
                planType = "Free Tier",
                startDate = "N/A",
                expiryDate = "N/A",
                paymentId = "N/A"
            )
        }

        val prefs = getPrefs(context)
        return SubscriptionDetails(
            isPremium = true,
            planType = prefs.getString(KEY_PLAN_TYPE, "CalcMagic Pro") ?: "CalcMagic Pro",
            startDate = prefs.getString(KEY_START_DATE, "N/A") ?: "N/A",
            expiryDate = prefs.getString(KEY_EXPIRY_DATE, "N/A") ?: "N/A",
            paymentId = prefs.getString(KEY_PAYMENT_ID, "N/A") ?: "N/A"
        )
    }

    /**
     * Reset subscription details upon logout or expiry.
     */
    fun clearSubscription(context: Context) {
        getPrefs(context).edit().clear().apply()
        PremiumManager.clearSession(context)
    }
}
