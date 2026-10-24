package com.magic.calculator.feature.secret.data.repository

import com.magic.calculator.feature.secret.data.datasource.SecretLocalDataSource
import com.magic.calculator.feature.secret.domain.model.SwitchboardConfig
import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class SecretRepositoryImpl(
    private val localDataSource: SecretLocalDataSource
) : SecretRepository {

    override fun getSwitchboardConfig(): SwitchboardConfig {
        return localDataSource.getSwitchboardConfig()
    }

    override fun setActiveMagic(magicKey: String) {
        localDataSource.setActiveMagic(magicKey)
    }

    override fun setSensorTrigger(triggerKey: String) {
        localDataSource.setSensorTrigger(triggerKey)
    }

    override fun setAppMode(mode: Int) {
        localDataSource.setAppMode(mode)
    }

    override fun saveCalibration(
        sensitivity: Float,
        volume: Int,
        haptic: Int,
        forcedPhone: String,
        flipTrigger: String,
        flipResult: String
    ) {
        localDataSource.saveCalibration(
            sensitivity = sensitivity,
            volume = volume,
            haptic = haptic,
            forcedPhone = forcedPhone,
            flipTrigger = flipTrigger,
            flipResult = flipResult
        )
    }

    override fun magicNeedsSensor(key: String): Boolean {
        return localDataSource.magicNeedsSensor(key)
    }
}
