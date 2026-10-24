package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class DisconnectP2PUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke() {
        repository.disconnect()
    }
}
