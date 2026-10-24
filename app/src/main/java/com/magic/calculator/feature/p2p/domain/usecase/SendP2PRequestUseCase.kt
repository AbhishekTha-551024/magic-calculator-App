package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class SendP2PRequestUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke(toMagicId: String, onResult: (success: Boolean, message: String) -> Unit) {
        repository.sendRequest(toMagicId, onResult)
    }
}
