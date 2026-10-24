package com.magic.calculator.feature.secret.domain.repository

import com.magic.calculator.feature.secret.domain.model.SwitchboardConfig

/**
 * Domain contract for Secret Switchboard and hardware sensor settings.
 */
interface SecretRepository {
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
