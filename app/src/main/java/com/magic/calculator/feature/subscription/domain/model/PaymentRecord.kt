package com.magic.calculator.feature.subscription.domain.model

/**
 * Domain entity representing a completed payment transaction.
 */
data class PaymentRecord(
    val orderId: String = "",
    val paymentId: String = "",
    val planId: String = "",
    val planName: String = "",
    val amount: Int = 0,
    val currency: String = "INR",
    val method: String = "UPI",
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis(),
    val expiryDate: String = "",
    val startDate: String = ""
)

