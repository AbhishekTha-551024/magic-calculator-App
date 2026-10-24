package com.magic.calculator.feature.subscription.domain.model

/**
 * Domain entity representing subscription state and metadata.
 */
data class SubscriptionDetails(
    val isPremium: Boolean = false,
    val planType: String = "Free Tier",
    val startDate: String = "N/A",
    val expiryDate: String = "N/A",
    val paymentId: String = "N/A",
    val expiryMillis: Long = 0L,
    val activatedAt: Long = 0L,
    val purchaseType: String = "subscription"
)

