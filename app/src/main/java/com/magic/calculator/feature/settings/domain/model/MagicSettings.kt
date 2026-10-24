package com.magic.calculator.feature.settings.domain.model

/**
 * Domain model representing configurable magic calculator settings.
 */
data class MagicSettings(
    val shakeSensitivity: Float = 15f,
    val soundVolume: Int = 80,
    val hapticIntensity: Int = 80
)
