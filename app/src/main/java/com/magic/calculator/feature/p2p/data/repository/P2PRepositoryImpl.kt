package com.magic.calculator.feature.p2p.data.repository

import com.magic.calculator.feature.p2p.data.datasource.FirestoreP2PDataSource
import com.magic.calculator.feature.p2p.domain.model.P2PEventListener
import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class P2PRepositoryImpl(
    private val dataSource: FirestoreP2PDataSource = FirestoreP2PDataSource()
) : P2PRepository {

    override var myMagicId: String
        get() = dataSource.myMagicId
        set(value) { dataSource.myMagicId = value }

    override var partnerMagicId: String
        get() = dataSource.partnerMagicId
        set(value) { dataSource.partnerMagicId = value }

    override var sessionId: String
        get() = dataSource.sessionId
        set(value) { dataSource.sessionId = value }

    override val isConnected: Boolean
        get() = dataSource.isConnected

    override val isMirrorEnabled: Boolean
        get() = dataSource.isMirrorEnabled

    override fun initialize(onReady: (magicId: String) -> Unit) {
        dataSource.initialize(onReady)
    }

    override fun sendRequest(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit) {
        dataSource.sendRequest(toMagicId, onResult)
    }

    override fun acceptRequest(requestDocId: String, fromMagicId: String) {
        dataSource.acceptRequest(requestDocId, fromMagicId)
    }

    override fun declineRequest(requestDocId: String) {
        dataSource.declineRequest(requestDocId)
    }

    override fun startListeningForRequests() {
        dataSource.startListeningForRequests()
    }

    override fun listenForAcceptedRequest(onAccepted: (sessionId: String, partnerMagicId: String) -> Unit) {
        dataSource.listenForAcceptedRequest(onAccepted)
    }

    override fun syncDisplay(display: String, formula: String) {
        dataSource.syncDisplay(display, formula)
    }

    override fun sendCommand(command: String) {
        dataSource.sendCommand(command)
    }

    override fun setMirrorEnabled(enabled: Boolean) {
        dataSource.setMirrorEnabled(enabled)
    }

    override fun disconnect() {
        dataSource.disconnect()
    }

    override fun stopAll() {
        dataSource.stopAll()
    }

    override fun setListener(listener: P2PEventListener?) {
        dataSource.listener = listener
    }
}
