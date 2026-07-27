package com.magic.calculator

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.google.android.material.card.MaterialCardView
import com.magic.calculator.logic.PremiumManager

class PremiumActivity : AppCompatActivity() {

    private var selectedPlan = "YEARLY"
    private lateinit var billingClient: BillingClient
    private var productDetailsList: List<ProductDetails> = emptyList()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase canceled.", Toast.LENGTH_SHORT).show()
            resetPurchaseButton()
        } else {
            Log.e("PremiumActivity", "Purchase failed: ${billingResult.debugMessage}")
            Toast.makeText(this, "Purchase failed: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
            resetPurchaseButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)

        val btnClose = findViewById<ImageView>(R.id.btnClose)
        val cardMonthly = findViewById<MaterialCardView>(R.id.cardMonthly)
        val cardYearly = findViewById<MaterialCardView>(R.id.cardYearly)
        val btnPurchase = findViewById<Button>(R.id.btnPurchase)

        btnClose.setOnClickListener {
            finish()
        }

        val btnTest7Day = findViewById<Button>(R.id.btnTest7Day)
        val btnTestLifetime = findViewById<Button>(R.id.btnTestLifetime)

        btnTest7Day.setOnClickListener {
            btnTest7Day.isEnabled = false
            Toast.makeText(this, "Simulating 7-Day Sandbox Subscription...", Toast.LENGTH_SHORT).show()
            PremiumManager.updatePremiumFirestore(this, true, "MONTHLY") { success, msg ->
                runOnUiThread {
                    Toast.makeText(this@PremiumActivity, msg, Toast.LENGTH_LONG).show()
                    if (success) finish() else btnTest7Day.isEnabled = true
                }
            }
        }

        btnTestLifetime.setOnClickListener {
            btnTestLifetime.isEnabled = false
            Toast.makeText(this, "Simulating Lifetime Sandbox License...", Toast.LENGTH_SHORT).show()
            PremiumManager.updatePremiumFirestore(this, true, "lifetime") { success, msg ->
                runOnUiThread {
                    Toast.makeText(this@PremiumActivity, msg, Toast.LENGTH_LONG).show()
                    if (success) finish() else btnTestLifetime.isEnabled = true
                }
            }
        }

        cardMonthly.setOnClickListener {
            selectedPlan = "MONTHLY"
            cardMonthly.setCardBackgroundColor(Color.parseColor("#1A00E5FF"))
            cardMonthly.strokeWidth = 2
            cardMonthly.strokeColor = Color.parseColor("#00E5FF")

            cardYearly.setCardBackgroundColor(Color.parseColor("#222222"))
            cardYearly.strokeWidth = 0

            btnPurchase.text = "Continue with Monthly (₹99)"
        }

        cardYearly.setOnClickListener {
            selectedPlan = "YEARLY"
            cardYearly.setCardBackgroundColor(Color.parseColor("#1A00E5FF"))
            cardYearly.strokeWidth = 2
            cardYearly.strokeColor = Color.parseColor("#00E5FF")

            cardMonthly.setCardBackgroundColor(Color.parseColor("#222222"))
            cardMonthly.strokeWidth = 0

            btnPurchase.text = "Continue with Yearly (₹2000)"
        }

        setupBillingClient()

        btnPurchase.setOnClickListener {
            btnPurchase.isEnabled = false
            btnPurchase.text = "Processing..."

            val productId = if (selectedPlan == "YEARLY") "yearly_2000" else "monthly_99"
            val productDetails = productDetailsList.find { it.productId == productId }

            if (productDetails != null) {
                val subscriptionOfferDetails = productDetails.subscriptionOfferDetails
                val offerToken = subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""

                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                billingClient.launchBillingFlow(this, billingFlowParams)
            } else {
                // Developer / Simulator fallback mode
                Log.d("PremiumActivity", "BillingClient not ready. Falling back to simulated Google Play Checkout.")
                showSimulatedPlayBillingDialog()
            }
        }
    }

    private fun showSimulatedPlayBillingDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheetDialog.setContentView(R.layout.dialog_google_play_billing)
        bottomSheetDialog.setCancelable(false)

        val tvGoogleAccountEmail = bottomSheetDialog.findViewById<android.widget.TextView>(R.id.tvGoogleAccountEmail)
        val tvBillingDetails = bottomSheetDialog.findViewById<android.widget.TextView>(R.id.tvBillingDetails)
        val tvBillingPrice = bottomSheetDialog.findViewById<android.widget.TextView>(R.id.tvBillingPrice)
        val layoutProcessing = bottomSheetDialog.findViewById<android.view.View>(R.id.layoutProcessing)
        val layoutActions = bottomSheetDialog.findViewById<android.view.View>(R.id.layoutActions)
        val btnCancelBilling = bottomSheetDialog.findViewById<android.widget.Button>(R.id.btnCancelBilling)
        val btnSubscribeBilling = bottomSheetDialog.findViewById<android.widget.Button>(R.id.btnSubscribeBilling)

        // Bind data
        val email = com.magic.calculator.logic.AuthManager.currentUser?.email ?: "tester@magicapp.com"
        tvGoogleAccountEmail?.text = email

        if (selectedPlan == "YEARLY") {
            tvBillingDetails?.text = "Yearly Subscription"
            tvBillingPrice?.text = "₹2,000.00"
        } else {
            tvBillingDetails?.text = "Monthly Subscription"
            tvBillingPrice?.text = "₹99.00"
        }

        btnCancelBilling?.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "Purchase canceled.", Toast.LENGTH_SHORT).show()
            resetPurchaseButton()
        }

        btnSubscribeBilling?.setOnClickListener {
            // Show processing simulation
            layoutActions?.visibility = android.view.View.GONE
            layoutProcessing?.visibility = android.view.View.VISIBLE

            // Perform the Firestore transaction and listen for the explicit callback
            PremiumManager.updatePremiumFirestore(this, true, selectedPlan) { success, msg ->
                runOnUiThread {
                    // Instantly hide the progress bar and dismiss the bottom sheet
                    layoutProcessing?.visibility = android.view.View.GONE
                    bottomSheetDialog.dismiss()
                    
                    Toast.makeText(this@PremiumActivity, msg, Toast.LENGTH_LONG).show()
                    if (success) {
                        // Success! Unlock Advanced Features instantly by finishing the Paywall
                        finish()
                    } else {
                        resetPurchaseButton()
                    }
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails()
                } else {
                    Log.e("PremiumActivity", "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d("PremiumActivity", "Billing service disconnected. Reconnecting...")
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("monthly_99")
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("yearly_2000")
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                this.productDetailsList = productDetailsList
                Log.d("PremiumActivity", "Product details queried: ${productDetailsList.size} found.")
            } else {
                Log.e("PremiumActivity", "Query product details failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val planType = if (purchase.products.contains("yearly_2000")) "YEARLY" else "MONTHLY"
                        PremiumManager.updatePremiumFirestore(this, true, planType) { success, msg ->
                            runOnUiThread {
                                Toast.makeText(this@PremiumActivity, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    finish()
                                } else {
                                    resetPurchaseButton()
                                }
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@PremiumActivity, "Acknowledgment failed: ${billingResult.debugMessage}", Toast.LENGTH_LONG).show()
                            resetPurchaseButton()
                        }
                    }
                }
            }
        }
    }

    private fun resetPurchaseButton() {
        val btnPurchase = findViewById<Button>(R.id.btnPurchase)
        runOnUiThread {
            btnPurchase.isEnabled = true
            btnPurchase.text = if (selectedPlan == "YEARLY") "Continue with Yearly (₹2000)" else "Continue with Monthly (₹99)"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
    }
}
