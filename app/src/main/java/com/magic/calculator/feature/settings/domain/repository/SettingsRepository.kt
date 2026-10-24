package com.magic.calculator.feature.settings.domain.repository

import com.magic.calculator.feature.settings.domain.model.MagicSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for managing app settings.
 */
interface SettingsRepository {
    val settings: StateFlow<MagicSettings>

    fun getShakeSensitivity(): Float
    fun setShakeSensitivity(value: Float)

    fun getSoundVolume(): Int
    fun setSoundVolume(value: Int)

    fun getHapticIntensity(): Int
    fun setHapticIntensity(value: Int)

    // Extended settings accessors
    var activeMagic: String
    var forcedPhoneNumber: String
    var forcedLuckyNumber: String
    var customFlipTrigger: String
    var customFlipResult: String
    var secretUnlockCode: String
    var appMode: Int
    var userPhoneNumber: String
    var isDialerThemeActive: Boolean
    var explosionTrigger: String
    var isExplosionEnabled: Boolean
    var activeSensorTrigger: String
    var toxicMode: Int
    var ghostTypingNumber: String
    var ghostTypingDelay: Int
    var forcedGalleryTime: String
    var forcedContactName: String
    var forcedContactPhone: String
    var isContactForceEnabled: Boolean
    var isP2PEnabled: Boolean
    var p2pSessionId: String
    var p2pPartnerMagicId: String
    var isP2PMirrorEnabled: Boolean
    var isGalleryThemeDark: Boolean
    var isDialerThemeDark: Boolean
    var stepRevealSequence: String

    // Wallpaper & Voice Magic Settings
    var isWallpaperMagicEnabled: Boolean
    var wallpaperMagicTarget: Int
    var forcedWallpaperNumber: String
    var isVoiceListenerEnabled: Boolean
    var wallpaperStyleIndex: Int
    var wallpaperTriggerMethod: String
    var wallpaperContentType: String
    var forcedWallpaperCard: String
}
