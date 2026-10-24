package com.magic.calculator.feature.vault.domain.usecase

import com.magic.calculator.feature.vault.domain.model.VaultAlbum
import com.magic.calculator.feature.vault.domain.repository.VaultRepository

class GetVaultAlbumsUseCase(
    private val repository: VaultRepository
) {
    operator fun invoke(): List<VaultAlbum> = repository.getVaultAlbums()
}
