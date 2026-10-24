package com.magic.calculator.feature.vault.domain.usecase

import com.magic.calculator.feature.vault.domain.repository.VaultRepository

class GetVaultThemeUseCase(
    private val repository: VaultRepository
) {
    operator fun invoke(): Boolean = repository.isDarkTheme()
}
