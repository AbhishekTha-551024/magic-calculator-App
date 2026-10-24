package com.magic.calculator.feature.settings.presentation.state

/**
 * UI State for Magic Settings screen.
 */
data class SettingsUiState(
    val shakeSensitivity: Float = 15f,
    val soundVolume: Int = 80,
    val hapticIntensity: Int = 80
)
