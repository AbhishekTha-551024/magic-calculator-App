package com.magic.calculator.feature.settings.domain.usecase

import com.magic.calculator.feature.settings.domain.model.MagicSettings
import com.magic.calculator.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class GetSettingsUseCase(private val repository: SettingsRepository) {
    operator fun invoke(): StateFlow<MagicSettings> = repository.settings
}
