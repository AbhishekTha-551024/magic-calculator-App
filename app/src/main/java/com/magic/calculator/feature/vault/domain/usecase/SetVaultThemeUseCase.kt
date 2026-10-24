package com.magic.calculator.feature.vault.domain.usecase

import com.magic.calculator.feature.vault.domain.repository.VaultRepository

class SetVaultThemeUseCase(
    private val repository: VaultRepository
) {
    operator fun invoke(isDark: Boolean) = repository.setDarkTheme(isDark)
}
