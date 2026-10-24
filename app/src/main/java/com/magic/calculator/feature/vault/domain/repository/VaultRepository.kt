package com.magic.calculator.feature.vault.domain.repository

import com.magic.calculator.feature.vault.domain.model.VaultAlbum
import com.magic.calculator.feature.vault.domain.model.VaultMedia

interface VaultRepository {
    fun getVaultPhotos(): List<VaultMedia>
    fun getVaultAlbums(): List<VaultAlbum>
    fun isDarkTheme(): Boolean
    fun setDarkTheme(isDark: Boolean)
}
