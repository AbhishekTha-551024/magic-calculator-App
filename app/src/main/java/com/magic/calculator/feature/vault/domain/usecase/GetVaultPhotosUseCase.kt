package com.magic.calculator.feature.vault.domain.usecase

import com.magic.calculator.feature.vault.domain.model.VaultMedia
import com.magic.calculator.feature.vault.domain.repository.VaultRepository

class GetVaultPhotosUseCase(
    private val repository: VaultRepository
) {
    operator fun invoke(): List<VaultMedia> = repository.getVaultPhotos()
}
