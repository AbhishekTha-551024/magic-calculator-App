package com.magic.calculator.feature.secret.domain.model

/**
 * Snapshot of all switchboard, trick trigger, and sensor calibration states.
 */
data class SwitchboardConfig(
    val activeMagic: String,
    val activeSensorTrigger: String,
    val appMode: Int,
    val isP2PEnabled: Boolean,
    val isContactForceEnabled: Boolean,
    val isExplosionEnabled: Boolean,
    val shakeSensitivity: Float,
    val soundVolume: Int,
    val hapticIntensity: Int,
    val forcedPhoneNumber: String,
    val customFlipTrigger: String,
    val customFlipResult: String
)
