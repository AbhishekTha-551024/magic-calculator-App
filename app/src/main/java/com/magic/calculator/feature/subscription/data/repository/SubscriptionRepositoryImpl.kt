package com.magic.calculator.feature.subscription.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.magic.calculator.feature.subscription.data.datasource.FirestoreSubscriptionRemoteDataSource
import com.magic.calculator.feature.subscription.data.datasource.PaymentRemoteDataSource
import com.magic.calculator.feature.subscription.data.datasource.SubscriptionLocalDataSource
import com.magic.calculator.feature.subscription.data.datasource.SubscriptionRemoteDataSource
import com.magic.calculator.feature.subscription.data.datasource.VercelPaymentRemoteDataSource
import com.magic.calculator.feature.subscription.domain.model.PaymentRecord
import com.magic.calculator.feature.subscription.domain.model.SubscriptionDetails
import com.magic.calculator.feature.subscription.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubscriptionRepositoryImpl(
    private val localDataSource: SubscriptionLocalDataSource,
    private val remoteDataSource: SubscriptionRemoteDataSource = FirestoreSubscriptionRemoteDataSource(),
    private val paymentRemoteDataSource: PaymentRemoteDataSource = VercelPaymentRemoteDataSource()
) : SubscriptionRepository {

    private val _isPremium = MutableStateFlow(localDataSource.isPremium())
    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    override fun isPremiumUser(): Boolean {
        val pro = localDataSource.isPremium()
        if (_isPremium.value != pro) {
            _isPremium.value = pro
        }
        return pro
    }

    override fun getSubscriptionDetails(): SubscriptionDetails {
        return localDataSource.getSubscriptionDetails()
    }

    override fun saveSubscriptionLocally(
        planType: String,
        paymentId: String,
        durationDays: Int,
        activatedAt: Long,
        startDate: String?,
        expiryMillis: Long?,
        expiryDate: String?,
        purchaseType: String?
    ) {
        localDataSource.saveSubscription(
            planType = planType,
            paymentId = paymentId,
            durationDays = durationDays,
            activatedAt = activatedAt,
            startDate = startDate,
            expiryMillis = expiryMillis,
            expiryDate = expiryDate,
            purchaseType = purchaseType
        )
        _isPremium.value = true
    }

    override fun clearSubscription() {
        localDataSource.clearSubscription()
        _isPremium.value = false
    }

    override suspend fun syncAndRestorePremiumStatus(): Result<String> {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result.failure(Exception("User not signed in. Please log in first."))

        return try {
            val snapshot = remoteDataSource.fetchUserSubscriptionDoc(user.uid)
            if (snapshot != null && snapshot.exists()) {
                val isPremiumBool = snapshot.getBoolean("isPremium") == true
                val isProUserBool = snapshot.getBoolean("isProUser") == true
                val planType = snapshot.getString("planType") ?: ""
                val transactionId = snapshot.getString("transactionId")
                    ?: snapshot.getString("paymentId")
                    ?: ""

                val hasValidPlan = planType.isNotEmpty() && !planType.equals("Free Tier", ignoreCase = true)
                val hasValidTransaction = transactionId.isNotEmpty() && !transactionId.equals("N/A", ignoreCase = true)
                val expiryMillisFromDoc = snapshot.getLong("expiryMillis")
                    ?: snapshot.getLong("premiumExpiryDate")
                    ?: 0L

                val isLifetime = planType.contains("Lifetime", ignoreCase = true)
                val isExpired = !isLifetime && expiryMillisFromDoc > 0L && System.currentTimeMillis() > expiryMillisFromDoc

                val isUserPro = (isPremiumBool || isProUserBool || hasValidPlan || hasValidTransaction) && !isExpired

                if (isUserPro) {
                    val activePlan = if (hasValidPlan) planType else "CalcMagic Pro"
                    val activeTx = if (hasValidTransaction) transactionId else "RESTORED_PAYMENT"
                    val durationDays = when {
                        activePlan.contains("Lifetime", ignoreCase = true) -> 36500
                        activePlan.contains("Yearly", ignoreCase = true)   -> 365
                        activePlan.contains("Monthly", ignoreCase = true)  -> 30
                        activePlan.contains("Weekly", ignoreCase = true)   -> 7
                        else -> 30
                    }

                    val activatedAtDoc = snapshot.getLong("activatedAt")
                        ?: snapshot.getLong("lastPaymentDate")
                        ?: System.currentTimeMillis()
                    val startDateDoc = snapshot.getString("startDate")
                    val expiryDateDoc = snapshot.getString("expiryDate")
                    val purchaseTypeDoc = snapshot.getString("purchaseType")
                        ?: if (isLifetime) "lifetime" else "subscription"

                    saveSubscriptionLocally(
                        planType = activePlan,
                        paymentId = activeTx,
                        durationDays = durationDays,
                        activatedAt = activatedAtDoc,
                        startDate = startDateDoc,
                        expiryMillis = expiryMillisFromDoc,
                        expiryDate = expiryDateDoc,
                        purchaseType = purchaseTypeDoc
                    )
                    Result.success("Purchase restored successfully! Active plan: $activePlan")
                } else {
                    clearSubscription()
                    val msg = if (isExpired) "Your previous subscription has expired." else "No active subscription found for account: ${user.email}"
                    Result.failure(Exception(msg))
                }
            } else {
                clearSubscription()
                Result.failure(Exception("No purchase history found for account: ${user.email}"))
            }
        } catch (e: Exception) {
            val localPro = isPremiumUser()
            if (localPro) {
                Result.success("Using cached subscription status.")
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun createPaymentOrder(amount: Int, planType: String, userId: String?): Result<String> {
        return paymentRemoteDataSource.createOrder(amount, planType, userId)
    }

    override suspend fun verifyAndUnlockPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        planType: String,
        userId: String?,
        userEmail: String,
        planName: String,
        durationDays: Int
    ): Result<String> {
        val verificationResult = paymentRemoteDataSource.verifyPayment(
            orderId = orderId,
            paymentId = paymentId,
            signature = signature,
            planType = planType,
            userId = userId
        )

        return verificationResult.mapCatching { message ->
            val nowMillis = System.currentTimeMillis()
            val isLifetime = planType == "LIFETIME" || planName.contains("Lifetime", ignoreCase = true)
            val expiryMillis = if (isLifetime) 0L else nowMillis + (durationDays.toLong() * 24 * 60 * 60 * 1000)

            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
            val startDateStr = dateFormat.format(java.util.Date(nowMillis))
            val expiryDateStr = if (isLifetime) "Never Expires (Lifetime Access)" else dateFormat.format(java.util.Date(expiryMillis))
            val purchaseType = if (isLifetime) "lifetime" else "subscription"

            // 1. Save locally with complete dates
            saveSubscriptionLocally(
                planType = planName,
                paymentId = paymentId,
                durationDays = durationDays,
                activatedAt = nowMillis,
                startDate = startDateStr,
                expiryMillis = expiryMillis,
                expiryDate = expiryDateStr,
                purchaseType = purchaseType
            )

            // 2. Commit verified license to Cloud Firestore
            if (!userId.isNullOrEmpty()) {
                val paymentDataMap = hashMapOf(
                    "isPremium"         to true,
                    "isProUser"         to true,
                    "planType"          to planName,
                    "purchaseType"      to purchaseType,
                    "transactionId"     to paymentId,
                    "paymentId"         to paymentId,
                    "lastOrderId"       to orderId,
                    "activatedAt"       to nowMillis,
                    "startDate"         to startDateStr,
                    "expiryMillis"      to expiryMillis,
                    "premiumExpiryDate" to expiryMillis,
                    "expiryDate"        to expiryDateStr,
                    "lastPaymentDate"   to nowMillis,
                    "lastSyncedAt"      to nowMillis,
                    "lastSeen"          to nowMillis,
                    "email"             to userEmail
                )
                remoteDataSource.saveSubscriptionToFirestore(userId, paymentDataMap)

                // 3. Save to append-only transactions subcollection
                val txData = hashMapOf(
                    "orderId"        to orderId,
                    "paymentId"      to paymentId,
                    "transactionId"  to paymentId,
                    "planId"         to planType,
                    "planName"       to planName,
                    "amount"         to estimatePlanAmount(planName),
                    "currency"       to "INR",
                    "method"         to "UPI",
                    "status"         to "SUCCESS",
                    "timestamp"      to nowMillis,
                    "activatedAt"    to nowMillis,
                    "startDate"      to startDateStr,
                    "expiryMillis"   to expiryMillis,
                    "expiryDate"     to expiryDateStr,
                    "purchaseType"   to purchaseType
                )
                remoteDataSource.saveTransactionRecord(userId, paymentId, txData)
            }

            message
        }
    }

    override suspend fun fetchPaymentHistory(): Result<List<PaymentRecord>> {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            val details = getSubscriptionDetails()
            return if (details.isPremium && details.paymentId != "N/A" && details.paymentId.isNotEmpty()) {
                Result.success(listOf(
                    PaymentRecord(
                        orderId = "order_offline",
                        paymentId = details.paymentId,
                        planId = details.planType,
                        planName = details.planType,
                        amount = estimatePlanAmount(details.planType),
                        status = "SUCCESS",
                        timestamp = if (details.activatedAt > 0L) details.activatedAt else System.currentTimeMillis(),
                        expiryDate = details.expiryDate,
                        startDate = details.startDate
                    )
                ))
            } else {
                Result.success(emptyList())
            }
        }

        return try {
            val records = remoteDataSource.fetchTransactions(user.uid)
            if (records.isNotEmpty()) {
                Result.success(records)
            } else {
                // Fallback to parent user doc
                val snapshot = remoteDataSource.fetchUserSubscriptionDoc(user.uid)
                val paymentId = snapshot?.getString("paymentId") ?: snapshot?.getString("transactionId") ?: ""
                val isPremium = snapshot?.getBoolean("isPremium") == true || snapshot?.getBoolean("isProUser") == true

                if (paymentId.isNotEmpty() || isPremium) {
                    val planName = snapshot?.getString("planType") ?: "CalcMagic Pro"
                    val orderId = snapshot?.getString("lastOrderId") ?: "order_verified"
                    val activatedAt = snapshot?.getLong("activatedAt") ?: System.currentTimeMillis()
                    val expiryDate = snapshot?.getString("expiryDate") ?: ""
                    val startDate = snapshot?.getString("startDate") ?: ""

                    Result.success(listOf(
                        PaymentRecord(
                            orderId = orderId,
                            paymentId = paymentId.ifEmpty { "pay_verified" },
                            planId = planName,
                            planName = planName,
                            amount = estimatePlanAmount(planName),
                            status = "SUCCESS",
                            timestamp = activatedAt,
                            expiryDate = expiryDate,
                            startDate = startDate
                        )
                    ))
                } else {
                    Result.success(emptyList())
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun estimatePlanAmount(planName: String): Int {
        return when {
            planName.contains("1999", ignoreCase = true) || planName.contains("Yearly", ignoreCase = true) || planName.contains("Lifetime", ignoreCase = true) -> 1999
            planName.contains("299", ignoreCase = true) || planName.contains("Monthly", ignoreCase = true) -> 299
            planName.contains("149", ignoreCase = true) || planName.contains("Weekly", ignoreCase = true) -> 149
            else -> 299
        }
    }

    override suspend fun ensureUserDocumentCreated(): Result<Unit> {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success(Unit)
        return try {
            remoteDataSource.ensureUserDocumentCreated(user.uid, user.email ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
