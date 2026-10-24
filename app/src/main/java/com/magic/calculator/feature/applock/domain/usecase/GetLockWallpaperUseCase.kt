package com.magic.calculator.feature.applock.domain.usecase

import com.magic.calculator.feature.applock.domain.repository.AppLockRepository
import java.io.File

class GetLockWallpaperUseCase(
    private val repository: AppLockRepository
) {
    operator fun invoke(): File? {
        return repository.getWallpaperFile()
    }
}
