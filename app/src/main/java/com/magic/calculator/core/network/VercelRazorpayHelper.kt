package com.magic.calculator.core.network

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.magic.calculator.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * VercelRazorpayHelper
 *
 * Handles HTTP requests to the live Vercel Razorpay backend (Base URL: https://razor-pay-backend-tau.vercel.app).
 * Features automatic high-availability fallback to Firebase Cloud Functions (asia-south1)
 * if Vercel encounters cold starts, downtime, or network failures.
 */
object VercelRazorpayHelper {

    private const val TAG = "VercelRazorpayHelper"

    // Live Vercel Backend URL
    const val BASE_URL = "https://razor-pay-backend-tau.vercel.app"

    /**
     * Razorpay Key ID from BuildConfig.
     * Set via buildConfigField in app/build.gradle.
     */
    val RAZORPAY_KEY_ID: String
        get() = BuildConfig.RAZORPAY_KEY_ID

    // OkHttpClient with proper timeouts â€” prevents payment hanging on slow/down servers
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Call /api/create-order on Vercel Backend to generate a Razorpay Order ID.
     * Falls back to Firebase Cloud Functions if Vercel is unreachable or fails.
     */
    fun createOrder(
        amount: Int,
        planType: String = "MONTHLY",
        userId: String? = null,
        onResult: (success: Boolean, orderId: String?, error: String?) -> Unit
    ) {
        val url = "$BASE_URL/api/create-order"

        val jsonBody = JSONObject().apply {
            put("amount", amount)
            put("planType", planType)
            put("currency", "INR")
            if (!userId.isNullOrEmpty()) {
                put("userId", userId)
                put("uid", userId)
            }
        }

        val requestBody = jsonBody.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Vercel create-order failed (${e.message}). Falling back to Firebase Cloud Functions...", e)
                createOrderFirebaseFallback(planType, onResult)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseString = response.body?.string()
                try {
                    if (response.isSuccessful && !responseString.isNullOrEmpty()) {
                        val json = JSONObject(responseString)
                        val isSuccess = json.optBoolean("success", false)
                        val orderId = json.optString("orderId", "")

                        if (isSuccess && orderId.isNotEmpty()) {
                            onResult(true, orderId, null)
                            return
                        }
                    }

                    // If Vercel returned an error or HTTP non-200, try Firebase Functions fallback
                    Log.w(TAG, "Vercel create-order response unsuccessful (${response.code}). Falling back to Firebase Functions...")
                    createOrderFirebaseFallback(planType, onResult)
                } catch (e: Exception) {
                    Log.w(TAG, "Parsing error on Vercel response. Falling back to Firebase Functions...", e)
                    createOrderFirebaseFallback(planType, onResult)
                }
            }
        })
    }

    /**
     * Fallback to Firebase Cloud Functions for creating a Razorpay order.
     */
    private fun createOrderFirebaseFallback(
        planType: String,
        onResult: (success: Boolean, orderId: String?, error: String?) -> Unit
    ) {
        try {
            val functions = FirebaseFunctions.getInstance("asia-south1")
            val data = hashMapOf(
                "planType" to planType
            )

            functions.getHttpsCallable("createRazorpayOrder")
                .call(data)
                .addOnSuccessListener { result ->
                    val resData = result.data as? Map<*, *>
                    val orderId = resData?.get("orderId") as? String
                    if (!orderId.isNullOrEmpty()) {
                        Log.i(TAG, "âœ… Firebase Cloud Functions fallback createOrder SUCCESS: orderId=$orderId")
                        onResult(true, orderId, null)
                    } else {
                        onResult(false, null, "Failed to create order on backup server.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "âŒ Firebase Cloud Functions fallback createOrder FAILED", e)
                    onResult(false, null, e.localizedMessage ?: "Payment service currently unavailable. Please check your connection.")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initializing Firebase Functions fallback", e)
            onResult(false, null, e.localizedMessage ?: "Payment service currently unavailable.")
        }
    }

    /**
     * Call /api/verify-payment on Vercel Backend to verify Razorpay signature server-side.
     * Falls back to Firebase Cloud Functions if Vercel is unreachable or fails.
     */
    fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String = "MONTHLY",
        userId: String? = null,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val url = "$BASE_URL/api/verify-payment"

        val jsonBody = JSONObject().apply {
            put("razorpay_order_id", orderId)
            put("razorpay_payment_id", paymentId)
            put("razorpay_signature", signature)
            put("planType", planType)
            if (!userId.isNullOrEmpty()) {
                put("userId", userId)
                put("uid", userId)
            }
        }

        val requestBody = jsonBody.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Vercel verify-payment failed (${e.message}). Falling back to Firebase Cloud Functions...", e)
                verifyPaymentFirebaseFallback(orderId, paymentId, signature, planType, onResult)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseString = response.body?.string()
                try {
                    if (!responseString.isNullOrEmpty()) {
                        val json = JSONObject(responseString)
                        val status = json.optString("status", "")
                        val isSuccess = json.optBoolean("success", false) || status == "success"

                        if (isSuccess) {
                            val msg = json.optString("message", "Payment verified successfully!")
                            onResult(true, msg)
                            return
                        }
                    }

                    // If Vercel returned failure or HTTP error, try Firebase Functions fallback
                    Log.w(TAG, "Vercel verify-payment response unsuccessful (${response.code}). Falling back to Firebase Functions...")
                    verifyPaymentFirebaseFallback(orderId, paymentId, signature, planType, onResult)
                } catch (e: Exception) {
                    Log.w(TAG, "Parsing error on Vercel verify response. Falling back to Firebase Functions...", e)
                    verifyPaymentFirebaseFallback(orderId, paymentId, signature, planType, onResult)
                }
            }
        })
    }

    /**
     * Fallback to Firebase Cloud Functions for verifying Razorpay signature.
     */
    private fun verifyPaymentFirebaseFallback(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        try {
            val functions = FirebaseFunctions.getInstance("asia-south1")
            val data = hashMapOf(
                "razorpayOrderId" to orderId,
                "razorpayPaymentId" to paymentId,
                "razorpaySignature" to signature,
                "planType" to planType
            )

            functions.getHttpsCallable("verifyRazorpayPayment")
                .call(data)
                .addOnSuccessListener { result ->
                    val resData = result.data as? Map<*, *>
                    val isSuccess = resData?.get("success") == true
                    val msg = resData?.get("message") as? String ?: "Payment verified successfully!"
                    if (isSuccess) {
                        Log.i(TAG, "âœ… Firebase Cloud Functions fallback verifyPayment SUCCESS")
                        onResult(true, msg)
                    } else {
                        onResult(false, msg)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "âŒ Firebase Cloud Functions fallback verifyPayment FAILED", e)
                    onResult(false, e.localizedMessage ?: "Payment verification failed.")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in Firebase Functions verify fallback", e)
            onResult(false, e.localizedMessage ?: "Payment verification failed.")
        }
    }
}

