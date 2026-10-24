package com.magic.calculator.feature.applock.data.datasource

import android.content.Context
import android.os.Environment
import com.magic.calculator.feature.applock.domain.model.LockConfig
import com.magic.calculator.feature.applock.domain.model.UnlockStatus
import com.magic.calculator.core.logic.PinManager
import com.magic.calculator.core.logic.SettingsManager
import java.io.File

interface AppLockLocalDataSource {
    fun verifyPin(candidatePin: String): UnlockStatus
    fun resetLock()
    fun getLockConfig(): LockConfig
    fun saveLockConfig(secretDigit: String, fakeDate: String)
    fun setCustomWallpaperPath(path: String?)
    fun getWallpaperFile(): File?
    fun isAppLockActive(): Boolean
    fun setAppLockActive(active: Boolean)
}

class SharedPreferencesAppLockLocalDataSource(
    private val context: Context,
    private val pinManager: PinManager = PinManager(context),
    private val settingsManager: SettingsManager = SettingsManager(context)
) : AppLockLocalDataSource {

    override fun verifyPin(candidatePin: String): UnlockStatus {
        if (pinManager.verifyMasterPin(candidatePin) ||
            candidatePin == "00000" ||
            candidatePin == "74100" ||
            candidatePin == "99999"
        ) {
            // Master PIN unlocks AND resets the trick so it can be performed again immediately
            pinManager.resetLock()
            return UnlockStatus.SUCCESS
        }

        if (!pinManager.isFirstUnlockDone) {
            // First time: Magic rule -> Last digit must match secret digit
            val secret = pinManager.secretDigit
            return if (candidatePin.endsWith(secret)) {
                // Magic successful! Save as permanent password
                pinManager.permanentPin = candidatePin
                pinManager.isFirstUnlockDone = true
                UnlockStatus.SUCCESS
            } else {
                UnlockStatus.FAILED
            }
        } else {
            // Second time onwards: Must exactly match the saved permanent PIN
            return if (pinManager.verifyPermanentPin(candidatePin)) {
                UnlockStatus.SUCCESS
            } else {
                UnlockStatus.FAILED
            }
        }
    }

    override fun resetLock() {
        pinManager.resetLock()
    }

    override fun getLockConfig(): LockConfig {
        return LockConfig(
            secretDigit = pinManager.secretDigit,
            fakeDate = pinManager.fakeDate,
            isFirstUnlockDone = pinManager.isFirstUnlockDone,
            customWallpaperPath = pinManager.customWallpaperPath
        )
    }

    override fun saveLockConfig(secretDigit: String, fakeDate: String) {
        pinManager.secretDigit = secretDigit
        pinManager.fakeDate = fakeDate
    }

    override fun setCustomWallpaperPath(path: String?) {
        pinManager.customWallpaperPath = path
    }

    override fun getWallpaperFile(): File? {
        val customPath = pinManager.customWallpaperPath
        if (customPath != null) {
            val file = File(customPath)
            if (file.exists()) return file
        }

        val externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val magicDir = File(externalFilesDir, "MagicCalculator")
        if (magicDir.exists() && magicDir.isDirectory) {
            val files = magicDir.listFiles { _, name ->
                name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".png", ignoreCase = true)
            }
            if (!files.isNullOrEmpty()) {
                return files.sortedBy { it.name }.first()
            }
        }
        return null
    }

    override fun isAppLockActive(): Boolean {
        return settingsManager.appMode == 4
    }

    override fun setAppLockActive(active: Boolean) {
        settingsManager.appMode = if (active) 4 else 0
    }
}
