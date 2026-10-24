package com.magic.calculator.feature.applock.domain.repository

import com.magic.calculator.feature.applock.domain.model.LockConfig
import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import java.io.File

/**
 * Domain repository contract for AppLock / MoiLock security engine.
 */
interface AppLockRepository {
    fun verifyPin(candidatePin: String): UnlockStatus
    fun resetLockTrick()
    fun getLockConfig(): LockConfig
    fun saveLockConfig(secretDigit: String, fakeDate: String)
    fun setCustomWallpaperPath(path: String?)
    fun getWallpaperFile(): File?
    fun isAppLockActive(): Boolean
    fun setAppLockActive(active: Boolean)
}
