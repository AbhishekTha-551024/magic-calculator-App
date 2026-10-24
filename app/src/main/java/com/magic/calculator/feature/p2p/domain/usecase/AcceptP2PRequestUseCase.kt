package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class AcceptP2PRequestUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke(requestDocId: String, fromMagicId: String) {
        repository.acceptRequest(requestDocId, fromMagicId)
    }
}
