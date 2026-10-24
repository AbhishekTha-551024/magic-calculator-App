package com.magic.calculator.feature.settings.domain.usecase

import com.magic.calculator.feature.settings.domain.repository.SettingsRepository

class UpdateSoundVolumeUseCase(private val repository: SettingsRepository) {
    operator fun invoke(value: Int) {
        repository.setSoundVolume(value)
    }
}
