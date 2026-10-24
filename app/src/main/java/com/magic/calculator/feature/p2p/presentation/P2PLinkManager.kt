package com.magic.calculator.feature.p2p.presentation

import com.magic.calculator.feature.p2p.data.repository.P2PRepositoryImpl
import com.magic.calculator.feature.p2p.domain.model.P2PEventListener
import com.magic.calculator.feature.p2p.domain.repository.P2PRepository


class P2PLinkManager private constructor(
    private val repository: P2PRepository = P2PRepositoryImpl()
) {

    companion object {
        @Volatile
        private var instance: P2PLinkManager? = null

        fun getInstance(): P2PLinkManager {
            return instance ?: synchronized(this) {
                instance ?: P2PLinkManager().also { instance = it }
            }
        }

        fun generateMagicId(uid: String): String {
            val alphanum = uid.filter { it.isLetterOrDigit() }.take(6).uppercase()
            val padded = alphanum.padEnd(6, '0')
            return "MC-$padded"
        }
    }

    var listener: P2PEventListener?
        get() = null
        set(value) {
            repository.setListener(value)
        }

    val myMagicId: String
        get() = repository.myMagicId

    val partnerMagicId: String
        get() = repository.partnerMagicId

    val sessionId: String
        get() = repository.sessionId

    val isConnected: Boolean
        get() = repository.isConnected

    val isMirrorEnabled: Boolean
        get() = repository.isMirrorEnabled

    fun initialize(onReady: (magicId: String) -> Unit) {
        repository.initialize(onReady)
    }

    fun sendRequest(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit) {
        repository.sendRequest(toMagicId, onResult)
    }

    fun acceptRequest(requestDocId: String, fromMagicId: String) {
        repository.acceptRequest(requestDocId, fromMagicId)
    }

    fun declineRequest(requestDocId: String) {
        repository.declineRequest(requestDocId)
    }

    fun startListeningForRequests() {
        repository.startListeningForRequests()
    }

    fun listenForAcceptedRequest(onAccepted: (sessionId: String, partnerMagicId: String) -> Unit) {
        repository.listenForAcceptedRequest(onAccepted)
    }

    fun syncDisplay(display: String, formula: String) {
        repository.syncDisplay(display, formula)
    }

    fun sendCommand(command: String) {
        repository.sendCommand(command)
    }

    fun setMirrorEnabled(enabled: Boolean) {
        repository.setMirrorEnabled(enabled)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun stopAll() {
        repository.stopAll()
    }
}
