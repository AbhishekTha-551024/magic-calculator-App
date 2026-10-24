package com.magic.calculator.feature.p2p.domain.model

sealed class P2PConnectionState {
    object Disconnected : P2PConnectionState()
    data class Connecting(val magicId: String) : P2PConnectionState()
    data class Connected(val partnerMagicId: String, val sessionId: String) : P2PConnectionState()
    data class IncomingRequest(val fromMagicId: String, val requestDocId: String) : P2PConnectionState()
    data class Error(val message: String) : P2PConnectionState()
}
