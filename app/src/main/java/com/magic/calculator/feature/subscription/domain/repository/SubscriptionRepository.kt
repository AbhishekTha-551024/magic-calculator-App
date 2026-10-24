package com.magic.calculator.feature.subscription.domain.repository

import com.magic.calculator.feature.subscription.domain.model.PaymentRecord
import com.magic.calculator.feature.subscription.domain.model.SubscriptionDetails
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain contract for secure subscription verification, purchases, and state management.
 */
interface SubscriptionRepository {
    val isPremium: StateFlow<Boolean>

    fun isPremiumUser(): Boolean
    fun getSubscriptionDetails(): SubscriptionDetails
    fun saveSubscriptionLocally(
        planType: String,
        paymentId: String,
        durationDays: Int = 365,
        activatedAt: Long = System.currentTimeMillis(),
        startDate: String? = null,
        expiryMillis: Long? = null,
        expiryDate: String? = null,
        purchaseType: String? = null
    )
    fun clearSubscription()

    suspend fun syncAndRestorePremiumStatus(): Result<String>
    suspend fun createPaymentOrder(amount: Int, planType: String, userId: String?): Result<String>
    suspend fun verifyAndUnlockPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        userId: String?,
        userEmail: String,
        planName: String,
        durationDays: Int
    ): Result<String>

    suspend fun fetchPaymentHistory(): Result<List<PaymentRecord>>
    suspend fun ensureUserDocumentCreated(): Result<Unit>
}
