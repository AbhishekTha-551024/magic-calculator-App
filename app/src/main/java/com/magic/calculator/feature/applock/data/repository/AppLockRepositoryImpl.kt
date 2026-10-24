package com.magic.calculator.feature.applock.data.repository

import com.magic.calculator.feature.applock.data.datasource.AppLockLocalDataSource
import com.magic.calculator.feature.applock.domain.model.LockConfig
import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import com.magic.calculator.feature.applock.domain.repository.AppLockRepository
import java.io.File

class AppLockRepositoryImpl(
    private val localDataSource: AppLockLocalDataSource
) : AppLockRepository {

    override fun verifyPin(candidatePin: String): UnlockStatus {
        return localDataSource.verifyPin(candidatePin)
    }

    override fun resetLockTrick() {
        localDataSource.resetLock()
    }

    override fun getLockConfig(): LockConfig {
        return localDataSource.getLockConfig()
    }

    override fun saveLockConfig(secretDigit: String, fakeDate: String) {
        localDataSource.saveLockConfig(secretDigit, fakeDate)
    }

    override fun setCustomWallpaperPath(path: String?) {
        localDataSource.setCustomWallpaperPath(path)
    }

    override fun getWallpaperFile(): File? {
        return localDataSource.getWallpaperFile()
    }

    override fun isAppLockActive(): Boolean {
        return localDataSource.isAppLockActive()
    }

    override fun setAppLockActive(active: Boolean) {
        localDataSource.setAppLockActive(active)
    }
}
