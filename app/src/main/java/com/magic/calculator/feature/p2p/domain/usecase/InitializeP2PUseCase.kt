package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class InitializeP2PUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke(onReady: (magicId: String) -> Unit) = repository.initialize(onReady)
}
