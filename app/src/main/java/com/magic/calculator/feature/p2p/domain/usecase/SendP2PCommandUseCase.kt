package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class SendP2PCommandUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke(command: String) {
        repository.sendCommand(command)
    }
}
