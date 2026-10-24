package com.magic.calculator.feature.subscription.data.datasource

import com.magic.calculator.core.network.VercelRazorpayHelper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

interface PaymentRemoteDataSource {
    suspend fun createOrder(amount: Int, planType: String, userId: String?): Result<String>
    suspend fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        userId: String?
    ): Result<String>
}

class VercelPaymentRemoteDataSource : PaymentRemoteDataSource {

    override suspend fun createOrder(amount: Int, planType: String, userId: String?): Result<String> =
        suspendCoroutine { cont ->
            VercelRazorpayHelper.createOrder(
                amount = amount,
                planType = planType,
                userId = userId
            ) { success, orderId, error ->
                if (success && !orderId.isNullOrEmpty()) {
                    cont.resume(Result.success(orderId))
                } else {
                    cont.resume(Result.failure(Exception(error ?: "Failed to generate payment order")))
                }
            }
        }

    override suspend fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        userId: String?
    ): Result<String> =
        suspendCoroutine { cont ->
            VercelRazorpayHelper.verifyPayment(
                orderId = orderId,
                paymentId = paymentId,
                signature = signature,
                planType = planType,
                userId = userId
            ) { success, message ->
                if (success) {
                    cont.resume(Result.success(message))
                } else {
                    cont.resume(Result.failure(Exception(message)))
                }
            }
        }
}
