package com.magic.calculator.feature.secret.data.datasource

import com.magic.calculator.feature.secret.domain.model.SwitchboardConfig
import com.magic.calculator.core.logic.SettingsManager

interface SecretLocalDataSource {
    fun getSwitchboardConfig(): SwitchboardConfig
    fun setActiveMagic(magicKey: String)
    fun setSensorTrigger(triggerKey: String)
    fun setAppMode(mode: Int)
    fun saveCalibration(
        sensitivity: Float,
        volume: Int,
        haptic: Int,
        forcedPhone: String,
        flipTrigger: String,
        flipResult: String
    )
    fun magicNeedsSensor(key: String): Boolean
}

class SharedPreferencesSecretLocalDataSource(
    private val settingsManager: SettingsManager
) : SecretLocalDataSource {

    override fun getSwitchboardConfig(): SwitchboardConfig {
        return SwitchboardConfig(
            activeMagic = settingsManager.activeMagic,
            activeSensorTrigger = settingsManager.activeSensorTrigger,
            appMode = settingsManager.appMode,
            isP2PEnabled = settingsManager.isP2PEnabled,
            isContactForceEnabled = settingsManager.isContactForceEnabled,
            isExplosionEnabled = settingsManager.isExplosionEnabled,
            shakeSensitivity = settingsManager.shakeSensitivity,
            soundVolume = settingsManager.soundVolume,
            hapticIntensity = settingsManager.hapticIntensity,
            forcedPhoneNumber = settingsManager.forcedPhoneNumber,
            customFlipTrigger = settingsManager.customFlipTrigger,
            customFlipResult = settingsManager.customFlipResult
        )
    }

    override fun setActiveMagic(magicKey: String) {
        settingsManager.activeMagic = magicKey
        settingsManager.isContactForceEnabled = (magicKey == "CONTACT_FORCE")
        settingsManager.isExplosionEnabled = (magicKey == "BUTTON_EXPLOSION")
        if (magicKey == "MOILOCK") {
            settingsManager.appMode = 4
        } else if (settingsManager.appMode == 4) {
            settingsManager.appMode = 0
        }
        if (magicKey != "P2P_LINK") {
            settingsManager.isP2PEnabled = false
        }
    }

    override fun setSensorTrigger(triggerKey: String) {
        settingsManager.activeSensorTrigger = triggerKey
    }

    override fun setAppMode(mode: Int) {
        settingsManager.appMode = mode
    }

    override fun saveCalibration(
        sensitivity: Float,
        volume: Int,
        haptic: Int,
        forcedPhone: String,
        flipTrigger: String,
        flipResult: String
    ) {
        settingsManager.shakeSensitivity = sensitivity
        settingsManager.soundVolume = volume
        settingsManager.hapticIntensity = haptic
        settingsManager.forcedPhoneNumber = forcedPhone
        settingsManager.customFlipTrigger = flipTrigger
        settingsManager.customFlipResult = flipResult
    }

    override fun magicNeedsSensor(key: String): Boolean {
        return key in listOf("DIGIT_FLIP", "GHOST_TYPING", "REAPPEARING_RESULT")
    }
}
