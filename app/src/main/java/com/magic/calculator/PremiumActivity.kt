package com.magic.calculator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.magic.calculator.di.appContainer
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.PremiumManager
import com.magic.calculator.logic.SubscriptionManager
import com.magic.calculator.network.VercelRazorpayHelper
import com.magic.calculator.ui.util.MagicToast
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

/**
 * PremiumActivity — 3-Tier Subscription Pricing:
 * 1. Weekly   (₹149  / 7 Days)
 * 2. Monthly  (₹299  / 30 Days)   ← DEFAULT / MOST POPULAR
 * 3. Yearly   (₹1999 / 365 Days)  ← 1-YEAR VIP ACCESS
 */
class PremiumActivity : AppCompatActivity(), PaymentResultWithDataListener {

    private var selectedPlan = "MONTHLY"
    private var selectedAmountInINR = 299 // Default monthly amount
    private var pendingOrderId: String? = null
    private var isOrderInProgress = false
    private val safetyHandler = Handler(Looper.getMainLooper())
    private val resetTimeoutRunnable = Runnable { resetPurchaseButton() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val cardWeekly = findViewById<MaterialCardView>(R.id.cardWeekly)
        val cardYearly = findViewById<MaterialCardView>(R.id.cardYearly)
        val cardLifetime = findViewById<MaterialCardView>(R.id.cardLifetime)
        val tvRestorePurchases = findViewById<TextView>(R.id.tvRestorePurchases)
        val btnPurchase = findViewById<Button>(R.id.btnPurchase)

        btnClose.setOnClickListener { finish() }

        val allCards = listOf(cardWeekly, cardYearly, cardLifetime)

        // Highlight default selection (Monthly Pass ₹299)
        highlightSelectedCard(cardYearly, allCards, "#00E5FF")
        btnPurchase.text = "Get Monthly Pass for ₹299"

        // ── Plan Selectors ──────────────────────────────────────────────────
        cardWeekly.setOnClickListener {
            selectedPlan = "WEEKLY"
            selectedAmountInINR = 149
            highlightSelectedCard(cardWeekly, allCards, "#00E5FF")
            btnPurchase.text = "Get Weekly Pass for ₹149"
        }

        cardYearly.setOnClickListener {
            selectedPlan = "MONTHLY"
            selectedAmountInINR = 299
            highlightSelectedCard(cardYearly, allCards, "#00E5FF")
            btnPurchase.text = "Get Monthly Pass for ₹299"
        }

        cardLifetime.setOnClickListener {
            selectedPlan = "YEARLY"
            selectedAmountInINR = 1999
            highlightSelectedCard(cardLifetime, allCards, "#10B981")
            btnPurchase.text = "Get 1 Year Pass for ₹1999"
        }

        // ── Task 3: Restore Purchases Logic ─────────────────────────────────
        tvRestorePurchases.setOnClickListener {
            tvRestorePurchases.isEnabled = false
            MagicToast.show(this, "Restoring purchases from server...")

            SubscriptionManager.syncAndRestorePremiumStatus(this) { success, message ->
                runOnUiThread {
                    tvRestorePurchases.isEnabled = true
                    MagicToast.show(this, message, isLong = true)
                    if (success) {
                        finish() // Return to dashboard upon successful restore
                    }
                }
            }
        }

        // Razorpay recommends preloading checkout for faster launch
        Checkout.preload(applicationContext)

        // ── FIX 4: Prevent Duplicate Orders / Fast Double-Taps ───────────────
        btnPurchase.setOnClickListener {
            if (isOrderInProgress) return@setOnClickListener
            isOrderInProgress = true
            btnPurchase.isEnabled = false
            btnPurchase.text = "Creating order..."

            // Safety 25s timeout in case of network stall
            safetyHandler.postDelayed(resetTimeoutRunnable, 25000)

            val settingsManager = appContainer.settingsManager
            val phone = settingsManager.userPhoneNumber.ifEmpty { AuthManager.currentUser?.phoneNumber ?: "" }
            if (phone.length < 10) {
                promptForPhoneNumber {
                    startPurchaseFlow()
                }
            } else {
                startPurchaseFlow()
            }
        }
    }

    // ── FIX 5: Handle "user backgrounds app mid-payment" ─────────────────────
    override fun onResume() {
        super.onResume()
        if (PremiumManager.checkIsPremium(this)) {
            finish()
            return
        }
        val currentUserId = AuthManager.currentUid
        if (!currentUserId.isNullOrEmpty()) {
            SubscriptionManager.syncAndRestorePremiumStatus(this) { success, _ ->
                if (success && !isFinishing) {
                    runOnUiThread { finish() }
                }
            }
        }
    }

    private fun promptForPhoneNumber(onConfirmed: () -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "10-digit mobile number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#777777"))
            setBackgroundColor(Color.parseColor("#1A1A1E"))
            setPadding(40, 30, 40, 30)
        }

        val container = android.widget.FrameLayout(this).apply {
            setPadding(50, 20, 50, 10)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Mobile Number for UPI")
            .setMessage("PhonePe, Google Pay & Paytm require a 10-digit mobile number to open UPI:")
            .setView(container)
            .setPositiveButton("Continue") { _, _ ->
                val phone = input.text.toString().trim().filter { it.isDigit() }.takeLast(10)
                if (phone.length == 10) {
                    val settingsManager = com.magic.calculator.logic.SettingsManager(this)
                    settingsManager.userPhoneNumber = phone
                    onConfirmed()
                } else {
                    MagicToast.show(this, "Please enter a valid 10-digit mobile number.")
                    resetPurchaseButton()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                resetPurchaseButton()
            }
            .setCancelable(false)
            .show()
    }

    private fun highlightSelectedCard(
        activeCard: MaterialCardView,
        allCards: List<MaterialCardView>,
        strokeHexColor: String
    ) {
        val colorInt = Color.parseColor(strokeHexColor)
        val bgAlphaColor = Color.parseColor(
            when (strokeHexColor) {
                "#FFD700" -> "#22FFD700"
                "#10B981" -> "#1A10B981"
                else      -> "#1A00E5FF"
            }
        )

        for (card in allCards) {
            if (card == activeCard) {
                card.setCardBackgroundColor(bgAlphaColor)
                card.strokeWidth = 4
                card.strokeColor = colorInt
            } else {
                card.setCardBackgroundColor(Color.parseColor("#1A1A1E"))
                card.strokeWidth = 2
                card.strokeColor = Color.parseColor("#333333")
            }
        }
    }

    /**
     * Step 1: Call Vercel Backend /api/create-order to get Razorpay Order ID.
     */
    private fun startPurchaseFlow() {
        val currentUserId = AuthManager.currentUid
        VercelRazorpayHelper.createOrder(
            amount = selectedAmountInINR,
            planType = selectedPlan,
            userId = currentUserId
        ) { success, orderId, error ->
            runOnUiThread {
                if (!success || orderId == null) {
                    MagicToast.show(this, error ?: "Could not generate payment order.", isLong = true)
                    resetPurchaseButton()
                    return@runOnUiThread
                }

                pendingOrderId = orderId
                // FIX 2: Check POST_NOTIFICATIONS runtime permission before launch on Android 13+
                checkNotificationPermissionAndProceed {
                    openRazorpayCheckout(orderId, selectedAmountInINR)
                }
            }
        }
    }

    // ── FIX 2: Request POST_NOTIFICATIONS runtime permission on Android 13+ ──
    private fun checkNotificationPermissionAndProceed(onReady: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        onReady()
    }

    /**
     * Step 2: Open Razorpay Payment Sheet with generated orderId.
     */
    private fun openRazorpayCheckout(orderId: String, amountInINR: Int) {
        val checkout = Checkout()
        checkout.setKeyID(VercelRazorpayHelper.RAZORPAY_KEY_ID)
        checkout.setImage(R.drawable.ic_magic)

        val planLabel = when (selectedPlan) {
            "WEEKLY"   -> "Weekly Pass (₹149)"
            "MONTHLY"  -> "Monthly Pass (₹299)"
            "YEARLY"   -> "1 Year VIP Pass (₹1999)"
            "LIFETIME" -> "1 Year VIP Pass (₹1999)"
            else       -> "Monthly Pass (₹299)"
        }

        try {
            val settingsManager = appContainer.settingsManager
            val phone = settingsManager.userPhoneNumber.ifEmpty {
                AuthManager.currentUser?.phoneNumber ?: ""
            }

            val options = JSONObject().apply {
                put("name", "Magic Calculator Pro")
                put("description", planLabel)
                put("order_id", orderId)
                put("currency", "INR")
                put("amount", amountInINR * 100) // Amount in paise
                put("theme.color", if (selectedPlan == "LIFETIME") "#10B981" else if (selectedPlan == "TEST") "#FFD700" else "#00E5FF")
                put("send_sms_hash", true)

                // ── Explicitly Force UPI Intent Flow (Top PSP apps: PhonePe, GPay, Paytm) ──
                val upiObj = JSONObject().apply {
                    put("flow", "intent")
                }
                val methodObj = JSONObject().apply {
                    put("upi", upiObj)
                    put("card", true)
                    put("netbanking", true)
                    put("wallet", true)
                }
                put("method", methodObj)

                val prefill = JSONObject().apply {
                    val user = AuthManager.currentUser
                    user?.email?.let { put("email", it) }
                    if (phone.isNotEmpty()) {
                        put("contact", phone)
                    }
                    put("method", "upi")
                }
                put("prefill", prefill)

                val retryObj = JSONObject().apply {
                    put("enabled", true)
                    put("max_count", 4)
                }
                put("retry", retryObj)
            }

            Log.d("RazorpayDiagnostic", "=== RAZORPAY CHECKOUT DIAGNOSTICS ===")
            Log.d("RazorpayDiagnostic", "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            Log.d("RazorpayDiagnostic", "KeyID: ***${VercelRazorpayHelper.RAZORPAY_KEY_ID.takeLast(6)}")
            Log.d("RazorpayDiagnostic", "OrderID: $orderId | AmountPaise: ${amountInINR * 100} | Plan: $selectedPlan")
            Log.d("RazorpayDiagnostic", "Options Payload: ${options.toString()}")

            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e("RazorpayDiagnostic", "CRITICAL: Exception launching Razorpay Checkout on API ${Build.VERSION.SDK_INT}", e)
            runOnUiThread {
                resetPurchaseButton()
                AlertDialog.Builder(this)
                    .setTitle("Payment Sheet Error")
                    .setMessage("Could not open payment sheet on Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}).\n\nDetails: ${e.localizedMessage ?: "Unknown initialization error"}")
                    .setPositiveButton("Retry Payment") { _, _ ->
                        startPurchaseFlow()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    /**
     * Step 3: Callback on Payment Success - Razorpay confirmed payment.
     * FIX 3 (SECURITY): Premium unlocks ONLY AFTER server signature verification succeeds!
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)

        try {
            if (razorpayPaymentId.isNullOrEmpty()) {
                MagicToast.show(this, "Payment ID missing. Contact support.", isLong = true)
                resetPurchaseButton()
                com.magic.calculator.logic.CrashReportingManager.logNonFatal(
                    IllegalArgumentException("onPaymentSuccess invoked with null/empty razorpayPaymentId"),
                    "Payment success callback missing paymentId"
                )
                return
            }

            val orderId = paymentData?.orderId ?: pendingOrderId ?: ""
            val signature = paymentData?.signature ?: ""

            com.magic.calculator.logic.CrashReportingManager.log("Razorpay onPaymentSuccess: orderId=$orderId paymentId=$razorpayPaymentId")

            val (planName, durationDays) = when (selectedPlan) {
                "WEEKLY"   -> "Weekly Pass (₹149)" to 7
                "MONTHLY"  -> "Monthly Pass (₹299)" to 30
                "YEARLY"   -> "1 Year VIP Pass (₹1999)" to 365
                "LIFETIME" -> "1 Year VIP Pass (₹1999)" to 365
                else       -> "Monthly Pass (₹299)" to 30
            }

            val btnPurchase = findViewById<Button>(R.id.btnPurchase)
            btnPurchase.isEnabled = false
            btnPurchase.text = "Verifying payment with server..."

            // ── FIX 3 (SECURITY): Verify with Vercel Backend FIRST before granting Pro ──
            verifyAndUnlock(
                paymentId = razorpayPaymentId,
                orderId = orderId,
                signature = signature,
                planName = planName,
                durationDays = durationDays
            )
        } catch (e: Throwable) {
            com.magic.calculator.logic.CrashReportingManager.logNonFatal(
                e,
                "Exception in PremiumActivity onPaymentSuccess"
            )
            resetPurchaseButton()
        }
    }

    private fun verifyAndUnlock(
        paymentId: String,
        orderId: String,
        signature: String,
        planName: String,
        durationDays: Int
    ) {
        val currentUser = AuthManager.currentUser
        val userId = currentUser?.uid
        val userEmail = currentUser?.email ?: ""

        VercelRazorpayHelper.verifyPayment(
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            planType = selectedPlan,
            userId = userId
        ) { success, message ->
            runOnUiThread {
                if (success) {
                    val nowMillis = System.currentTimeMillis()
                    val expiryMillis = if (selectedPlan == "LIFETIME") 0L else nowMillis + (durationDays.toLong() * 24 * 60 * 60 * 1000)

                    // 1. Grant Premium locally
                    SubscriptionManager.saveSubscription(this, planName, paymentId, durationDays)
                    PremiumManager.setPremiumState(this, true)

                    // 2. Commit verified license to Cloud Firestore
                    if (!userId.isNullOrEmpty()) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val paymentDataMap = hashMapOf(
                            "isPremium"       to true,
                            "isProUser"       to true,
                            "planType"        to planName,
                            "transactionId"   to paymentId,
                            "paymentId"       to paymentId,
                            "lastOrderId"     to orderId,
                            "activatedAt"     to nowMillis,
                            "expiryMillis"    to expiryMillis,
                            "lastPaymentDate" to nowMillis,
                            "lastSyncedAt"    to nowMillis,
                            "lastSeen"        to nowMillis,
                            "email"           to userEmail
                        )

                        db.collection("users").document(userId)
                            .set(paymentDataMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("PremiumActivity", "✅ Firestore verified write SUCCESS for userId=$userId")
                            }
                            .addOnFailureListener { e ->
                                Log.e("PremiumActivity", "❌ Firestore write failed: ${e.message}", e)
                            }
                    }

                    MagicToast.show(this, "🎉 Payment Verified! Pro features unlocked.", isLong = true)
                    finish()
                } else {
                    // Verification Failed: DO NOT unlock Pro
                    Log.e("PremiumActivity", "❌ CRITICAL: Server verification FAILED! paymentId=$paymentId orderId=$orderId error=$message")
                    showVerificationFailedDialog(paymentId, orderId, signature, planName, durationDays, message)
                }
            }
        }
    }

    private fun showVerificationFailedDialog(
        paymentId: String,
        orderId: String,
        signature: String,
        planName: String,
        durationDays: Int,
        errorReason: String?
    ) {
        resetPurchaseButton()
        AlertDialog.Builder(this)
            .setTitle("Verification Pending")
            .setMessage("Payment received by Razorpay (ID: $paymentId), but backend signature verification could not be completed.\n\nReason: ${errorReason ?: "Network error"}\n\nPlease tap Retry to verify again.")
            .setPositiveButton("Retry Verification") { _, _ ->
                val btnPurchase = findViewById<Button>(R.id.btnPurchase)
                btnPurchase.isEnabled = false
                btnPurchase.text = "Retrying verification..."
                verifyAndUnlock(paymentId, orderId, signature, planName, durationDays)
            }
            .setNegativeButton("Close", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Step 4: Callback on Payment Error or User Cancellation (FIX 1)
     */
    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
        Log.e("PremiumActivity", "Razorpay onPaymentError: code=$code description=$description orderId=${paymentData?.orderId} paymentId=${paymentData?.paymentId}")
        
        if (code == Checkout.PAYMENT_CANCELED) {
            com.magic.calculator.logic.CrashReportingManager.log("Payment flow cancelled by user (code $code)")
        } else {
            com.magic.calculator.logic.CrashReportingManager.logNonFatal(
                Exception("Razorpay payment error: code=$code description=$description orderId=${paymentData?.orderId}"),
                "Payment failed on Razorpay checkout"
            )
        }

        runOnUiThread {
            resetPurchaseButton()
            if (code == Checkout.PAYMENT_CANCELED) {
                MagicToast.show(this, "Payment cancelled")
            } else {
                val errorMsg = description?.takeIf { it.isNotBlank() } ?: "Payment failed (Error code $code)"
                MagicToast.show(this, errorMsg, isLong = true)
            }
        }
    }

    private fun resetPurchaseButton() {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
        isOrderInProgress = false
        val btnPurchase = findViewById<Button>(R.id.btnPurchase)
        btnPurchase.isEnabled = true
        btnPurchase.text = when (selectedPlan) {
            "WEEKLY"   -> "Get Weekly Pass for ₹149"
            "MONTHLY"  -> "Get Monthly Pass for ₹299"
            "YEARLY"   -> "Get 1 Year Pass for ₹1999"
            "LIFETIME" -> "Get 1 Year Pass for ₹1999"
            else       -> "Get Monthly Pass for ₹299"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
    }
}
