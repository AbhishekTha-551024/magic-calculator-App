package com.magic.calculator.p2p

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.magic.calculator.logic.AuthManager

/**
 * Online P2P Magic Link Manager.
 * Uses Firebase Firestore to pair two calculators and sync typing in real-time.
 *
 * Firestore schema:
 *   /magic_ids/{magicId}  → { uid: String }
 *   /users/{uid}          → { magicId: String, ... }
 *   /requests/{docId}     → { fromId: String, fromUid: String, toId: String, toUid: String,
 *                              status: "pending"|"accepted"|"declined", timestamp: Long }
 *   /sessions/{sessionId} → { masterUid: String, slaveUid: String,
 *                              display: String, formula: String,
 *                              command: String, commandSentBy: String,
 *                              mirrorEnabled: Boolean, updatedBy: String, updatedAt: Long }
 *
 * REQUIRED Firestore Security Rules (paste in Firebase Console → Firestore → Rules):
 *
 *   rules_version = '2';
 *   service cloud.firestore {
 *     match /databases/{database}/documents {
 *       match /magic_ids/{magicId} {
 *         allow read: if true;
 *         allow write: if request.auth != null;
 *       }
 *       match /users/{uid} {
 *         allow read, write: if request.auth != null && request.auth.uid == uid;
 *       }
 *       match /requests/{docId} {
 *         allow read, write: if request.auth != null;
 *       }
 *       match /sessions/{sessionId} {
 *         allow read, write: if request.auth != null;
 *       }
 *     }
 *   }
 */
class P2PLinkManager private constructor() {

    companion object {
        private const val TAG = "P2PLinkManager"

        @Volatile
        private var instance: P2PLinkManager? = null

        fun getInstance(): P2PLinkManager {
            return instance ?: synchronized(this) {
                instance ?: P2PLinkManager().also { instance = it }
            }
        }

        /**
         * Generate a unique Magic ID from the Firebase UID.
         *
         * OLD (buggy): "M-${hash % 9000}" → only 9,000 possible IDs → collisions at scale
         * NEW (fixed): Uses first 6 alphanumeric chars of UID → 36^6 = 2.18 billion combos
         *
         * Format: "MC-XXXXXX" where X is uppercase alphanumeric from the UID.
         * Example: "MC-A3B7KQ"
         */
        fun generateMagicId(uid: String): String {
            // Take the first 6 alphanumeric characters from the UID (UIDs are already random)
            val alphanum = uid.filter { it.isLetterOrDigit() }.take(6).uppercase()
            // Pad with zeros if somehow shorter (very unlikely with Firebase UIDs)
            val padded = alphanum.padEnd(6, '0')
            return "MC-$padded"
        }
    }

    private val db = FirebaseFirestore.getInstance()

    // Local state
    var myMagicId: String = ""
    var myUid: String = ""
    var partnerMagicId: String = ""
    var partnerUid: String = ""
    var sessionId: String = ""
    var isMirrorEnabled: Boolean = false
        private set
    var isConnected: Boolean = false

    // Track whether registration in Firestore succeeded
    private var isRegisteredInFirestore: Boolean = false

    // Active Firestore listeners
    private var requestListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    // Callback interface for UI/Activity events
    interface P2PEventListener {
        fun onIncomingRequest(fromMagicId: String, requestDocId: String)
        fun onConnected(partnerMagicId: String, sessionId: String)
        fun onDisconnected()
        fun onRemoteDisplayUpdate(display: String, formula: String)
        fun onRemoteCommand(command: String)
        fun onError(message: String)
    }

    var listener: P2PEventListener? = null

    // =========================================================================
    // INITIALIZATION
    // =========================================================================

    /**
     * Call once at app start / SecretActivity open.
     * Generates or fetches Magic ID, then starts listening for incoming requests.
     */
    fun initialize(onReady: (magicId: String) -> Unit) {
        val uid = AuthManager.currentUid ?: run {
            Log.e(TAG, "No auth UID found — user not logged in")
            listener?.onError("Not logged in. Please sign in to use Online Magic Link.")
            return
        }
        myUid = uid
        myMagicId = generateMagicId(uid)
        isRegisteredInFirestore = false

        Log.d(TAG, "Initializing P2P for uid=$uid magicId=$myMagicId")

        // Deliver ID immediately so UI can show it while Firestore registers
        onReady(myMagicId)

        // Register magic ID in Firestore so partners can look us up
        registerMagicId { success, error ->
            if (success) {
                isRegisteredInFirestore = true
                Log.d(TAG, "Magic ID registered successfully: $myMagicId")
                startListeningForRequests()
            } else {
                Log.e(TAG, "Failed to register Magic ID: $error")
                // Still try to listen for requests even if registration failed
                startListeningForRequests()
            }
        }
    }

    /**
     * Registers this device's magic ID in Firestore.
     * Retries once if it fails.
     */
    private fun registerMagicId(onDone: (success: Boolean, error: String?) -> Unit) {
        val magicIdData = mapOf("uid" to myUid)

        db.collection("magic_ids").document(myMagicId).set(magicIdData)
            .addOnSuccessListener {
                // Also save to user profile (best-effort, don't fail on this)
                db.collection("users").document(myUid)
                    .set(mapOf("magicId" to myMagicId, "lastSeen" to System.currentTimeMillis()),
                        com.google.firebase.firestore.SetOptions.merge())
                onDone(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "registerMagicId failed: ${e.message}", e)
                // Retry once after 2 seconds
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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

    // =========================================================================
    // SEND CONNECTION REQUEST
    // =========================================================================

    fun sendRequest(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit) {
        val cleanId = toMagicId.trim().uppercase()

        // Validate format: accept both "MC-XXXXXX" (new) and "M-XXXX" (legacy)
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

        // If we're not registered yet, re-register first then send
        if (!isRegisteredInFirestore) {
            Log.w(TAG, "Not yet registered in Firestore — registering now before sending request")
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
        Log.d(TAG, "Looking up Magic ID: $cleanId")

        db.collection("magic_ids").document(cleanId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(false, "Magic ID '$cleanId' not found.\n\nMake sure your partner:\n• Has the app open\n• Is logged in\n• Has tapped the Magic Link panel at least once")
                    return@addOnSuccessListener
                }

                val toUid = doc.getString("uid") ?: run {
                    onResult(false, "Partner data is corrupted. Ask them to re-open the Magic Link panel.")
                    return@addOnSuccessListener
                }

                val requestData = mapOf(
                    "fromId"    to myMagicId,
                    "fromUid"   to myUid,
                    "toId"      to cleanId,
                    "toUid"     to toUid,
                    "status"    to "pending",
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("requests").add(requestData)
                    .addOnSuccessListener { docRef ->
                        Log.d(TAG, "Request sent to $cleanId, docId=${docRef.id}")
                        onResult(true, "✅ Request sent to $cleanId! Ask your partner to accept it.")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to create request doc: ${e.message}", e)
                        onResult(false, "Failed to send request.\nError: ${e.message}\n\nCheck Firestore security rules.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firestore lookup failed for $cleanId: ${e.message}", e)
                onResult(false, "Cannot reach Firebase.\n\nError: ${e.message}\n\nPossible fixes:\n• Check internet connection\n• Enable Firestore in Firebase Console\n• Update Firestore Security Rules (see README)")
            }
    }

    // =========================================================================
    // LISTEN FOR INCOMING REQUESTS
    // =========================================================================

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
                        Log.d(TAG, "Incoming request from $fromId (doc=${doc.id})")
                        listener?.onIncomingRequest(fromId, doc.id)
                    }
                }
            }
    }

    // =========================================================================
    // ACCEPT / DECLINE REQUEST
    // =========================================================================

    fun acceptRequest(requestDocId: String, fromMagicId: String) {
        db.collection("magic_ids").document(fromMagicId).get()
            .addOnSuccessListener { doc ->
                val fromUid = doc.getString("uid") ?: return@addOnSuccessListener

                val newSessionId = "${myUid.take(8)}_${fromUid.take(8)}"
                sessionId = newSessionId
                partnerMagicId = fromMagicId
                partnerUid = fromUid

                val sessionData = mapOf(
                    "masterUid"     to fromUid,
                    "slaveUid"      to myUid,
                    "masterMagicId" to fromMagicId,
                    "slaveMagicId"  to myMagicId,
                    "display"       to "",
                    "formula"       to "",
                    "command"       to "",
                    "commandSentBy" to "",
                    "mirrorEnabled" to false,
                    "updatedBy"     to "",
                    "updatedAt"     to System.currentTimeMillis()
                )

                db.collection("sessions").document(newSessionId).set(sessionData)
                    .addOnSuccessListener {
                        db.collection("requests").document(requestDocId)
                            .update(mapOf("status" to "accepted", "sessionId" to newSessionId))
                        isConnected = true
                        startListeningToSession(newSessionId)
                        listener?.onConnected(fromMagicId, newSessionId)
                        Log.d(TAG, "Connected! Session: $newSessionId")
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

    // =========================================================================
    // SESSION LISTENER — For the MASTER (the one who sent the request)
    // =========================================================================

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

    // =========================================================================
    // SESSION LISTENER — Real-time display mirroring
    // =========================================================================

    fun startListeningToSession(sessionId: String) {
        sessionListener?.remove()
        sessionListener = db.collection("sessions").document(sessionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Session listener error: ${error?.message}", error)
                    return@addSnapshotListener
                }

                val updatedBy = snapshot.getString("updatedBy") ?: ""
                if (updatedBy == myUid) return@addSnapshotListener // ignore own writes

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

    // =========================================================================
    // SEND DATA TO PARTNER
    // =========================================================================

    fun syncDisplay(display: String, formula: String) {
        if (!isConnected || !isMirrorEnabled || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId).update(
            mapOf(
                "display"   to display,
                "formula"   to formula,
                "updatedBy" to myUid,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    fun sendCommand(command: String) {
        if (!isConnected || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId).update(
            mapOf(
                "command"       to command,
                "commandSentBy" to myUid,
                "updatedAt"     to System.currentTimeMillis()
            )
        )
    }

    fun setMirrorEnabled(enabled: Boolean) {
        isMirrorEnabled = enabled
        if (!isConnected || sessionId.isEmpty()) return
        db.collection("sessions").document(sessionId)
            .update(mapOf("mirrorEnabled" to enabled))
    }

    // =========================================================================
    // DISCONNECT
    // =========================================================================

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
