package com.magic.calculator.feature.settings.domain.usecase

import com.magic.calculator.feature.settings.domain.repository.SettingsRepository

class UpdateShakeSensitivityUseCase(private val repository: SettingsRepository) {
    operator fun invoke(value: Float) {
        repository.setShakeSensitivity(value)
    }
}
