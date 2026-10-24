package com.magic.calculator.feature.splash.data.repository

import com.magic.calculator.feature.splash.data.datasource.SplashLocalDataSource
import com.magic.calculator.feature.splash.domain.repository.SplashRepository

class SplashRepositoryImpl(
    private val localDataSource: SplashLocalDataSource
) : SplashRepository {
    override fun isUserLoggedIn(): Boolean = localDataSource.isUserLoggedIn()
}
