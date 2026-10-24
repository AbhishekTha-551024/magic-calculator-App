package com.magic.calculator.feature.p2p.data.datasource

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.magic.calculator.feature.p2p.domain.model.P2PEventListener
import com.magic.calculator.core.logic.AuthManager

class FirestoreP2PDataSource {

    companion object {
        private const val TAG = "FirestoreP2PDataSource"

        fun generateMagicId(uid: String): String {
            val alphanum = uid.filter { it.isLetterOrDigit() }.take(6).uppercase()
            val padded = alphanum.padEnd(6, '0')
            return "MC-$padded"
        }
    }

    private val db = FirebaseFirestore.getInstance()

    var myMagicId: String = ""
    var myUid: String = ""
    var partnerMagicId: String = ""
    var partnerUid: String = ""
    var sessionId: String = ""
    var isMirrorEnabled: Boolean = false
        private set
    var isConnected: Boolean = false

    private var isRegisteredInFirestore: Boolean = false
    private var requestListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null
    var listener: P2PEventListener? = null

    fun initialize(onReady: (magicId: String) -> Unit) {
        val uid = AuthManager.currentUid ?: run {
            Log.e(TAG, "No auth UID found â€” user not logged in")
            listener?.onError("Not logged in. Please sign in to use Online Magic Link.")
            return
        }
        myUid = uid
        myMagicId = generateMagicId(uid)
        isRegisteredInFirestore = false

        onReady(myMagicId)

        registerMagicId { success, error ->
            if (success) {
                isRegisteredInFirestore = true
                startListeningForRequests()
            } else {
                Log.e(TAG, "Failed to register Magic ID: $error")
                startListeningForRequests()
            }
        }
    }

    private fun registerMagicId(onDone: (success: Boolean, error: String?) -> Unit) {
        val magicIdData = mapOf("uid" to myUid)

        db.collection("magic_ids").document(myMagicId).set(magicIdData)
            .addOnSuccessListener {
                db.collection("users").document(myUid)
                    .set(
                        mapOf("magicId" to myMagicId, "lastSeen" to System.currentTimeMillis()),
                        SetOptions.merge()
                    )
                onDone(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "registerMagicId failed: ${e.message}", e)
                Handler(Looper.getMainLooper()).postDelayed({
                    db.collection("magic_ids").document(myMagicId).set(magicIdData)
                        .addOnSuccessListener {
                            isRegisteredInFirestore = true
                            onDone(true, null)
                        }
                        .addOnFailureListener { e2 ->
                            Log.e(TAG, "registerMagicId retry failed: ${e2.message}", e2)
                            onDone(false, e2.message)
                        }
                }, 2000)
            }
    }

    fun sendRequest(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit) {
        val cleanId = toMagicId.trim().uppercase()

        if (!cleanId.matches(Regex("MC-[A-Z0-9]{6}")) && !cleanId.matches(Regex("M-\\d{4}"))) {
            onResult(false, "Invalid Magic ID format. Should look like MC-A3B7KQ")
            return
        }
        if (cleanId == myMagicId) {
            onResult(false, "You cannot connect to yourself!")
            return
        }
        if (myUid.isEmpty()) {
            onResult(false, "Not logged in. Please restart the app.")
            return
        }

        if (!isRegisteredInFirestore) {
            registerMagicId { success, error ->
                if (success) {
                    isRegisteredInFirestore = true
                    doSendRequest(cleanId, onResult)
                } else {
                    onResult(false, "Could not reach Firebase. Check your internet and Firestore rules.\nError: $error")
                }
            }
        } else {
            doSendRequest(cleanId, onResult)
        }
    }

    private fun doSendRequest(cleanId: String, onResult: (success: Boolean, message: String) -> Unit) {
        db.collection("magic_ids").document(cleanId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(false, "Magic ID '$cleanId' not found.\n\nMake sure your partner:\nâ€¢ Has the app open\nâ€¢ Is logged in\nâ€¢ Has tapped the Magic Link panel at least once")
                    return@addOnSuccessListener
                }

                val toUid = doc.getString("uid") ?: run {
                    onResult(false, "Partner data is corrupted. Ask them to re-open the Magic Link panel.")
                    return@addOnSuccessListener
                }

                val requestData = mapOf(
                    "fromId" to myMagicId,
                    "fromUid" to myUid,
                    "toId" to cleanId,
                    "toUid" to toUid,
                    "status" to "pending",
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("requests").add(requestData)
                    .addOnSuccessListener { docRef ->
                        Log.d(TAG, "Request sent to $cleanId, docId=${docRef.id}")
                        onResult(true, "âœ… Request sent to $cleanId! Ask your partner to accept it.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create request doc: ${e.message}", e)
                        onResult(false, "Failed to send request.\nError: ${e.message}\n\nCheck Firestore security rules.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore lookup failed for $cleanId: ${e.message}", e)
                onResult(false, "Cannot reach Firebase.\n\nError: ${e.message}")
            }
    }

    fun startListeningForRequests() {
        if (myUid.isEmpty()) return

        requestListener?.remove()
        requestListener = db.collection("requests")
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "Request listener error: ${error.message}", error)
                    return@addSnapshotListener
                }
                for (doc in snapshots?.documents ?: emptyList()) {
                    val fromId = doc.getString("fromId") ?: continue
                    val status = doc.getString("status") ?: ""
                    if (status == "pending") {
                        listener?.onIncomingRequest(fromId, doc.id)
                    }
                }
            }
    }

    fun acceptRequest(requestDocId: String, fromMagicId: String) {
        db.collection("magic_ids").document(fromMagicId).get()
            .addOnSuccessListener { doc ->
                val fromUid = doc.getString("uid") ?: return@addOnSuccessListener

                val newSessionId = "${myUid.take(8)}_${fromUid.take(8)}"
                sessionId = newSessionId
                partnerMagicId = fromMagicId
                partnerUid = fromUid

                val sessionData = mapOf(
                    "masterUid" to fromUid,
                    "slaveUid" to myUid,
                    "masterMagicId" to fromMagicId,
                    "slaveMagicId" to myMagicId,
                    "display" to "",
                    "formula" to "",
                    "command" to "",
                    "commandSentBy" to "",
                    "mirrorEnabled" to false,
                    "updatedBy" to "",
                    "updatedAt" to System.currentTimeMillis()
                )

                db.collection("sessions").document(newSessionId).set(sessionData)
                    .addOnSuccessListener {
                        db.collection("requests").document(requestDocId)
                            .update(mapOf("status" to "accepted", "sessionId" to newSessionId))
                        isConnected = true
                        startListeningToSession(newSessionId)
                        listener?.onConnected(fromMagicId, newSessionId)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create session: ${e.message}", e)
                        listener?.onError("Failed to create session: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to look up requester: ${e.message}", e)
                listener?.onError("Cannot accept request right now: ${e.message}")
            }
    }

    fun declineRequest(requestDocId: String) {
        db.collection("requests").document(requestDocId)
            .update(mapOf("status" to "declined"))
    }

    fun listenForAcceptedRequest(onAccepted: (sessionId: String, partnerMagicId: String) -> Unit) {
        if (myUid.isEmpty()) return

        db.collection("requests")
            .whereEqualTo("fromUid", myUid)
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(TAG, "listenForAcceptedRequest error: ${error.message}", error)
                    return@addSnapshotListener
                }
                for (doc in snapshots?.documents ?: emptyList()) {
                    val newSessionId = doc.getString("sessionId") ?: continue
                    val toId = doc.getString("toId") ?: continue
                    if (!isConnected) {
                        sessionId = newSessionId
                        partnerMagicId = toId
                        isConnected = true
                        startListeningToSession(newSessionId)
                        onAccepted(newSessionId, toId)
                        doc.reference.update("status", "connected")
                    }
                }
            }
    }

    fun startListeningToSession(sessionId: String) {
        sessionListener?.remove()
        sessionListener = db.collection("sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Session listener error: ${error?.message}", error)
                    return@addSnapshotListener
                }

                val updatedBy = snapshot.getString("updatedBy") ?: ""
                if (updatedBy == myUid) return@addSnapshotListener

                val display = snapshot.getString("display") ?: ""
                val formula = snapshot.getString("formula") ?: ""
                if (display.isNotEmpty() || formula.isNotEmpty()) {
                    listener?.onRemoteDisplayUpdate(display, formula)
                }

                val command = snapshot.getString("command") ?: ""
                val cmdSentBy = snapshot.getString("commandSentBy") ?: ""
                if (command.isNotEmpty() && cmdSentBy != myUid) {
                    listener?.onRemoteCommand(command)
                    db.collection("sessions").document(sessionId)
                        .update(mapOf("command" to "", "commandSentBy" to ""))
                }

                isMirrorEnabled = snapshot.getBoolean("mirrorEnabled") ?: false
            }
    }

    fun syncDisplay(display: String, formula: String) {
        if (!isConnected || !isMirrorEnabled || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId).update(
            mapOf(
                "display" to display,
                "formula" to formula,
                "updatedBy" to myUid,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    fun sendCommand(command: String) {
        if (!isConnected || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId).update(
            mapOf(
                "command" to command,
                "commandSentBy" to myUid,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    fun setMirrorEnabled(enabled: Boolean) {
        isMirrorEnabled = enabled
        if (!isConnected || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId)
            .update(mapOf("mirrorEnabled" to enabled))
    }

    fun disconnect() {
        if (sessionId.isNotEmpty()) {
            db.collection("sessions").document(sessionId).delete()
        }
        sessionListener?.remove()
        sessionListener = null
        isConnected = false
        partnerMagicId = ""
        partnerUid = ""
        sessionId = ""
        isMirrorEnabled = false
        listener?.onDisconnected()
    }

    fun stopAll() {
        requestListener?.remove()
        requestListener = null
        sessionListener?.remove()
        sessionListener = null
        isConnected = false
    }
}
