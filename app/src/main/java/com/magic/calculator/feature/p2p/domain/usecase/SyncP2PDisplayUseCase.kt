package com.magic.calculator.feature.p2p.domain.usecase

import com.magic.calculator.feature.p2p.domain.repository.P2PRepository

class SyncP2PDisplayUseCase(
    private val repository: P2PRepository
) {
    operator fun invoke(display: String, formula: String) {
        repository.syncDisplay(display, formula)
    }
}
