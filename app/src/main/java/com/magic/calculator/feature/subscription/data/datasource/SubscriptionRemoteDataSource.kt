package com.magic.calculator.feature.subscription.data.datasource

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.magic.calculator.feature.subscription.domain.model.PaymentRecord
import kotlinx.coroutines.tasks.await

interface SubscriptionRemoteDataSource {
    suspend fun fetchUserSubscriptionDoc(userId: String): DocumentSnapshot?
    suspend fun saveSubscriptionToFirestore(userId: String, paymentData: Map<String, Any>)
    suspend fun saveTransactionRecord(userId: String, paymentId: String, txData: Map<String, Any>)
    suspend fun ensureUserDocumentCreated(userId: String, email: String)
    suspend fun fetchTransactions(userId: String): List<PaymentRecord>
}

class FirestoreSubscriptionRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SubscriptionRemoteDataSource {

    override suspend fun fetchUserSubscriptionDoc(userId: String): DocumentSnapshot? {
        val docRef = firestore.collection("users").document(userId)
        return try {
            // Priority: Source.SERVER to prevent client tampering
            docRef.get(Source.SERVER).await()
        } catch (e: Exception) {
            // Fallback: Source.CACHE
            try {
                docRef.get(Source.CACHE).await()
            } catch (ignored: Exception) {
                null
            }
        }
    }

    override suspend fun saveSubscriptionToFirestore(userId: String, paymentData: Map<String, Any>) {
        firestore.collection("users").document(userId)
            .set(paymentData, SetOptions.merge())
            .await()
    }

    override suspend fun saveTransactionRecord(userId: String, paymentId: String, txData: Map<String, Any>) {
        firestore.collection("users").document(userId)
            .collection("transactions").document(paymentId)
            .set(txData, SetOptions.merge())
            .await()
    }

    override suspend fun ensureUserDocumentCreated(userId: String, email: String) {
        val userData = hashMapOf<String, Any>(
            "email" to email,
            "lastSeen" to System.currentTimeMillis()
        )
        firestore.collection("users").document(userId)
            .set(userData, SetOptions.merge())
            .await()
    }

    override suspend fun fetchTransactions(userId: String): List<PaymentRecord> {
        val txCollection = firestore.collection("users").document(userId).collection("transactions")
        val snapshot = try {
            txCollection.get(Source.SERVER).await()
        } catch (e: Exception) {
            try {
                txCollection.get(Source.CACHE).await()
            } catch (ex: Exception) {
                null
            }
        } ?: return emptyList()

        return snapshot.documents.mapNotNull { doc ->
            try {
                val planName = doc.getString("planName") ?: doc.getString("planType") ?: "CalcMagic Pro"
                PaymentRecord(
                    orderId = doc.getString("orderId") ?: doc.id,
                    paymentId = doc.getString("paymentId") ?: doc.getString("transactionId") ?: "",
                    planId = doc.getString("planId") ?: doc.getString("planType") ?: "",
                    planName = planName,
                    amount = doc.getLong("amount")?.toInt() ?: estimateAmount(planName),
                    currency = doc.getString("currency") ?: "INR",
                    method = doc.getString("method") ?: "UPI",
                    status = doc.getString("status") ?: "SUCCESS",
                    timestamp = doc.getLong("timestamp") ?: doc.getLong("activatedAt") ?: System.currentTimeMillis(),
                    expiryDate = doc.getString("expiryDate") ?: "",
                    startDate = doc.getString("startDate") ?: ""
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    private fun estimateAmount(planName: String): Int {
        return when {
            planName.contains("1999", ignoreCase = true) || planName.contains("Yearly", ignoreCase = true) || planName.contains("Lifetime", ignoreCase = true) -> 1999
            planName.contains("299", ignoreCase = true) || planName.contains("Monthly", ignoreCase = true) -> 299
            planName.contains("149", ignoreCase = true) || planName.contains("Weekly", ignoreCase = true) -> 149
            else -> 299
        }
    }
}
