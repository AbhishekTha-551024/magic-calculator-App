package com.magic.calculator.feature.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.magic.calculator.feature.settings.domain.model.MagicSettings
import com.magic.calculator.feature.settings.domain.repository.SettingsRepository
import com.magic.calculator.feature.settings.presentation.state.SettingsUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = settingsRepository.settings.value.toUiState()
        )

    fun updateShakeSensitivity(value: Float) {
        settingsRepository.setShakeSensitivity(value)
    }

    fun updateSoundVolume(volume: Int) {
        settingsRepository.setSoundVolume(volume)
    }

    fun updateHapticIntensity(intensity: Int) {
        settingsRepository.setHapticIntensity(intensity)
    }

    private fun MagicSettings.toUiState(): SettingsUiState = SettingsUiState(
        shakeSensitivity = shakeSensitivity,
        soundVolume = soundVolume,
        hapticIntensity = hapticIntensity
    )

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
