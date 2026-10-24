package com.magic.calculator.feature.settings.domain.usecase

import com.magic.calculator.feature.settings.domain.repository.SettingsRepository

class UpdateHapticIntensityUseCase(private val repository: SettingsRepository) {
    operator fun invoke(value: Int) {
        repository.setHapticIntensity(value)
    }
}
