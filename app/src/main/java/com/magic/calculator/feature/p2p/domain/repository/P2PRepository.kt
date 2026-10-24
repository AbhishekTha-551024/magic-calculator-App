package com.magic.calculator.feature.p2p.domain.repository

import com.magic.calculator.feature.p2p.domain.model.P2PEventListener

interface P2PRepository {
    var myMagicId: String
    var partnerMagicId: String
    var sessionId: String
    val isConnected: Boolean
    val isMirrorEnabled: Boolean

    fun initialize(onReady: (magicId: String) -> Unit)
    fun sendRequest(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit)
    fun acceptRequest(requestDocId: String, fromMagicId: String)
    fun declineRequest(requestDocId: String)
    fun startListeningForRequests()
    fun listenForAcceptedRequest(onAccepted: (sessionId: String, partnerMagicId: String) -> Unit)
    fun syncDisplay(display: String, formula: String)
    fun sendCommand(command: String)
    fun setMirrorEnabled(enabled: Boolean)
    fun disconnect()
    fun stopAll()
    fun setListener(listener: P2PEventListener?)
}
