package com.magic.calculator.feature.settings.data.repository

import com.magic.calculator.feature.settings.data.datasource.SettingsLocalDataSource
import com.magic.calculator.feature.settings.domain.model.MagicSettings
import com.magic.calculator.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepositoryImpl(
    private val localDataSource: SettingsLocalDataSource
) : SettingsRepository {

    private val _settings = MutableStateFlow(localDataSource.getSettings())
    override val settings: StateFlow<MagicSettings> = _settings.asStateFlow()

    override fun getShakeSensitivity(): Float {
        return _settings.value.shakeSensitivity
    }

    override fun setShakeSensitivity(value: Float) {
        localDataSource.setShakeSensitivity(value)
        _settings.value = _settings.value.copy(shakeSensitivity = value)
    }

    override fun getSoundVolume(): Int {
        return _settings.value.soundVolume
    }

    override fun setSoundVolume(value: Int) {
        localDataSource.setSoundVolume(value)
        _settings.value = _settings.value.copy(soundVolume = value)
    }

    override fun getHapticIntensity(): Int {
        return _settings.value.hapticIntensity
    }

    override fun setHapticIntensity(value: Int) {
        localDataSource.setHapticIntensity(value)
        _settings.value = _settings.value.copy(hapticIntensity = value)
    }

    override var activeMagic: String
        get() = localDataSource.activeMagic
        set(value) { localDataSource.activeMagic = value }

    override var forcedPhoneNumber: String
        get() = localDataSource.forcedPhoneNumber
        set(value) { localDataSource.forcedPhoneNumber = value }

    override var forcedLuckyNumber: String
        get() = localDataSource.forcedLuckyNumber
        set(value) { localDataSource.forcedLuckyNumber = value }

    override var customFlipTrigger: String
        get() = localDataSource.customFlipTrigger
        set(value) { localDataSource.customFlipTrigger = value }

    override var customFlipResult: String
        get() = localDataSource.customFlipResult
        set(value) { localDataSource.customFlipResult = value }

    override var secretUnlockCode: String
        get() = localDataSource.secretUnlockCode
        set(value) { localDataSource.secretUnlockCode = value }

    override var appMode: Int
        get() = localDataSource.appMode
        set(value) { localDataSource.appMode = value }

    override var userPhoneNumber: String
        get() = localDataSource.userPhoneNumber
        set(value) { localDataSource.userPhoneNumber = value }

    override var isDialerThemeActive: Boolean
        get() = localDataSource.isDialerThemeActive
        set(value) { localDataSource.isDialerThemeActive = value }

    override var explosionTrigger: String
        get() = localDataSource.explosionTrigger
        set(value) { localDataSource.explosionTrigger = value }

    override var isExplosionEnabled: Boolean
        get() = localDataSource.isExplosionEnabled
        set(value) { localDataSource.isExplosionEnabled = value }

    override var activeSensorTrigger: String
        get() = localDataSource.activeSensorTrigger
        set(value) { localDataSource.activeSensorTrigger = value }

    override var toxicMode: Int
        get() = localDataSource.toxicMode
        set(value) { localDataSource.toxicMode = value }

    override var ghostTypingNumber: String
        get() = localDataSource.ghostTypingNumber
        set(value) { localDataSource.ghostTypingNumber = value }

    override var ghostTypingDelay: Int
        get() = localDataSource.ghostTypingDelay
        set(value) { localDataSource.ghostTypingDelay = value }

    override var forcedGalleryTime: String
        get() = localDataSource.forcedGalleryTime
        set(value) { localDataSource.forcedGalleryTime = value }

    override var forcedContactName: String
        get() = localDataSource.forcedContactName
        set(value) { localDataSource.forcedContactName = value }

    override var forcedContactPhone: String
        get() = localDataSource.forcedContactPhone
        set(value) { localDataSource.forcedContactPhone = value }

    override var isContactForceEnabled: Boolean
        get() = localDataSource.isContactForceEnabled
        set(value) { localDataSource.isContactForceEnabled = value }

    override var isP2PEnabled: Boolean
        get() = localDataSource.isP2PEnabled
        set(value) { localDataSource.isP2PEnabled = value }

    override var p2pSessionId: String
        get() = localDataSource.p2pSessionId
        set(value) { localDataSource.p2pSessionId = value }

    override var p2pPartnerMagicId: String
        get() = localDataSource.p2pPartnerMagicId
        set(value) { localDataSource.p2pPartnerMagicId = value }

    override var isP2PMirrorEnabled: Boolean
        get() = localDataSource.isP2PMirrorEnabled
        set(value) { localDataSource.isP2PMirrorEnabled = value }

    override var isGalleryThemeDark: Boolean
        get() = localDataSource.isGalleryThemeDark
        set(value) { localDataSource.isGalleryThemeDark = value }

    override var isDialerThemeDark: Boolean
        get() = localDataSource.isDialerThemeDark
        set(value) { localDataSource.isDialerThemeDark = value }

    override var stepRevealSequence: String
        get() = localDataSource.stepRevealSequence
        set(value) { localDataSource.stepRevealSequence = value }

    // Wallpaper & Voice Magic Settings
    override var isWallpaperMagicEnabled: Boolean
        get() = localDataSource.isWallpaperMagicEnabled
        set(value) { localDataSource.isWallpaperMagicEnabled = value }

    override var wallpaperMagicTarget: Int
        get() = localDataSource.wallpaperMagicTarget
        set(value) { localDataSource.wallpaperMagicTarget = value }

    override var forcedWallpaperNumber: String
        get() = localDataSource.forcedWallpaperNumber
        set(value) { localDataSource.forcedWallpaperNumber = value }

    override var isVoiceListenerEnabled: Boolean
        get() = localDataSource.isVoiceListenerEnabled
        set(value) { localDataSource.isVoiceListenerEnabled = value }

    override var wallpaperStyleIndex: Int
        get() = localDataSource.wallpaperStyleIndex
        set(value) { localDataSource.wallpaperStyleIndex = value }

    override var wallpaperTriggerMethod: String
        get() = localDataSource.wallpaperTriggerMethod
        set(value) { localDataSource.wallpaperTriggerMethod = value }

    override var wallpaperContentType: String
        get() = localDataSource.wallpaperContentType
        set(value) { localDataSource.wallpaperContentType = value }

    override var forcedWallpaperCard: String
        get() = localDataSource.forcedWallpaperCard
        set(value) { localDataSource.forcedWallpaperCard = value }
}
