package com.magic.calculator.feature.vault.data.datasource

import android.content.Context
import android.util.Log
import com.magic.calculator.core.logic.SettingsManager

interface VaultLocalDataSource {
    fun listGalleryAssets(): List<String>
    fun getActiveMagic(): String
    fun isDarkTheme(): Boolean
    fun setDarkTheme(isDark: Boolean)
}

class AssetVaultLocalDataSource(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager(context)
) : VaultLocalDataSource {

    override fun listGalleryAssets(): List<String> {
        return try {
            val allFiles = context.assets.list("gallery")
            allFiles?.filter {
                val lower = it.lowercase()
                lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
            }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e("AssetVaultLocalDataSource", "Error listing gallery assets", e)
            emptyList()
        }
    }

    override fun getActiveMagic(): String = settingsManager.activeMagic

    override fun isDarkTheme(): Boolean = settingsManager.isGalleryThemeDark

    override fun setDarkTheme(isDark: Boolean) {
        settingsManager.isGalleryThemeDark = isDark
    }
}
