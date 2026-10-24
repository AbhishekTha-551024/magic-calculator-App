package com.magic.calculator.feature.applock.domain.model

/**
 * Configuration and state settings for App Lock / MoiLock trick.
 */
data class LockConfig(
    val secretDigit: String,
    val fakeDate: String,
    val isFirstUnlockDone: Boolean,
    val customWallpaperPath: String? = null
)
