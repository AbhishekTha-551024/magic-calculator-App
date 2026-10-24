package com.magic.calculator.feature.subscription.presentation

import android.Manifest
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.magic.calculator.R
import com.magic.calculator.core.logic.PremiumManager
import com.magic.calculator.core.network.VercelRazorpayHelper
import com.magic.calculator.core.ui.util.MagicToast
import com.magic.calculator.di.appContainer
import com.magic.calculator.feature.subscription.domain.model.PlanTier
import com.magic.calculator.feature.subscription.domain.model.SubscriptionDetails
import com.magic.calculator.feature.subscription.presentation.state.PremiumUiState
import com.magic.calculator.feature.subscription.presentation.viewmodel.PremiumViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch
import org.json.JSONObject

class PremiumActivity : AppCompatActivity(), PaymentResultWithDataListener {

    private val viewModel: PremiumViewModel by viewModels {
        PremiumViewModel.Factory(appContainer.subscriptionRepository)
    }

    private var pendingOrderId: String? = null
    private var isOrderInProgress = false
    private val safetyHandler = Handler(Looper.getMainLooper())
    private val resetTimeoutRunnable = Runnable { resetPurchaseButton() }

    private lateinit var btnPurchase: Button
    private lateinit var tvRestorePurchases: TextView
    private lateinit var cardWeekly: MaterialCardView
    private lateinit var cardYearly: MaterialCardView
    private lateinit var cardLifetime: MaterialCardView
    private lateinit var allCards: List<MaterialCardView>

    // Active subscription views
    private lateinit var cardActiveSubscription: MaterialCardView
    private lateinit var txtActivePlanName: TextView
    private lateinit var txtActiveStartDate: TextView
    private lateinit var txtActiveExpiryDate: TextView
    private lateinit var txtActiveCountdown: TextView
    private lateinit var btnActiveHistory: Button
    private lateinit var llFeatures: View
    private lateinit var tvPlansHeader: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        cardWeekly = findViewById(R.id.cardWeekly)
        cardYearly = findViewById(R.id.cardYearly)
        cardLifetime = findViewById(R.id.cardLifetime)
        tvRestorePurchases = findViewById(R.id.tvRestorePurchases)
        btnPurchase = findViewById(R.id.btnPurchase)

        cardActiveSubscription = findViewById(R.id.cardActiveSubscription)
        txtActivePlanName = findViewById(R.id.txtActivePlanName)
        txtActiveStartDate = findViewById(R.id.txtActiveStartDate)
        txtActiveExpiryDate = findViewById(R.id.txtActiveExpiryDate)
        txtActiveCountdown = findViewById(R.id.txtActiveCountdown)
        btnActiveHistory = findViewById(R.id.btnActiveHistory)
        llFeatures = findViewById(R.id.llFeatures)
        tvPlansHeader = findViewById(R.id.tvPlansHeader)

        btnActiveHistory.setOnClickListener {
            startActivity(Intent(this, PaymentHistoryActivity::class.java))
        }

        allCards = listOf(cardWeekly, cardYearly, cardLifetime)
        btnClose.setOnClickListener { finish() }

        // Highlight default selection (Monthly Pass ₹299)
        highlightSelectedCard(cardYearly, allCards, "#00E5FF")
        btnPurchase.text = "Get Monthly Pass for ₹299"

        cardWeekly.setOnClickListener {
            viewModel.selectPlan(PlanTier.WEEKLY)
            highlightSelectedCard(cardWeekly, allCards, "#00E5FF")
            updatePurchaseButtonLabel("Weekly Pass", "₹149")
        }

        cardYearly.setOnClickListener {
            viewModel.selectPlan(PlanTier.MONTHLY)
            highlightSelectedCard(cardYearly, allCards, "#00E5FF")
            updatePurchaseButtonLabel("Monthly Pass", "₹299")
        }

        cardLifetime.setOnClickListener {
            viewModel.selectPlan(PlanTier.YEARLY)
            highlightSelectedCard(cardLifetime, allCards, "#10B981")
            updatePurchaseButtonLabel("1 Year VIP Pass", "₹1999")
        }

        tvRestorePurchases.setOnClickListener {
            tvRestorePurchases.isEnabled = false
            MagicToast.show(this, "Restoring purchases from server...")
            viewModel.restorePurchases()
        }

        Checkout.preload(applicationContext)

        btnPurchase.setOnClickListener {
            if (isOrderInProgress) return@setOnClickListener
            isOrderInProgress = true
            btnPurchase.isEnabled = false
            btnPurchase.text = "Creating order..."

            safetyHandler.postDelayed(resetTimeoutRunnable, 25000)

            val settingsManager = appContainer.settingsManager
            val phone = settingsManager.userPhoneNumber.ifEmpty {
                appContainer.authRepository.currentUser?.phoneNumber ?: ""
            }
            if (phone.length < 10) {
                promptForPhoneNumber {
                    val uid = appContainer.authRepository.currentUid
                    viewModel.startPurchaseFlow(uid)
                }
            } else {
                val uid = appContainer.authRepository.currentUid
                viewModel.startPurchaseFlow(uid)
            }
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        updateActiveSubscriptionUI()
    }

    private fun updateActiveSubscriptionUI() {
        val repo = appContainer.subscriptionRepository
        val isPremium = repo.isPremiumUser() || PremiumManager.isUserPremium
        val details = repo.getSubscriptionDetails()

        if (isPremium) {
            cardActiveSubscription.visibility = View.VISIBLE
            llFeatures.visibility = View.GONE
            tvPlansHeader.text = "Extend / Upgrade Your Plan:"

            txtActivePlanName.text = details.planType
            txtActiveStartDate.text = details.startDate
            txtActiveExpiryDate.text = details.expiryDate

            if (details.purchaseType == "lifetime" || details.expiryMillis == 0L) {
                txtActiveCountdown.text = "Forever Unlocked (Lifetime VIP)"
                txtActiveCountdown.setTextColor(Color.parseColor("#00FF66"))
            } else {
                val daysDiff = details.expiryMillis - System.currentTimeMillis()
                if (daysDiff <= 0) {
                    txtActiveCountdown.text = "Plan Expired"
                    txtActiveCountdown.setTextColor(Color.parseColor("#FF2D55"))
                } else {
                    val days = daysDiff / (1000 * 60 * 60 * 24)
                    val hours = (daysDiff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                    txtActiveCountdown.text = "Expires in $days Days, $hours Hours"
                    txtActiveCountdown.setTextColor(Color.parseColor("#00E5FF"))
                }
            }
        } else {
            cardActiveSubscription.visibility = View.GONE
            llFeatures.visibility = View.VISIBLE
            tvPlansHeader.text = "Choose Your Plan:"
        }

        resetPurchaseButton()
    }

    private fun updatePurchaseButtonLabel(planTitle: String, priceStr: String) {
        val isPremium = appContainer.subscriptionRepository.isPremiumUser() || PremiumManager.isUserPremium
        btnPurchase.text = if (isPremium) "Extend / Upgrade: $planTitle ($priceStr)" else "Get $planTitle for $priceStr"
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is PremiumUiState.Idle -> {
                            resetPurchaseButton()
                        }
                        is PremiumUiState.PlanSelected -> {
                            // Handled in card click listeners
                        }
                        is PremiumUiState.CreatingOrder -> {
                            btnPurchase.isEnabled = false
                            btnPurchase.text = "Creating order..."
                        }
                        is PremiumUiState.OrderCreated -> {
                            pendingOrderId = state.orderId
                            checkNotificationPermissionAndProceed {
                                openRazorpayCheckout(state.orderId, state.amountInINR)
                            }
                        }
                        is PremiumUiState.VerifyingPayment -> {
                            btnPurchase.isEnabled = false
                            btnPurchase.text = "Verifying payment with server..."
                        }
                        is PremiumUiState.PaymentSuccess -> {
                            resetPurchaseButton()
                            updateActiveSubscriptionUI()
                            val details = state.details ?: appContainer.subscriptionRepository.getSubscriptionDetails()
                            showPaymentSuccessDialog(details)
                        }
                        is PremiumUiState.VerificationPending -> {
                            showVerificationFailedDialog(
                                paymentId = state.paymentId,
                                orderId = state.orderId,
                                signature = state.signature,
                                errorReason = state.reason
                            )
                        }
                        is PremiumUiState.Restoring -> {
                            tvRestorePurchases.isEnabled = false
                        }
                        is PremiumUiState.RestoreCompleted -> {
                            tvRestorePurchases.isEnabled = true
                            MagicToast.show(this@PremiumActivity, state.message, isLong = true)
                            if (state.success) {
                                updateActiveSubscriptionUI()
                            }
                        }
                        is PremiumUiState.Error -> {
                            resetPurchaseButton()
                            MagicToast.show(this@PremiumActivity, state.message, isLong = true)
                        }
                    }
                }
            }
        }
    }

    private fun showPaymentSuccessDialog(details: SubscriptionDetails) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_subscription_success)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.85f)
        }
        dialog.setCancelable(false)

        val txtSuccessPlanName = dialog.findViewById<TextView>(R.id.txtSuccessPlanName)
        val txtSuccessStartDate = dialog.findViewById<TextView>(R.id.txtSuccessStartDate)
        val txtSuccessExpiryDate = dialog.findViewById<TextView>(R.id.txtSuccessExpiryDate)
        val txtSuccessRemaining = dialog.findViewById<TextView>(R.id.txtSuccessRemaining)
        val txtSuccessTxId = dialog.findViewById<TextView>(R.id.txtSuccessTxId)
        val btnSuccessDone = dialog.findViewById<Button>(R.id.btnSuccessDone)
        val btnSuccessReceipt = dialog.findViewById<Button>(R.id.btnSuccessReceipt)

        txtSuccessPlanName?.text = details.planType
        txtSuccessStartDate?.text = details.startDate
        txtSuccessExpiryDate?.text = details.expiryDate
        txtSuccessTxId?.text = details.paymentId

        if (details.purchaseType == "lifetime" || details.expiryMillis == 0L) {
            txtSuccessRemaining?.text = "Forever Unlocked (Lifetime Access)"
            txtSuccessRemaining?.setTextColor(Color.parseColor("#00FF66"))
        } else {
            val daysDiff = details.expiryMillis - System.currentTimeMillis()
            if (daysDiff <= 0) {
                txtSuccessRemaining?.text = "Plan Expired"
                txtSuccessRemaining?.setTextColor(Color.parseColor("#FF2D55"))
            } else {
                val days = daysDiff / (1000 * 60 * 60 * 24)
                val hours = (daysDiff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
                txtSuccessRemaining?.text = "Expires in $days Days, $hours Hours"
                txtSuccessRemaining?.setTextColor(Color.parseColor("#FFD700"))
            }
        }

        txtSuccessTxId?.setOnClickListener {
            val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = ClipData.newPlainText("Payment ID", details.paymentId)
            clipManager?.setPrimaryClip(clipData)
            MagicToast.show(this, "Payment ID copied to clipboard!")
        }

        btnSuccessDone?.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        btnSuccessReceipt?.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, PaymentHistoryActivity::class.java))
            finish()
        }

        dialog.show()
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
                    val settingsManager = appContainer.settingsManager
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

    private fun checkNotificationPermissionAndProceed(onReady: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        onReady()
    }

    private fun openRazorpayCheckout(orderId: String, amountInINR: Int) {
        val checkout = Checkout()
        checkout.setKeyID(VercelRazorpayHelper.RAZORPAY_KEY_ID)
        checkout.setImage(R.drawable.ic_magic)

        val plan = viewModel.selectedPlan

        try {
            val settingsManager = appContainer.settingsManager
            val phone = settingsManager.userPhoneNumber.ifEmpty {
                appContainer.authRepository.currentUser?.phoneNumber ?: ""
            }

            val options = JSONObject().apply {
                put("name", "Magic Calculator Pro")
                put("description", plan.displayName)
                put("order_id", orderId)
                put("currency", "INR")
                put("amount", amountInINR * 100)
                put("theme.color", if (plan == PlanTier.YEARLY || plan == PlanTier.LIFETIME) "#10B981" else "#00E5FF")
                put("send_sms_hash", true)

                val upiObj = JSONObject().apply { put("flow", "intent") }
                val methodObj = JSONObject().apply {
                    put("upi", upiObj)
                    put("card", true)
                    put("netbanking", true)
                    put("wallet", true)
                }
                put("method", methodObj)

                val prefill = JSONObject().apply {
                    val user = appContainer.authRepository.currentUser
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

            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e("RazorpayDiagnostic", "Exception launching Razorpay", e)
            resetPurchaseButton()
            AlertDialog.Builder(this)
                .setTitle("Payment Sheet Error")
                .setMessage("Could not open payment sheet.\n\nDetails: ${e.localizedMessage ?: "Unknown initialization error"}")
                .setPositiveButton("Retry") { _, _ ->
                    val uid = appContainer.authRepository.currentUid
                    viewModel.startPurchaseFlow(uid)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)

        if (razorpayPaymentId.isNullOrEmpty()) {
            MagicToast.show(this, "Payment ID missing. Contact support.", isLong = true)
            resetPurchaseButton()
            return
        }

        val orderId = paymentData?.orderId ?: pendingOrderId ?: ""
        val signature = paymentData?.signature ?: ""
        val currentUser = appContainer.authRepository.currentUser
        val userId = currentUser?.uid
        val userEmail = currentUser?.email ?: ""

        viewModel.verifyPayment(
            paymentId = razorpayPaymentId,
            orderId = orderId,
            signature = signature,
            userId = userId,
            userEmail = userEmail
        )
    }

    override fun onPaymentError(code: Int, description: String?, paymentData: PaymentData?) {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
        resetPurchaseButton()
        if (code == Checkout.PAYMENT_CANCELED) {
            MagicToast.show(this, "Payment cancelled")
        } else {
            val errorMsg = description?.takeIf { it.isNotBlank() } ?: "Payment failed (Error code $code)"
            MagicToast.show(this, errorMsg, isLong = true)
        }
    }

    private fun showVerificationFailedDialog(
        paymentId: String,
        orderId: String,
        signature: String,
        errorReason: String?
    ) {
        resetPurchaseButton()
        AlertDialog.Builder(this)
            .setTitle("Verification Pending")
            .setMessage("Payment received by Razorpay (ID: $paymentId), but backend signature verification could not be completed.\n\nReason: ${errorReason ?: "Network error"}\n\nPlease tap Retry to verify again.")
            .setPositiveButton("Retry Verification") { _, _ ->
                val currentUser = appContainer.authRepository.currentUser
                viewModel.verifyPayment(
                    paymentId = paymentId,
                    orderId = orderId,
                    signature = signature,
                    userId = currentUser?.uid,
                    userEmail = currentUser?.email ?: ""
                )
            }
            .setNegativeButton("Close", null)
            .setCancelable(false)
            .show()
    }

    private fun resetPurchaseButton() {
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
        isOrderInProgress = false
        btnPurchase.isEnabled = true
        val isPremium = appContainer.subscriptionRepository.isPremiumUser() || PremiumManager.isUserPremium
        btnPurchase.text = when (viewModel.selectedPlan) {
            PlanTier.WEEKLY  -> if (isPremium) "Extend / Upgrade: Weekly Pass (₹149)" else "Get Weekly Pass for ₹149"
            PlanTier.MONTHLY -> if (isPremium) "Extend / Upgrade: Monthly Pass (₹299)" else "Get Monthly Pass for ₹299"
            PlanTier.YEARLY, PlanTier.LIFETIME -> if (isPremium) "Extend / Upgrade: 1 Year VIP Pass (₹1999)" else "Get 1 Year Pass for ₹1999"
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        safetyHandler.removeCallbacks(resetTimeoutRunnable)
    }
}
