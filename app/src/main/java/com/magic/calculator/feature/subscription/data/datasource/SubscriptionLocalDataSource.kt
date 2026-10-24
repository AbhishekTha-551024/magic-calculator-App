package com.magic.calculator.feature.subscription.data.datasource

import android.content.Context
import android.content.SharedPreferences
import com.magic.calculator.feature.subscription.domain.model.SubscriptionDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface SubscriptionLocalDataSource {
    fun isPremium(): Boolean
    fun getSubscriptionDetails(): SubscriptionDetails
    fun saveSubscription(
        planType: String,
        paymentId: String,
        durationDays: Int,
        activatedAt: Long = System.currentTimeMillis(),
        startDate: String? = null,
        expiryMillis: Long? = null,
        expiryDate: String? = null,
        purchaseType: String? = null
    )
    fun setPremiumState(isPremium: Boolean)
    fun clearSubscription()
}

class SharedPreferencesSubscriptionLocalDataSource(
    private val context: Context
) : SubscriptionLocalDataSource {

    companion object {
        private const val PREF_SUBSCRIPTION = "subscription_prefs"
        private const val PREF_PREMIUM = "premium_prefs"

        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_PLAN_TYPE = "plan_type"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_EXPIRY_DATE = "expiry_date"
        private const val KEY_EXPIRY_MILLIS = "expiry_millis"
        private const val KEY_ACTIVATED_AT = "activated_at"
        private const val KEY_PURCHASE_TYPE = "purchase_type"
        private const val KEY_PAYMENT_ID = "payment_id"
    }

    private val subPrefs: SharedPreferences
        get() = context.getSharedPreferences(PREF_SUBSCRIPTION, Context.MODE_PRIVATE)

    private val premiumPrefs: SharedPreferences
        get() = context.getSharedPreferences(PREF_PREMIUM, Context.MODE_PRIVATE)

    override fun isPremium(): Boolean {
        val isPremium = subPrefs.getBoolean(KEY_IS_PREMIUM, false)
        if (!isPremium) return false

        val purchaseType = subPrefs.getString(KEY_PURCHASE_TYPE, "subscription") ?: "subscription"
        val expiryMillis = subPrefs.getLong(KEY_EXPIRY_MILLIS, 0L)
        val isLifetime = purchaseType == "lifetime" || expiryMillis == 0L

        if (!isLifetime && expiryMillis > 0 && System.currentTimeMillis() > expiryMillis) {
            clearSubscription()
            return false
        }
        return true
    }

    override fun getSubscriptionDetails(): SubscriptionDetails {
        if (!isPremium()) {
            return SubscriptionDetails(
                isPremium = false,
                planType = "Free Tier",
                startDate = "N/A",
                expiryDate = "N/A",
                paymentId = "N/A",
                expiryMillis = 0L,
                activatedAt = 0L,
                purchaseType = "free"
            )
        }
        return SubscriptionDetails(
            isPremium = true,
            planType = subPrefs.getString(KEY_PLAN_TYPE, "CalcMagic Pro") ?: "CalcMagic Pro",
            startDate = subPrefs.getString(KEY_START_DATE, "N/A") ?: "N/A",
            expiryDate = subPrefs.getString(KEY_EXPIRY_DATE, "N/A") ?: "N/A",
            paymentId = subPrefs.getString(KEY_PAYMENT_ID, "N/A") ?: "N/A",
            expiryMillis = subPrefs.getLong(KEY_EXPIRY_MILLIS, 0L),
            activatedAt = subPrefs.getLong(KEY_ACTIVATED_AT, 0L),
            purchaseType = subPrefs.getString(KEY_PURCHASE_TYPE, "subscription") ?: "subscription"
        )
    }

    override fun saveSubscription(
        planType: String,
        paymentId: String,
        durationDays: Int,
        activatedAt: Long,
        startDate: String?,
        expiryMillis: Long?,
        expiryDate: String?,
        purchaseType: String?
    ) {
        val isLifetime = purchaseType == "lifetime" ||
                planType.contains("Lifetime", ignoreCase = true) ||
                durationDays >= 3650

        val resolvedActivatedAt = if (activatedAt > 0L) activatedAt else System.currentTimeMillis()
        val resolvedExpiryMillis = expiryMillis ?: if (isLifetime) 0L else (resolvedActivatedAt + (durationDays.toLong() * 24 * 60 * 60 * 1000))

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val resolvedStartDate = startDate ?: dateFormat.format(Date(resolvedActivatedAt))
        val resolvedExpiryDate = expiryDate ?: if (isLifetime) "Never Expires (Lifetime Access)" else dateFormat.format(Date(resolvedExpiryMillis))
        val resolvedPurchaseType = purchaseType ?: if (isLifetime) "lifetime" else "subscription"

        subPrefs.edit()
            .putBoolean(KEY_IS_PREMIUM, true)
            .putString(KEY_PLAN_TYPE, planType)
            .putString(KEY_START_DATE, resolvedStartDate)
            .putString(KEY_EXPIRY_DATE, resolvedExpiryDate)
            .putLong(KEY_EXPIRY_MILLIS, resolvedExpiryMillis)
            .putLong(KEY_ACTIVATED_AT, resolvedActivatedAt)
            .putString(KEY_PURCHASE_TYPE, resolvedPurchaseType)
            .putString(KEY_PAYMENT_ID, paymentId)
            .apply()

        setPremiumState(true)
    }

    override fun setPremiumState(isPremium: Boolean) {
        premiumPrefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply()
        com.magic.calculator.core.logic.PremiumManager.setPremiumState(context, isPremium)
    }

    override fun clearSubscription() {
        subPrefs.edit().clear().apply()
        setPremiumState(false)
    }
}
