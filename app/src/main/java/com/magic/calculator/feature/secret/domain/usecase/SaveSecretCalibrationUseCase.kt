package com.magic.calculator.feature.secret.domain.usecase

import com.magic.calculator.feature.secret.domain.repository.SecretRepository

class SaveSecretCalibrationUseCase(
    private val repository: SecretRepository
) {
    operator fun invoke(
        sensitivity: Float,
        volume: Int,
        haptic: Int,
        forcedPhone: String,
        flipTrigger: String,
        flipResult: String
    ) {
        repository.saveCalibration(
            sensitivity = sensitivity,
            volume = volume,
            haptic = haptic,
            forcedPhone = forcedPhone,
            flipTrigger = flipTrigger,
            flipResult = flipResult
        )
    }
}
