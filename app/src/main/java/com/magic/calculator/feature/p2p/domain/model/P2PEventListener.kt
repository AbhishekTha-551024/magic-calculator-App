package com.magic.calculator.feature.p2p.domain.model

interface P2PEventListener {
    fun onIncomingRequest(fromMagicId: String, requestDocId: String)
    fun onConnected(partnerMagicId: String, sessionId: String)
    fun onDisconnected()
    fun onRemoteDisplayUpdate(display: String, formula: String)
    fun onRemoteCommand(command: String)
    fun onError(message: String)
}
