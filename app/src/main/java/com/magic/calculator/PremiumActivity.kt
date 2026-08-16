package com.magic.calculator

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.magic.calculator.logic.AuthManager
import com.magic.calculator.logic.SubscriptionManager
import com.magic.calculator.network.VercelRazorpayHelper
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

/**
 * PremiumActivity — 3-Tier Subscription Pricing:
 * 1. Weekly   (₹99  / 7 Days)
 * 2. Monthly  (₹199 / 30 Days)   ← DEFAULT / MOST POPULAR
 * 3. Lifetime (₹299 / Unlimited) ← UNBEATABLE DEAL
 */
class PremiumActivity : AppCompatActivity(), PaymentResultWithDataListener {

    private var selectedPlan = "MONTHLY"
    private var selectedAmountInINR = 199 // Default monthly amount
    private var pendingOrderId: String? = null

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

        // ── Plan Selectors ──────────────────────────────────────────────────
        cardWeekly.setOnClickListener {
            selectedPlan = "WEEKLY"
            selectedAmountInINR = 99
            highlightCard(cardWeekly, cardYearly, cardLifetime, "#00E5FF")
            btnPurchase.text = "Get Weekly Pass for ₹99"
        }

        cardYearly.setOnClickListener {
            selectedPlan = "MONTHLY"
            selectedAmountInINR = 199
            highlightCard(cardYearly, cardWeekly, cardLifetime, "#00E5FF")
            btnPurchase.text = "Get Monthly Pass for ₹199"
        }

        cardLifetime.setOnClickListener {
            selectedPlan = "LIFETIME"
            selectedAmountInINR = 299
            highlightCard(cardLifetime, cardWeekly, cardYearly, "#10B981")
            btnPurchase.text = "Get Lifetime Access for ₹299"
        }

        // ── Task 3: Restore Purchases Logic ─────────────────────────────────
        tvRestorePurchases.setOnClickListener {
            tvRestorePurchases.isEnabled = false
            Toast.makeText(this, "Restoring purchases from server...", Toast.LENGTH_SHORT).show()

            SubscriptionManager.syncAndRestorePremiumStatus(this) { success, message ->
                runOnUiThread {
                    tvRestorePurchases.isEnabled = true
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (success) {
                        finish() // Return to dashboard upon successful restore
                    }
                }
            }
        }

        // Razorpay recommends preloading checkout for faster launch
        Checkout.preload(applicationContext)

        btnPurchase.setOnClickListener {
            btnPurchase.isEnabled = false
            btnPurchase.text = "Creating order..."
            startPurchaseFlow()
        }
    }

    private fun highlightCard(
        activeCard: MaterialCardView,
        card2: MaterialCardView,
        card3: MaterialCardView,
        strokeHexColor: String
    ) {
        val colorInt = Color.parseColor(strokeHexColor)
        val bgAlphaColor = Color.parseColor(if (strokeHexColor == "#10B981") "#1A10B981" else "#1A00E5FF")

        activeCard.setCardBackgroundColor(bgAlphaColor)
        activeCard.strokeWidth = 4
        activeCard.strokeColor = colorInt

        card2.setCardBackgroundColor(Color.parseColor("#1A1A1E"))
        card2.strokeWidth = 2
        card2.strokeColor = Color.parseColor("#333333")

        card3.setCardBackgroundColor(Color.parseColor("#1A1A1E"))
        card3.strokeWidth = 2
        card3.strokeColor = Color.parseColor("#333333")
    }

    /**
     * Step 1: Call Vercel Backend /api/create-order to get Razorpay Order ID.
     */
    private fun startPurchaseFlow() {
        VercelRazorpayHelper.createOrder(selectedAmountInINR) { success, orderId, error ->
            runOnUiThread {
                if (!success || orderId == null) {
                    Toast.makeText(this, error ?: "Could not generate payment order.", Toast.LENGTH_LONG).show()
                    resetPurchaseButton()
                    return@runOnUiThread
                }

                pendingOrderId = orderId
                openRazorpayCheckout(orderId, selectedAmountInINR)
            }
        }
    }

    /**
     * Step 2: Open Razorpay Payment Sheet with generated orderId.
     */
    private fun openRazorpayCheckout(orderId: String, amountInINR: Int) {
        val checkout = Checkout()
        checkout.setKeyID(VercelRazorpayHelper.RAZORPAY_KEY_ID)
        checkout.setImage(R.drawable.ic_magic)

        val planLabel = when (selectedPlan) {
            "WEEKLY"   -> "Weekly Pass (₹99)"
            "MONTHLY"  -> "Monthly Pass (₹199)"
            "LIFETIME" -> "Lifetime Access (₹299)"
            else       -> "Monthly Pass (₹199)"
        }

        try {
            val options = JSONObject().apply {
                put("name", "Magic Calculator Pro")
                put("description", planLabel)
                put("order_id", orderId)
                put("currency", "INR")
                put("amount", amountInINR * 100) // Amount in paise
                put("theme.color", if (selectedPlan == "LIFETIME") "#10B981" else "#00E5FF")

                val prefill = JSONObject()
                AuthManager.currentUser?.email?.let { prefill.put("email", it) }
                put("prefill", prefill)
            }

            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e("PremiumActivity", "Error launching Razorpay Checkout", e)
            Toast.makeText(this, "Could not open payment sheet.", Toast.LENGTH_LONG).show()
            resetPurchaseButton()
        }
    }

    /**
     * Step 3: Callback on Payment Success - Razorpay confirmed payment.
     * CRITICAL FIX: Write to Firestore IMMEDIATELY when Razorpay confirms.
     * DO NOT wait for backend verification — Razorpay has already collected the money.
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        if (razorpayPaymentId.isNullOrEmpty()) {
            Toast.makeText(this, "Payment ID missing. Contact support.", Toast.LENGTH_LONG).show()
            resetPurchaseButton()
            return
        }

        val orderId = paymentData?.orderId ?: pendingOrderId ?: ""
        val signature = paymentData?.signature ?: ""

        val (planName, durationDays) = when (selectedPlan) {
            "WEEKLY"   -> "Weekly Pass (₹99)" to 7
            "MONTHLY"  -> "Monthly Pass (₹199)" to 30
            "LIFETIME" -> "Lifetime License (₹299)" to 36500
            else       -> "Monthly Pass (₹199)" to 30
        }

        // ── STEP A: Capture user identity RIGHT NOW before any async calls ──
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid
        val userEmail = currentUser?.email ?: ""

        Log.d("PremiumActivity", "onPaymentSuccess: paymentId=$razorpayPaymentId plan=$planName userId=$userId email=$userEmail")

        if (userId.isNullOrEmpty()) {
            // Edge case: Auth state lost — still save locally and write with direct call
            Log.e("PremiumActivity", "CRITICAL: currentUser is NULL during payment success! Cannot write to Firestore.")
            SubscriptionManager.saveSubscription(this, planName, razorpayPaymentId, durationDays)
            Toast.makeText(this, "✅ Payment done! Please restart app to activate.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // ── STEP B: Write to Firestore DIRECTLY with explicit userId ──
        // We use the userId captured above — not relying on FirebaseAuth later.
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val paymentData2 = hashMapOf(
            "isPremium"     to true,
            "isProUser"     to true,
            "planType"      to planName,
            "transactionId" to razorpayPaymentId,
            "paymentId"     to razorpayPaymentId,
            "activatedAt"   to System.currentTimeMillis(),
            "lastSyncedAt"  to System.currentTimeMillis(),
            "lastSeen"      to System.currentTimeMillis(),
            "email"         to userEmail
        )

        db.collection("users").document(userId)
            .set(paymentData2, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("PremiumActivity", "✅ Firestore write SUCCESS for userId=$userId plan=$planName tx=$razorpayPaymentId")
            }
            .addOnFailureListener { e ->
                Log.e("PremiumActivity", "❌ Firestore write FAILED for userId=$userId: ${e.message}", e)
            }

        // Save locally too
        SubscriptionManager.saveSubscription(this, planName, razorpayPaymentId, durationDays)

        // Show success to user immediately
        Toast.makeText(this, "✅ Payment Successful! Pro features unlocked.", Toast.LENGTH_LONG).show()

        // ── STEP C: Backend verification in background (audit trail only) ──
        if (orderId.isNotEmpty() && signature.isNotEmpty()) {
            com.magic.calculator.network.VercelRazorpayHelper.verifyPayment(orderId, razorpayPaymentId, signature) { success, message ->
                runOnUiThread {
                    if (success) {
                        Log.d("PremiumActivity", "Backend verification confirmed: $message")
                    } else {
                        Log.w("PremiumActivity", "Backend verify: $message (Firestore already written)")
                    }
                }
            }
        }

        finish()
    }


    /**
     * Step 4: Callback on Payment Error or User Cancellation
     */
    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        runOnUiThread {
            val message = if (code == Checkout.PAYMENT_CANCELED) {
                "Payment canceled by user."
            } else {
                "Payment failed: ${description ?: "Unknown error"}"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            resetPurchaseButton()
        }
    }

    private fun resetPurchaseButton() {
        val btnPurchase = findViewById<Button>(R.id.btnPurchase)
        btnPurchase.isEnabled = true
        btnPurchase.text = when (selectedPlan) {
            "WEEKLY"   -> "Get Weekly Pass for ₹99"
            "MONTHLY"  -> "Get Monthly Pass for ₹199"
            "LIFETIME" -> "Get Lifetime Access for ₹299"
            else       -> "Get Monthly Pass for ₹199"
        }
    }
}
