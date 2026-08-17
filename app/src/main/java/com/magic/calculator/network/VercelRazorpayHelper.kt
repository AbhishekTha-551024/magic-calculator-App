package com.magic.calculator.network

import android.util.Log
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
 * Handles HTTP requests to the live Vercel Razorpay backend:
 * Base URL: https://razor-pay-backend-tau.vercel.app
 */
object VercelRazorpayHelper {

    private const val TAG = "VercelRazorpayHelper"

    // Live Vercel Backend URL
    const val BASE_URL = "https://razor-pay-backend-tau.vercel.app"

    /**
     * Razorpay Key ID from BuildConfig.
     * Set via buildConfigField in app/build.gradle.
     * For production: use your live key (rzp_live_...) — never hardcode it here.
     */
    val RAZORPAY_KEY_ID: String
        get() = BuildConfig.RAZORPAY_KEY_ID

    // OkHttpClient with proper timeouts — prevents payment hanging on slow/down servers
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /**
     * Call /api/create-order on Vercel Backend to generate a Razorpay Order ID.
     *
     * @param amount Amount in INR (e.g. 99 for ₹99, 199 for ₹199, 299 for ₹299)
     * @param planType Plan identifier: WEEKLY, MONTHLY, LIFETIME
     * @param userId Current Firebase user UID (optional)
     * @param onResult Callback with success status, orderId, and optional error message.
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
                Log.e(TAG, "Network error calling create-order", e)
                onResult(false, null, e.message ?: "Network error. Please check internet connection.")
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
                        } else {
                            val errorMsg = json.optString("error", "Failed to create order.")
                            onResult(false, null, errorMsg)
                        }
                    } else {
                        var detailedError: String? = null
                        if (!responseString.isNullOrEmpty()) {
                            try {
                                val errJson = JSONObject(responseString)
                                detailedError = errJson.optString("error")
                            } catch (_: Exception) {}
                        }
                        val finalMsg = if (!detailedError.isNullOrEmpty()) {
                            "Vercel Error (${response.code}): $detailedError\n(Check RAZORPAY_KEY_ID & RAZORPAY_KEY_SECRET in Vercel Dashboard & click Redeploy)"
                        } else {
                            "Server error (${response.code})."
                        }
                        Log.e(TAG, "HTTP error on create-order: ${response.code} $responseString")
                        onResult(false, null, finalMsg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parsing error on create-order response", e)
                    onResult(false, null, "Invalid server response.")
                }
            }
        })
    }

    /**
     * Call /api/verify-payment on Vercel Backend to verify Razorpay signature server-side.
     *
     * @param orderId Razorpay Order ID
     * @param paymentId Razorpay Payment ID
     * @param signature Razorpay Signature returned by Checkout SDK
     * @param planType Plan identifier: WEEKLY, MONTHLY, LIFETIME
     * @param userId Current Firebase user UID (optional)
     * @param onResult Callback returning (success, message)
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
                Log.e(TAG, "Network error calling verify-payment", e)
                onResult(false, e.message ?: "Network error verifying payment.")
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
                        } else {
                            val msg = json.optString("message", json.optString("error", "Payment verification failed."))
                            onResult(false, msg)
                        }
                    } else {
                        onResult(false, "Empty response from server.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parsing error on verify-payment response", e)
                    onResult(false, "Failed to parse verification response.")
                }
            }
        })
    }
}
